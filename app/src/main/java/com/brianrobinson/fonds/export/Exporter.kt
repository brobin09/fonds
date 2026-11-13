package com.brianrobinson.fonds.export

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.brianrobinson.fonds.ai.Describer
import com.brianrobinson.fonds.data.CollectionEntity
import com.brianrobinson.fonds.data.Dates
import com.brianrobinson.fonds.data.DocumentEntity
import com.brianrobinson.fonds.data.DocumentRow
import com.brianrobinson.fonds.data.Fields
import com.brianrobinson.fonds.data.FolderRow
import com.brianrobinson.fonds.data.ListField
import com.brianrobinson.fonds.data.PageEntity
import com.brianrobinson.fonds.data.Proposals
import com.brianrobinson.fonds.data.Repository
import com.brianrobinson.fonds.data.SeriesEntity
import com.brianrobinson.fonds.data.Settings
import com.brianrobinson.fonds.data.field
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportOptions(val includeItems: Boolean, val includeDrafts: Boolean)

data class ResolvedItem(
    val doc: DocumentEntity,
    val title: String,
    val date: String,
    val dateSort: String,
    val type: String,
    val creator: String,
    val recipient: String,
    val summary: String,
    val people: List<String>,
    val places: List<String>,
    val organizations: List<String>,
    val subjects: List<String>,
    val usesDrafts: Boolean,
    val pageCount: Int,
)

data class ExportData(
    val collection: CollectionEntity,
    val series: List<SeriesEntity>,
    val folders: List<FolderRow>,
    val docsByFolder: Map<Long, List<DocumentRow>>,
    val pagesByDoc: Map<Long, List<PageEntity>>,
    val options: ExportOptions,
    val processor: String,
) {
    val collectionProposals: Map<String, String> = Proposals.parse(collection.proposals)
}

enum class ExportFormat(val label: String, val mime: String, val ext: String) {
    EAD("EAD 2002 XML", "application/xml", "xml"),
    HTML("Finding aid (HTML)", "text/html", "html"),
    CONTAINER_CSV("Container list (CSV)", "text/csv", "csv"),
    ITEM_CSV("Item list (CSV)", "text/csv", "csv"),
    JSON("Data (JSON)", "application/json", "json"),
    ZIP("Complete package (ZIP)", "application/zip", "zip"),
    IMAGES("Page images only (ZIP)", "application/zip", "zip"),
}

class Exporter(private val repo: Repository, private val settings: Settings) {

    private val dao = repo.dao

    suspend fun load(collectionId: Long, options: ExportOptions, withPages: Boolean): ExportData? = withContext(Dispatchers.IO) {
        val c = dao.getCollection(collectionId) ?: return@withContext null
        val docs = dao.allDocumentRows(collectionId)
        val pages = if (withPages) dao.allPages(collectionId).groupBy { it.documentId } else emptyMap()
        ExportData(
            collection = c,
            series = dao.seriesList(collectionId),
            folders = dao.folderRowsOnce(collectionId),
            docsByFolder = docs.groupBy { it.document.folderId },
            pagesByDoc = pages,
            options = options,
            processor = settings.current.processorName,
        )
    }

    fun suggestedName(c: CollectionEntity, format: ExportFormat): String {
        val slug = c.title.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48).ifBlank { "collection" }
        val suffix = when (format) {
            ExportFormat.EAD -> "ead"
            ExportFormat.HTML -> "finding-aid"
            ExportFormat.CONTAINER_CSV -> "container-list"
            ExportFormat.ITEM_CSV -> "items"
            ExportFormat.JSON -> "data"
            ExportFormat.ZIP -> "package"
            ExportFormat.IMAGES -> "images"
        }
        return "$slug-$suffix.${format.ext}"
    }

    suspend fun write(format: ExportFormat, data: ExportData, out: OutputStream) = withContext(Dispatchers.IO) {
        when (format) {
            ExportFormat.EAD -> out.write(ead(data).toByteArray(Charsets.UTF_8))
            ExportFormat.HTML -> out.write(html(data).toByteArray(Charsets.UTF_8))
            ExportFormat.CONTAINER_CSV -> out.write(containerCsv(data).toByteArray(Charsets.UTF_8))
            ExportFormat.ITEM_CSV -> out.write(itemCsv(data).toByteArray(Charsets.UTF_8))
            ExportFormat.JSON -> out.write(json(data).toByteArray(Charsets.UTF_8))
            ExportFormat.ZIP -> writeZip(data, out)
            ExportFormat.IMAGES -> writeImagesZip(data, out)
        }
        out.flush()
    }

    // ------------------------------------------------------------------ resolution

    fun item(d: ExportData, row: DocumentRow): ResolvedItem {
        val doc = row.document
        val props = if (d.options.includeDrafts) Proposals.parse(doc.proposals) else emptyMap()
        var drafts = false
        fun pick(key: String): String {
            val v = doc.field(key)
            if (v.isNotBlank()) return v
            val p = props[key].orEmpty()
            if (p.isNotBlank()) drafts = true
            return p
        }
        val title = pick(Fields.TITLE)
        val date = pick(Fields.DATE)
        val dateSort = doc.dateSort.ifBlank { props[Fields.DATE_SORT].orEmpty() }
        return ResolvedItem(
            doc = doc,
            title = title.ifBlank { "Item ${doc.sequence}" },
            date = date,
            dateSort = dateSort,
            type = pick(Fields.TYPE),
            creator = pick(Fields.CREATOR),
            recipient = pick(Fields.RECIPIENT),
            summary = pick(Fields.SUMMARY),
            people = ListField.split(pick(Fields.PEOPLE)),
            places = ListField.split(pick(Fields.PLACES)),
            organizations = ListField.split(pick(Fields.ORGS)),
            subjects = ListField.split(pick(Fields.SUBJECTS)),
            usesDrafts = drafts,
            pageCount = row.pageCount,
        )
    }

    fun items(d: ExportData, folderId: Long): List<ResolvedItem> = d.docsByFolder[folderId].orEmpty().map { item(d, it) }

    fun folderTitle(d: ExportData, row: FolderRow): String {
        val f = row.folder
        val draft = if (d.options.includeDrafts) Proposals.parse(f.proposals)[Fields.TITLE].orEmpty() else ""
        return f.title.ifBlank { f.label }.ifBlank { draft }.ifBlank { "Folder ${f.number}" }
    }

    fun folderScope(d: ExportData, row: FolderRow): String {
        val f = row.folder
        val draft = if (d.options.includeDrafts) Proposals.parse(f.proposals)[Fields.SCOPE].orEmpty() else ""
        return f.scopeNote.ifBlank { draft }
    }

    fun folderDates(d: ExportData, row: FolderRow): String {
        if (row.folder.dateRange.isNotBlank()) return row.folder.dateRange
        return Dates.range(items(d, row.folder.id).map { it.dateSort })
    }

    private fun collectionField(d: ExportData, approved: String, key: String): String =
        approved.ifBlank { if (d.options.includeDrafts) d.collectionProposals[key].orEmpty() else "" }

    fun collectionDates(d: ExportData): String =
        d.collection.dateRange.ifBlank {
            Dates.range(d.docsByFolder.values.flatten().map { item(d, it).dateSort })
        }

    fun extent(d: ExportData): String {
        if (d.collection.extent.isNotBlank()) return d.collection.extent
        val boxes = d.folders.map { it.boxNumber }.distinct().size
        val folders = d.folders.size
        val items = d.docsByFolder.values.sumOf { it.size }
        val pages = d.docsByFolder.values.flatten().sumOf { it.pageCount }
        if (boxes == 0) return ""
        return "${if (boxes == 1) "1 box" else "$boxes boxes"} ($folders folder${if (folders == 1) "" else "s"}, " +
            "$items item${if (items == 1) "" else "s"}, $pages page${if (pages == 1) "" else "s"})"
    }

    fun abstractText(d: ExportData) = collectionField(d, d.collection.abstractText, Fields.ABSTRACT)
    fun scopeContent(d: ExportData) = collectionField(d, d.collection.scopeContent, Fields.SCOPE_CONTENT)
    fun arrangement(d: ExportData): String = collectionField(d, d.collection.arrangement, Fields.ARRANGEMENT).ifBlank {
        if (d.series.isNotEmpty()) {
            "The collection is arranged in ${d.series.size} series: " +
                d.series.joinToString("; ") { "Series ${Describer.roman(it.number)}. ${it.title}" } + "."
        } else {
            ""
        }
    }

    fun preferredCitation(d: ExportData): String = d.collection.preferredCitation.ifBlank {
        Citations.preferred(d.collection.title, d.collection.repository)
    }

    fun processingInfo(d: ExportData): String = d.collection.processingInfo

    /** Names and subjects for <controlaccess>, most frequent first. */
    fun accessTerms(d: ExportData): Map<String, List<String>> {
        val all = d.docsByFolder.values.flatten().map { item(d, it) }
        fun top(lists: List<List<String>>): List<String> {
            val counts = lists.flatten().groupingBy { it.trim() }.eachCount().filterKeys { it.isNotBlank() }
            val min = if (all.size > 30) 2 else 1
            return counts.filterValues { it >= min }.entries.sortedByDescending { it.value }.take(25).map { it.key }
        }
        return mapOf(
            "persname" to top(all.map { it.people } + listOf(all.map { it.creator }.filter { it.isNotBlank() && !Citations.looksCorporate(it) })),
            "corpname" to top(all.map { it.organizations }),
            "geogname" to top(all.map { it.places }),
            "subject" to top(all.map { it.subjects }),
        )
    }

    /** Folders grouped for the container list: by series when there are series, else one group. */
    fun groups(d: ExportData): List<Pair<SeriesEntity?, List<FolderRow>>> {
        if (d.series.isEmpty()) return listOf(null to d.folders)
        val out = mutableListOf<Pair<SeriesEntity?, List<FolderRow>>>()
        d.series.forEach { s -> out += (s to d.folders.filter { it.folder.seriesId == s.id }) }
        val loose = d.folders.filter { f -> d.series.none { it.id == f.folder.seriesId } }
        if (loose.isNotEmpty()) out += (null to loose)
        return out
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // ------------------------------------------------------------------ EAD 2002

    fun ead(d: ExportData): String {
        val c = d.collection
        val x = Xml()
        x.raw("""<?xml version="1.0" encoding="UTF-8"?>""")
        x.open(
            "ead",
            "xmlns" to "urn:isbn:1-931666-22-9",
            "xmlns:xlink" to "http://www.w3.org/1999/xlink",
            "xmlns:xsi" to "http://www.w3.org/2001/XMLSchema-instance",
            "xsi:schemaLocation" to "urn:isbn:1-931666-22-9 http://www.loc.gov/ead/ead.xsd",
        )
        x.open("eadheader", "langencoding" to "iso639-2b", "dateencoding" to "iso8601", "countryencoding" to "iso3166-1", "scriptencoding" to "iso15924", "repositoryencoding" to "iso15511")
        x.leaf("eadid", c.identifier.ifBlank { suggestedName(c, ExportFormat.EAD).removeSuffix(".xml") })
        x.open("filedesc")
        x.open("titlestmt")
        x.leaf("titleproper", "Guide to the ${c.title}")
        if (d.processor.isNotBlank()) x.leaf("author", "Processed by ${d.processor}")
        x.close("titlestmt")
        x.open("publicationstmt")
        if (c.repository.isNotBlank()) x.leaf("publisher", c.repository)
        x.leaf("date", today().take(4))
        x.close("publicationstmt")
        x.close("filedesc")
        x.open("profiledesc")
        x.raw("<creation>Finding aid created with Fonds on <date normal=\"${today()}\">${today()}</date>.</creation>")
        x.raw("<langusage>Description is in <language langcode=\"eng\">English</language>.</langusage>")
        x.close("profiledesc")
        x.close("eadheader")

        x.open("archdesc", "level" to "collection")
        x.open("did")
        if (c.repository.isNotBlank()) {
            x.open("repository"); x.leaf("corpname", c.repository); x.close("repository")
        }
        if (c.creator.isNotBlank()) {
            x.open("origination", "label" to "Creator")
            x.leaf(if (Citations.looksCorporate(c.creator)) "corpname" else "persname", c.creator)
            x.close("origination")
        }
        x.leaf("unittitle", c.title)
        val dates = collectionDates(d)
        if (dates.isNotBlank()) x.leaf("unitdate", dates, "type" to "inclusive", "normal" to normalRange(dates))
        if (c.identifier.isNotBlank()) x.leaf("unitid", c.identifier)
        extent(d).takeIf { it.isNotBlank() }?.let { e -> x.open("physdesc"); x.leaf("extent", e); x.close("physdesc") }
        abstractText(d).takeIf { it.isNotBlank() }?.let { x.leaf("abstract", it) }
        x.close("did")

        fun note(tag: String, head: String, text: String) {
            if (text.isBlank()) return
            x.open(tag); x.leaf("head", head); paragraphs(text).forEach { x.leaf("p", it) }; x.close(tag)
        }
        note("bioghist", "Biographical / Historical", c.bioHist)
        note("scopecontent", "Scope and Contents", scopeContent(d))
        note("arrangement", "Arrangement", arrangement(d))
        note("accessrestrict", "Conditions Governing Access", c.accessRestrict)
        note("userestrict", "Conditions Governing Use", c.useRestrict)
        note("prefercite", "Preferred Citation", preferredCitation(d))
        note("processinfo", "Processing Information", processingInfo(d))

        val terms = accessTerms(d)
        if (terms.values.any { it.isNotEmpty() }) {
            x.open("controlaccess")
            terms.forEach { (tag, list) -> list.forEach { x.leaf(tag, it) } }
            x.close("controlaccess")
        }

        x.open("dsc", "type" to "combined")
        groups(d).forEach { (series, rows) ->
            if (series != null) {
                x.open("c", "level" to "series", "id" to "ser${series.number}")
                x.open("did")
                x.leaf("unitid", "Series ${Describer.roman(series.number)}")
                x.leaf("unittitle", series.title)
                val sd = series.dateRange.ifBlank { Dates.range(rows.map { folderDates(d, it) }.flatMap { it.split("-") }) }
                if (sd.isNotBlank()) x.leaf("unitdate", sd, "normal" to normalRange(sd))
                x.close("did")
                if (series.scopeNote.isNotBlank()) { x.open("scopecontent"); paragraphs(series.scopeNote).forEach { x.leaf("p", it) }; x.close("scopecontent") }
            }
            rows.forEach { row -> eadFolder(x, d, row) }
            if (series != null) x.close("c")
        }
        x.close("dsc")
        x.close("archdesc")
        x.close("ead")
        return x.toString()
    }

    private fun eadFolder(x: Xml, d: ExportData, row: FolderRow) {
        val id = "f${row.folder.id}"
        x.open("c", "level" to "file", "id" to id)
        x.open("did")
        x.leaf("container", "${row.boxNumber}", "id" to "${id}-box", "type" to "Box")
        x.leaf("container", "${row.folder.number}", "parent" to "${id}-box", "type" to "Folder")
        x.leaf("unittitle", folderTitle(d, row))
        val dates = folderDates(d, row)
        if (dates.isNotBlank()) x.leaf("unitdate", dates, "normal" to normalRange(dates))
        x.close("did")
        folderScope(d, row).takeIf { it.isNotBlank() }?.let { s -> x.open("scopecontent"); paragraphs(s).forEach { x.leaf("p", it) }; x.close("scopecontent") }
        if (d.options.includeItems) {
            items(d, row.folder.id).forEach { item ->
                x.open("c", "level" to "item")
                x.open("did")
                x.leaf("unittitle", item.title)
                if (item.date.isNotBlank()) {
                    if (item.dateSort.isNotBlank()) x.leaf("unitdate", item.date, "normal" to item.dateSort) else x.leaf("unitdate", item.date)
                }
                if (item.pageCount > 0) { x.open("physdesc"); x.leaf("extent", "${item.pageCount} page${if (item.pageCount == 1) "" else "s"}"); x.close("physdesc") }
                x.close("did")
                if (item.summary.isNotBlank()) { x.open("scopecontent"); x.leaf("p", item.summary); x.close("scopecontent") }
                val names = item.people.map { "persname" to it } + item.organizations.map { "corpname" to it } +
                    item.places.map { "geogname" to it } + item.subjects.map { "subject" to it }
                if (names.isNotEmpty()) {
                    x.open("controlaccess"); names.forEach { (tag, v) -> x.leaf(tag, v) }; x.close("controlaccess")
                }
                x.close("c")
            }
        }
        x.close("c")
    }

    private fun normalRange(text: String): String {
        val years = Regex("""\b(\d{4})\b""").findAll(text).map { it.value }.toList()
        return when {
            years.isEmpty() -> ""
            years.size == 1 -> years[0]
            else -> "${years.min()}/${years.max()}"
        }
    }

    private fun paragraphs(text: String) = text.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotEmpty() }

    // ------------------------------------------------------------------ HTML

    fun html(d: ExportData): String {
        val c = d.collection
        val e: (String) -> String = { esc(it) }
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        sb.append("<title>").append(e("Guide to the ${c.title}")).append("</title><style>").append(CSS).append("</style></head><body><main>")
        sb.append("<p class=\"kicker\">").append(e(c.repository.ifBlank { "Finding aid" })).append("</p>")
        sb.append("<h1>").append(e("Guide to the ${c.title}")).append("</h1>")
        collectionDates(d).takeIf { it.isNotBlank() }?.let { sb.append("<p class=\"dates\">").append(e(it)).append("</p>") }

        sb.append("<section><h2>Collection overview</h2><table class=\"overview\">")
        fun row(k: String, v: String) { if (v.isNotBlank()) sb.append("<tr><th>").append(e(k)).append("</th><td>").append(e(v)).append("</td></tr>") }
        row("Title", c.title); row("Creator", c.creator); row("Dates", collectionDates(d)); row("Collection number", c.identifier)
        row("Extent", extent(d)); row("Repository", c.repository); row("Abstract", abstractText(d))
        sb.append("</table></section>")

        fun section(title: String, text: String) {
            if (text.isBlank()) return
            sb.append("<section><h2>").append(e(title)).append("</h2>")
            paragraphs(text).forEach { sb.append("<p>").append(e(it)).append("</p>") }
            sb.append("</section>")
        }
        section("Biographical / historical note", c.bioHist)
        section("Scope and content", scopeContent(d))
        section("Arrangement", arrangement(d))
        section("Conditions governing access", c.accessRestrict)
        section("Conditions governing use", c.useRestrict)
        section("Preferred citation", preferredCitation(d))
        section("Processing information", processingInfo(d))

        val terms = accessTerms(d)
        if (terms.values.any { it.isNotEmpty() }) {
            sb.append("<section><h2>Subjects and names</h2>")
            val labels = mapOf("persname" to "People", "corpname" to "Organizations", "geogname" to "Places", "subject" to "Topics")
            terms.forEach { (k, list) ->
                if (list.isNotEmpty()) sb.append("<p><strong>").append(labels[k]).append(":</strong> ").append(e(list.joinToString("; "))).append("</p>")
            }
            sb.append("</section>")
        }

        sb.append("<section><h2>Container list</h2>")
        groups(d).forEach { (series, rows) ->
            if (series != null) {
                sb.append("<h3>Series ").append(Describer.roman(series.number)).append(". ").append(e(series.title)).append("</h3>")
                if (series.scopeNote.isNotBlank()) sb.append("<p class=\"scope\">").append(e(series.scopeNote)).append("</p>")
            } else if (d.series.isNotEmpty()) {
                sb.append("<h3>Other folders</h3>")
            }
            sb.append("<table class=\"containers\"><thead><tr><th>Box</th><th>Folder</th><th>Title</th><th>Dates</th></tr></thead><tbody>")
            rows.forEach { r ->
                sb.append("<tr class=\"folder\"><td>").append(r.boxNumber).append("</td><td>").append(r.folder.number).append("</td><td>")
                    .append(e(folderTitle(d, r)))
                folderScope(d, r).takeIf { it.isNotBlank() }?.let { sb.append("<div class=\"scope\">").append(e(it)).append("</div>") }
                sb.append("</td><td class=\"d\">").append(e(folderDates(d, r))).append("</td></tr>")
                if (d.options.includeItems) {
                    items(d, r.folder.id).forEach { item ->
                        sb.append("<tr class=\"item").append(if (item.usesDrafts) " draft" else "").append("\"><td></td><td class=\"n\">").append(item.doc.sequence)
                            .append("</td><td>").append(e(item.title))
                        if (item.summary.isNotBlank()) sb.append("<div class=\"sum\">").append(e(item.summary)).append("</div>")
                        sb.append("</td><td class=\"d\">").append(e(item.date)).append("</td></tr>")
                    }
                }
            }
            sb.append("</tbody></table>")
        }
        sb.append("</section>")
        if (d.options.includeDrafts) {
            sb.append("<p class=\"note\">Rows marked with a dotted underline include machine-drafted description that has not yet been reviewed.</p>")
        }
        sb.append("<footer>Created with Fonds · ").append(today()).append("</footer></main></body></html>")
        return sb.toString()
    }

    // ------------------------------------------------------------------ CSV

    fun containerCsv(d: ExportData): String {
        val rows = mutableListOf(listOf("Series", "Box", "Folder", "Folder title", "Dates", "Scope note", "Items", "Pages", "Folder tab label"))
        d.folders.forEach { r ->
            val s = d.series.firstOrNull { it.id == r.folder.seriesId }
            rows += listOf(
                s?.let { "Series ${Describer.roman(it.number)}. ${it.title}" }.orEmpty(),
                "${r.boxNumber}", "${r.folder.number}",
                folderTitle(d, r), folderDates(d, r), folderScope(d, r),
                "${r.docCount}", "${r.pageCount}", r.folder.label,
            )
        }
        return csv(rows)
    }

    fun itemCsv(d: ExportData): String {
        val header = listOf(
            "Box", "Folder", "Item", "Title", "Date", "Sortable date", "Type", "Creator", "Recipient", "Summary",
            "People", "Places", "Organizations", "Subjects", "Pages", "Handwritten", "Reviewed", "Drafted by",
            "Processor note", "Source repository", "Source collection", "Source location", "Citation", "Text",
        )
        val rows = mutableListOf(header)
        d.folders.forEach { r ->
            d.docsByFolder[r.folder.id].orEmpty().forEach { dr ->
                val ri = item(d, dr)
                val text = d.pagesByDoc[dr.document.id].orEmpty().joinToString("\n\n") { p -> p.text }
                rows += listOf(
                    "${r.boxNumber}", "${r.folder.number}", "${dr.document.sequence}", ri.title, ri.date, ri.dateSort, ri.type,
                    ri.creator, ri.recipient, ri.summary, ri.people.joinToString("; "), ri.places.joinToString("; "),
                    ri.organizations.joinToString("; "), ri.subjects.joinToString("; "), "${ri.pageCount}",
                    if (dr.document.handwritten) "yes" else "", if (dr.document.reviewed) "yes" else "no", dr.document.engine,
                    dr.document.note, dr.document.sourceRepository, dr.document.sourceCollection, dr.document.sourceLocation,
                    if (d.collection.kind == com.brianrobinson.fonds.data.CollectionKind.INBOX) {
                        Citations.singleNote(ri.creator, ri.recipient, ri.title, ri.date, dr.document.sourceLocation, dr.document.sourceCollection, dr.document.sourceRepository)
                    } else {
                        Citations.note(ri.creator, ri.recipient, ri.title, ri.date, r.boxNumber, r.folder.number, d.collection.title, d.collection.repository)
                    },
                    text,
                )
            }
        }
        return csv(rows)
    }

    private fun csv(rows: List<List<String>>): String = buildString {
        append('﻿') // so Excel opens UTF-8 correctly
        rows.forEach { r ->
            append(r.joinToString(",") { v ->
                if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
            })
            append("\r\n")
        }
    }

    // ------------------------------------------------------------------ JSON

    fun json(d: ExportData, imageName: ((PageEntity, FolderRow, DocumentEntity) -> String)? = null): String {
        val c = d.collection
        val root = JSONObject()
        root.put("format", "fonds-1")
        root.put("exported", today())
        root.put("collection", JSONObject().apply {
            put("title", c.title); put("creator", c.creator); put("repository", c.repository); put("identifier", c.identifier)
            put("dates", collectionDates(d)); put("extent", extent(d)); put("abstract", c.abstractText)
            put("scopeContent", c.scopeContent); put("bioHist", c.bioHist); put("arrangement", c.arrangement)
            put("accessRestrict", c.accessRestrict); put("useRestrict", c.useRestrict)
            put("preferredCitation", c.preferredCitation); put("processingInfo", c.processingInfo)
            put("pendingDrafts", JSONObject(d.collectionProposals))
        })
        root.put("series", JSONArray().apply {
            d.series.forEach { s ->
                put(JSONObject().put("number", s.number).put("title", s.title).put("scopeNote", s.scopeNote).put("id", s.id))
            }
        })
        root.put("folders", JSONArray().apply {
            d.folders.forEach { r ->
                val f = r.folder
                put(JSONObject().apply {
                    put("box", r.boxNumber); put("folder", f.number); put("label", f.label); put("title", f.title)
                    put("dates", f.dateRange); put("scopeNote", f.scopeNote); put("seriesId", f.seriesId ?: JSONObject.NULL)
                    put("note", f.note); put("pendingDrafts", JSONObject(Proposals.parse(f.proposals)))
                    put("items", JSONArray().apply {
                        d.docsByFolder[f.id].orEmpty().forEach { dr ->
                            val doc = dr.document
                            put(JSONObject().apply {
                                put("sequence", doc.sequence); put("title", doc.title); put("date", doc.date); put("dateSort", doc.dateSort)
                                put("type", doc.docType); put("creator", doc.creator); put("recipient", doc.recipient); put("summary", doc.summary)
                                put("people", JSONArray(ListField.split(doc.people))); put("places", JSONArray(ListField.split(doc.places)))
                                put("organizations", JSONArray(ListField.split(doc.organizations))); put("subjects", JSONArray(ListField.split(doc.subjects)))
                                put("handwritten", doc.handwritten); put("reviewed", doc.reviewed); put("draftedBy", doc.engine)
                                put("note", doc.note); put("pendingDrafts", JSONObject(Proposals.parse(doc.proposals)))
                                put("source", JSONObject().put("repository", doc.sourceRepository).put("collection", doc.sourceCollection).put("location", doc.sourceLocation))
                                put("pages", JSONArray().apply {
                                    d.pagesByDoc[doc.id].orEmpty().forEach { p ->
                                        put(JSONObject().apply {
                                            put("sequence", p.sequence); put("text", p.text); put("textSource", p.textSource)
                                            if (imageName != null) put("image", imageName(p, r, doc))
                                        })
                                    }
                                })
                            })
                        }
                    })
                })
            }
        })
        return root.toString(2)
    }

    // ------------------------------------------------------------------ ZIP

    /** Just the page images, filed by box/folder, for bulk download. */
    private fun writeImagesZip(d: ExportData, out: OutputStream) {
        fun pad(n: Int, w: Int) = n.toString().padStart(w, '0')
        val zip = ZipOutputStream(out)
        d.folders.forEach { r ->
            d.docsByFolder[r.folder.id].orEmpty().forEach { dr ->
                d.pagesByDoc[dr.document.id].orEmpty().forEach { p ->
                    val f = File(p.imagePath)
                    if (f.exists()) {
                        zip.putNextEntry(ZipEntry("box-${pad(r.boxNumber, 2)}/folder-${pad(r.folder.number, 3)}/item-${pad(dr.document.sequence, 3)}-p${pad(p.sequence, 2)}.jpg"))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
        zip.finish()
    }

    private fun writeZip(d: ExportData, out: OutputStream) {
        fun pad(n: Int, w: Int) = n.toString().padStart(w, '0')
        val naming: (PageEntity, FolderRow, DocumentEntity) -> String = { p, r, doc ->
            "images/box-${pad(r.boxNumber, 2)}/folder-${pad(r.folder.number, 3)}/item-${pad(doc.sequence, 3)}-p${pad(p.sequence, 2)}.jpg"
        }
        val zip = ZipOutputStream(out)
        fun text(name: String, body: String) {
            zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray(Charsets.UTF_8)); zip.closeEntry()
        }
        text("finding-aid.xml", ead(d))
        text("finding-aid.html", html(d))
        text("container-list.csv", containerCsv(d))
        text("items.csv", itemCsv(d))
        text("data.json", json(d, naming))
        text(
            "README.txt",
            "Exported from Fonds on ${today()}.\n\n" +
                "finding-aid.xml   EAD 2002, importable into ArchivesSpace and most catalogs\n" +
                "finding-aid.html  printable finding aid\n" +
                "container-list.csv / items.csv  spreadsheets\n" +
                "data.json         everything, including transcriptions and pending drafts\n" +
                "images/           every page, named by box, folder, item and page\n" +
                "text/             page transcriptions, one file per item\n",
        )
        d.folders.forEach { r ->
            d.docsByFolder[r.folder.id].orEmpty().forEach { dr ->
                val pages = d.pagesByDoc[dr.document.id].orEmpty()
                val transcript = pages.joinToString("\n\n") { p -> "[page ${p.sequence}]\n${p.text}" }
                if (transcript.isNotBlank()) {
                    text("text/box-${pad(r.boxNumber, 2)}/folder-${pad(r.folder.number, 3)}/item-${pad(dr.document.sequence, 3)}.txt", transcript)
                }
                pages.forEach { p ->
                    val f = File(p.imagePath)
                    if (f.exists()) {
                        zip.putNextEntry(ZipEntry(naming(p, r, dr.document)))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
        zip.finish()
    }

    companion object {
        private val CSS = """
            body{margin:0;background:#f5f1e8;color:#1f1c17;font:16px/1.55 Georgia,'Times New Roman',serif}
            main{max-width:860px;margin:0 auto;padding:40px 28px 64px;background:#fffdf8}
            .kicker{font:600 12px/1.2 -apple-system,Roboto,sans-serif;letter-spacing:.14em;text-transform:uppercase;color:#7d6128;margin:0}
            h1{font-size:30px;line-height:1.2;margin:.3em 0 .1em}
            .dates{color:#4c473d;margin:0 0 1.5em}
            h2{font-size:19px;border-bottom:1px solid #d3cbbb;padding-bottom:4px;margin:2em 0 .6em}
            h3{font-size:16px;margin:1.6em 0 .3em}
            table{border-collapse:collapse;width:100%}
            .overview th{text-align:left;vertical-align:top;width:11em;padding:4px 12px 4px 0;font:600 13px -apple-system,Roboto,sans-serif;color:#4c473d}
            .overview td{padding:4px 0}
            .containers th{font:600 12px -apple-system,Roboto,sans-serif;text-transform:uppercase;letter-spacing:.08em;text-align:left;border-bottom:2px solid #2e4a5a;padding:6px 8px}
            .containers td{padding:6px 8px;vertical-align:top;border-bottom:1px solid #ece6da}
            .containers td:first-child,.containers td:nth-child(2){font-family:ui-monospace,Menlo,monospace;font-size:14px;width:3.5em}
            .containers .d{white-space:nowrap;color:#4c473d;width:9em}
            tr.folder td{font-weight:600}
            tr.item td{font-size:14px;font-weight:normal;color:#34302a}
            tr.item .n{color:#7d766a}
            tr.draft td:nth-child(3){text-decoration:underline dotted #b08a3a}
            .scope,.sum{font-weight:normal;font-size:14px;color:#4c473d;margin-top:2px}
            .note{font-size:13px;color:#7d6128}
            footer{margin-top:48px;font:12px -apple-system,Roboto,sans-serif;color:#7d766a}
            @media print{body{background:#fff}main{padding:0;max-width:none}tr{page-break-inside:avoid}}
        """.trimIndent()

        fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        // Held so the WebView isn't garbage-collected before printing starts.
        private var printView: WebView? = null

        /** Opens Android's print dialog, where "Save as PDF" gives a PDF finding aid. */
        fun print(context: Context, html: String, jobName: String) {
            val view = WebView(context)
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView, url: String?) {
                    val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    pm.print(jobName, v.createPrintDocumentAdapter(jobName), PrintAttributes.Builder().build())
                }
            }
            view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            printView = view
        }
    }
}

/** Tiny indenting XML writer. */
private class Xml {
    private val sb = StringBuilder()
    private var depth = 0

    private fun indent() = "  ".repeat(depth)
    private fun attrs(a: Array<out Pair<String, String>>) =
        a.filter { it.second.isNotBlank() }.joinToString("") { " ${it.first}=\"${Exporter.esc(it.second)}\"" }

    fun raw(s: String) { sb.append(indent()).append(s).append('\n') }
    fun open(tag: String, vararg a: Pair<String, String>) { sb.append(indent()).append('<').append(tag).append(attrs(a)).append(">\n"); depth++ }
    fun close(tag: String) { depth--; sb.append(indent()).append("</").append(tag).append(">\n") }
    fun leaf(tag: String, text: String, vararg a: Pair<String, String>) {
        sb.append(indent()).append('<').append(tag).append(attrs(a)).append('>').append(Exporter.esc(text)).append("</").append(tag).append(">\n")
    }
    override fun toString() = sb.toString()
}

object Citations {
    private val CORPORATE = Regex(
        """(?i)\b(university|college|school|association|company|co\.|inc\.?|corporation|church|society|committee|board|department|office|club|league|foundation|council|bureau|commission|institute|union|fraternity|sorority|bank|hospital|press|agency)\b""",
    )

    fun looksCorporate(name: String) = CORPORATE.containsMatchIn(name)

    /** "1952 January 5" → "January 5, 1952" for Chicago-style notes. */
    fun naturalDate(dacs: String): String {
        val m = Regex("""^(circa\s+)?(\d{4})(?:\s+([A-Za-z]+))?(?:\s+(\d{1,2}))?$""", RegexOption.IGNORE_CASE).find(dacs.trim())
            ?: return dacs.trim()
        val circa = if (m.groupValues[1].isNotBlank()) "ca. " else ""
        val y = m.groupValues[2]
        val mo = m.groupValues[3]
        val d = m.groupValues[4]
        return circa + when {
            mo.isNotBlank() && d.isNotBlank() -> "$mo $d, $y"
            mo.isNotBlank() -> "$mo $y"
            else -> y
        }
    }

    fun note(creator: String, recipient: String, title: String, date: String, box: Int, folder: Int, collection: String, repository: String): String {
        val head = when {
            creator.isNotBlank() && recipient.isNotBlank() -> "$creator to $recipient"
            title.isNotBlank() -> title
            else -> "Untitled item"
        }
        val when_ = if (date.isBlank() || date.equals("undated", true)) "n.d." else naturalDate(date)
        return listOf(head, when_, "box $box, folder $folder", collection, repository)
            .filter { it.isNotBlank() }.joinToString(", ") + "."
    }

    /** For a single item: cite it by where it actually lives. */
    fun singleNote(creator: String, recipient: String, title: String, date: String, location: String, collection: String, repository: String): String {
        val head = when {
            creator.isNotBlank() && recipient.isNotBlank() -> "$creator to $recipient"
            title.isNotBlank() -> title
            else -> "Untitled item"
        }
        val when_ = if (date.isBlank() || date.equals("undated", true)) "n.d." else naturalDate(date)
        return listOf(head, when_, location, collection, repository).filter { it.isNotBlank() }.joinToString(", ") + "."
    }

    fun shortNote(creator: String, recipient: String, title: String, date: String): String {
        val head = when {
            creator.isNotBlank() && recipient.isNotBlank() -> "${creator.substringAfterLast(' ')} to ${recipient.substringAfterLast(' ')}"
            else -> title.split(" ").take(4).joinToString(" ")
        }
        val when_ = if (date.isBlank() || date.equals("undated", true)) "n.d." else naturalDate(date)
        return "$head, $when_."
    }

    fun bibliography(collection: String, repository: String): String =
        listOf(collection, repository).filter { it.isNotBlank() }.joinToString(". ") + "."

    fun preferred(collection: String, repository: String): String =
        "[Item title and date], box [#], folder [#], " + listOf(collection, repository).filter { it.isNotBlank() }.joinToString(", ") + "."
}

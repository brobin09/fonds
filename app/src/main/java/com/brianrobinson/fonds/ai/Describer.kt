package com.brianrobinson.fonds.ai

import com.brianrobinson.fonds.data.Dates
import com.brianrobinson.fonds.data.DocumentRow
import com.brianrobinson.fonds.data.Engine
import com.brianrobinson.fonds.data.Fields
import com.brianrobinson.fonds.data.FolderRow
import com.brianrobinson.fonds.data.ListField
import com.brianrobinson.fonds.data.Proposals
import com.brianrobinson.fonds.data.Repository
import com.brianrobinson.fonds.data.Settings
import com.brianrobinson.fonds.data.displayDate
import com.brianrobinson.fonds.data.displayTitle
import com.brianrobinson.fonds.data.displayType
import kotlinx.coroutines.flow.first

data class SeriesSuggestion(val title: String, val scopeNote: String, val folderIds: List<Long>)

data class DraftOutcome(val engine: String, val note: String = "")

/**
 * On-demand drafts above the item level: folder titles and scope notes, a proposed
 * series arrangement, and collection-level abstract / scope and content.
 * Everything comes back as proposals for you to accept or dismiss.
 */
class Describer(
    private val repo: Repository,
    private val settings: Settings,
    private val nano: NanoEngine,
    private val claude: ClaudeEngine,
) {
    private val dao = repo.dao

    private fun pick(): String {
        val s = settings.current
        return when {
            s.engine == Engine.OFF -> Pipeline.PATTERN
            s.engine == Engine.CLAUDE && s.hasClaude -> Engine.CLAUDE
            s.engine != Engine.CLAUDE && nano.isAvailable -> Engine.NANO
            s.hasClaude && s.engine != Engine.NANO -> Engine.CLAUDE
            else -> Pipeline.PATTERN
        }
    }

    private suspend fun ask(engine: String, prompt: String): String {
        val s = settings.current
        return when (engine) {
            Engine.CLAUDE -> claude.generate(s.claudeKey, s.claudeModel, prompt, maxTokens = 2000)
            Engine.NANO -> nano.generate(prompt.take(NanoEngine.MAX_TEXT_CHARS + 1500))
            else -> ""
        }
    }

    private fun engineName(engine: String) = when (engine) {
        Engine.CLAUDE -> "Claude"
        Engine.NANO -> "Gemini Nano (on device)"
        else -> "Pattern match (no AI)"
    }

    private fun itemLine(d: DocumentRow): String {
        val doc = d.document
        val summary = doc.summary.ifBlank { Proposals.parse(doc.proposals)[Fields.SUMMARY].orEmpty() }
        val creator = doc.creator.ifBlank { Proposals.parse(doc.proposals)[Fields.CREATOR].orEmpty() }
        return buildString {
            append("- ").append(doc.displayTitle())
            doc.displayDate().takeIf { it.isNotBlank() }?.let { append(" (").append(it).append(")") }
            doc.displayType().takeIf { it.isNotBlank() }?.let { append(" [").append(it).append("]") }
            if (creator.isNotBlank()) append(" by ").append(creator)
            if (summary.isNotBlank()) append(": ").append(summary)
        }
    }

    private fun sortOf(d: DocumentRow): String =
        d.document.dateSort.ifBlank { Proposals.parse(d.document.proposals)[Fields.DATE_SORT].orEmpty() }

    // ------------------------------------------------------------------ folder

    suspend fun draftFolder(folderId: Long): DraftOutcome {
        val row = dao.folderRowOnce(folderId) ?: return DraftOutcome("none", "Folder not found")
        val ctx = dao.getCollection(row.collectionId)
        val docs = dao.documentRows(folderId).first()
        if (docs.isEmpty()) return DraftOutcome("none", "Scan something into this folder first.")

        val drafts = mutableMapOf<String, String>()
        Dates.range(docs.map { sortOf(it) }).takeIf { it.isNotBlank() }?.let { drafts[Fields.DATE_RANGE] = it }

        val engine = pick()
        if (engine == Pipeline.PATTERN) {
            drafts += patternFolder(docs)
        } else {
            val lines = docs.joinToString("\n") { itemLine(it) }
            val prompt = Prompts.folder(ctx?.title.orEmpty(), row.boxNumber, row.folder.number, row.folder.label, lines.take(7000))
            val answer = DraftParser.parseKeyed(ask(engine, prompt), setOf("TITLE", "SCOPE"))
            answer["TITLE"]?.takeIf { it.isNotBlank() }?.let { drafts[Fields.TITLE] = it }
            answer["SCOPE"]?.takeIf { it.isNotBlank() }?.let { drafts[Fields.SCOPE] = it }
            if (answer.values.all { it.isBlank() }) drafts += patternFolder(docs)
        }
        val f = row.folder
        val changed = drafts.filter { (k, v) ->
            when (k) {
                Fields.TITLE -> v != f.title
                Fields.SCOPE -> v != f.scopeNote
                Fields.DATE_RANGE -> v != f.dateRange
                else -> true
            }
        }
        repo.mergeFolderProposals(folderId, changed)
        return DraftOutcome(engineName(engine), if (changed.isEmpty()) "Nothing new to suggest." else "")
    }

    private fun patternFolder(docs: List<DocumentRow>): Map<String, String> {
        val types = docs.map { it.document.displayType() }.filter { it.isNotBlank() }
        val dominant = types.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val title = when {
            dominant == null -> ""
            types.count { it == dominant } * 2 < types.size -> "Mixed materials"
            else -> plural(dominant)
        }
        val creators = docs.map { d ->
            d.document.creator.ifBlank { Proposals.parse(d.document.proposals)[Fields.CREATOR].orEmpty() }
        }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key }
        val scope = buildString {
            append("${docs.size} item${if (docs.size == 1) "" else "s"}")
            if (dominant != null) append(", mostly ${plural(dominant).lowercase()}")
            if (creators.isNotEmpty()) append(", including material from ${joinNames(creators)}")
            append(".")
        }
        return mapOf(Fields.TITLE to title, Fields.SCOPE to scope).filterValues { it.isNotBlank() }
    }

    // ------------------------------------------------------------------ series

    suspend fun suggestSeries(collectionId: Long): Pair<List<SeriesSuggestion>, DraftOutcome> {
        val c = dao.getCollection(collectionId) ?: return emptyList<SeriesSuggestion>() to DraftOutcome("none")
        val folders = dao.folderRowsOnce(collectionId)
        if (folders.isEmpty()) return emptyList<SeriesSuggestion>() to DraftOutcome("none", "No folders yet.")
        val docs = dao.allDocumentRows(collectionId).groupBy { it.document.folderId }
        val engine = pick()
        if (engine != Pipeline.PATTERN) {
            // Keep each folder line short: Nano reads ~4,000 tokens and answers in 256.
            val lines = folders.joinToString("\n") { f ->
                val title = f.folder.displayTitle().ifBlank { patternFolder(docs[f.folder.id].orEmpty())[Fields.TITLE].orEmpty() }
                "[${f.folder.id}] Box ${f.boxNumber}/Folder ${f.folder.number}: ${title.ifBlank { "untitled" }.take(80)}"
            }
            val raw = ask(engine, Prompts.series(c.title, lines.take(7000), withScope = engine == Engine.CLAUDE))
            val valid = folders.map { it.folder.id }.toSet()
            val used = mutableSetOf<Long>()
            val out = raw.replace("```", "").lines().mapNotNull { line ->
                val parts = line.split("|").map { it.trim() }
                if (parts.size < 2) return@mapNotNull null
                val title = parts[0].trim('-', '*', '•', ' ').replace(Regex("""^(series\s+[ivxlcdm\d]+[.:]\s*)""", RegexOption.IGNORE_CASE), "").trim()
                val ids = Regex("""\d+""").findAll(parts[1]).mapNotNull { it.value.toLongOrNull() }
                    .filter { it in valid && used.add(it) }.toList()
                if (title.isBlank() || ids.isEmpty()) null else SeriesSuggestion(title, parts.getOrNull(2).orEmpty(), ids)
            }
            if (out.isNotEmpty()) {
                // Anything the model didn't place (e.g. a long list cut short) is grouped by document type.
                val leftover = folders.filter { it.folder.id !in used }
                val extra = if (leftover.isEmpty()) emptyList() else patternSeries(leftover, docs)
                val merged = out.toMutableList()
                extra.forEach { e ->
                    val i = merged.indexOfFirst { it.title.equals(e.title, ignoreCase = true) }
                    if (i >= 0) merged[i] = merged[i].copy(folderIds = merged[i].folderIds + e.folderIds) else merged += e
                }
                return merged to DraftOutcome(engineName(engine))
            }
        }
        return patternSeries(folders, docs) to DraftOutcome(engineName(Pipeline.PATTERN))
    }

    private fun patternSeries(folders: List<FolderRow>, docs: Map<Long, List<DocumentRow>>): List<SeriesSuggestion> {
        fun bucket(type: String): String = when (type) {
            "Letter", "Telegram", "Memorandum" -> "Correspondence"
            "Minutes", "Report" -> "Minutes and reports"
            "Financial record" -> "Financial records"
            "Clipping", "Publication", "Program" -> "Printed materials"
            "Photograph" -> "Photographs"
            "Speech", "Notes" -> "Speeches and writings"
            "Legal document", "Form" -> "Legal and administrative records"
            else -> "Subject files"
        }
        val grouped = folders.groupBy { f ->
            val types = docs[f.folder.id].orEmpty().map { it.document.displayType() }.filter { it.isNotBlank() }
            bucket(types.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key.orEmpty())
        }
        val order = listOf(
            "Correspondence", "Minutes and reports", "Speeches and writings", "Financial records",
            "Legal and administrative records", "Printed materials", "Photographs", "Subject files",
        )
        return order.mapNotNull { name ->
            grouped[name]?.let { fs -> SeriesSuggestion(name, "", fs.map { it.folder.id }) }
        }
    }

    // ------------------------------------------------------------------ collection

    suspend fun computedExtent(collectionId: Long): String {
        val s = dao.collectionSummary(collectionId).first() ?: return ""
        fun n(count: Int, word: String) = "$count $word${if (count == 1) "" else "s"}"
        if (s.boxCount == 0) return ""
        val boxes = if (s.boxCount == 1) "1 box" else "${s.boxCount} boxes"
        return "$boxes (${n(s.folderCount, "folder")}, ${n(s.documentCount, "item")}, ${n(s.pageCount, "page")})"
    }

    suspend fun computedDates(collectionId: Long): String =
        Dates.range(dao.allDocumentRows(collectionId).map { sortOf(it) })

    suspend fun draftCollection(collectionId: Long): DraftOutcome {
        val c = dao.getCollection(collectionId) ?: return DraftOutcome("none")
        val drafts = mutableMapOf<String, String>()
        val dates = computedDates(collectionId)
        val extent = computedExtent(collectionId)
        if (dates.isNotBlank() && dates != c.dateRange) drafts[Fields.DATE_RANGE] = dates
        if (extent.isNotBlank() && extent != c.extent) drafts[Fields.EXTENT] = extent

        val folders = dao.folderRowsOnce(collectionId)
        val series = dao.seriesList(collectionId)
        if (series.isNotEmpty()) {
            val arrangement = "The collection is arranged in ${series.size} series: " +
                series.joinToString("; ") { "Series ${roman(it.number)}. ${it.title}" } + "."
            if (arrangement != c.arrangement) drafts[Fields.ARRANGEMENT] = arrangement
        } else if (folders.isNotEmpty()) {
            val a = "Materials are described in their original order by box and folder."
            if (a != c.arrangement) drafts[Fields.ARRANGEMENT] = a
        }

        val engine = pick()
        if (engine != Pipeline.PATTERN && folders.isNotEmpty()) {
            val docs = dao.allDocumentRows(collectionId).groupBy { it.document.folderId }
            val lines = folders.joinToString("\n") { f ->
                val title = f.folder.displayTitle().ifBlank { patternFolder(docs[f.folder.id].orEmpty())[Fields.TITLE].orEmpty() }
                val scope = f.folder.scopeNote.ifBlank { Proposals.parse(f.folder.proposals)[Fields.SCOPE].orEmpty() }
                val range = f.folder.dateRange.ifBlank { Dates.range(f.minDate, f.maxDate) }
                "Box ${f.boxNumber}, Folder ${f.folder.number}: ${title.ifBlank { "untitled" }}" +
                    (if (range.isNotBlank()) ", $range" else "") + (if (scope.isNotBlank()) " — $scope" else "")
            }
            // Two short asks so each answer fits Nano's 256-token limit.
            val folderText = lines.take(6500)
            DraftParser.plain(ask(engine, Prompts.collectionAbstract(c.title, c.creator, dates, extent, folderText)))
                .takeIf { it.isNotBlank() && it != c.abstractText }?.let { drafts[Fields.ABSTRACT] = it }
            DraftParser.plain(ask(engine, Prompts.collectionScope(c.title, c.creator, dates, extent, folderText)))
                .takeIf { it.isNotBlank() && it != c.scopeContent }?.let { drafts[Fields.SCOPE_CONTENT] = it }
        } else if (folders.isNotEmpty()) {
            val docs = dao.allDocumentRows(collectionId)
            val people = docs.flatMap { d ->
                ListField.split(d.document.people.ifBlank { Proposals.parse(d.document.proposals)[Fields.PEOPLE].orEmpty() })
            }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key }
            val scope = buildString {
                append("The collection contains ${docs.size} items in ${folders.size} folders")
                if (dates.isNotBlank()) append(", dating from $dates")
                append(".")
                if (people.isNotEmpty()) append(" Frequently named individuals include ${joinNames(people)}.")
            }
            drafts[Fields.SCOPE_CONTENT] = scope
        }
        repo.mergeCollectionProposals(collectionId, drafts)
        return DraftOutcome(engineName(engine), if (drafts.isEmpty()) "Nothing new to suggest." else "")
    }

    companion object {
        fun plural(type: String): String = when (type) {
            "Letter" -> "Correspondence"
            "Minutes" -> "Minutes"
            "Financial record" -> "Financial records"
            "Legal document" -> "Legal documents"
            "Notes" -> "Notes"
            "Other" -> "Miscellaneous"
            else -> type + "s"
        }

        fun joinNames(names: List<String>): String = when (names.size) {
            0 -> ""
            1 -> names[0]
            2 -> "${names[0]} and ${names[1]}"
            else -> names.dropLast(1).joinToString(", ") + ", and " + names.last()
        }

        fun roman(n: Int): String {
            if (n <= 0) return n.toString()
            val values = listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
                50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
            var x = n
            val sb = StringBuilder()
            for ((v, s) in values) while (x >= v) { sb.append(s); x -= v }
            return sb.toString()
        }
    }
}

package com.brianrobinson.fonds.data

import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Repository(
    private val db: FondsDatabase,
    val images: ImageStore,
) {
    val dao: FondsDao = db.dao()

    private fun now() = System.currentTimeMillis()

    // ------------------------------------------------------------------ collections

    suspend fun createCollection(title: String, creator: String, repository: String, identifier: String): Long {
        val color = (now() / 1000 % CollectionColors).toInt()
        return dao.insertCollection(
            CollectionEntity(
                title = title.trim(),
                creator = creator.trim(),
                repository = repository.trim(),
                identifier = identifier.trim(),
                colorIndex = color,
            ),
        )
    }

    suspend fun saveCollection(c: CollectionEntity) = dao.updateCollection(c.copy(updatedAt = now()))

    suspend fun deleteCollection(id: Long) {
        dao.deleteCollection(id)
        withContext(Dispatchers.IO) { images.deleteCollectionDir(id) }
    }

    suspend fun setCursor(collectionId: Long, box: Int, folder: Int) =
        dao.setCursor(collectionId, box.coerceAtLeast(1), folder.coerceAtLeast(1))

    suspend fun mergeCollectionProposals(collectionId: Long, drafts: Map<String, String>) {
        val c = dao.getCollection(collectionId) ?: return
        val merged = Proposals.parse(c.proposals) + drafts.filterValues { it.isNotBlank() }
        dao.updateCollection(c.copy(proposals = Proposals.encode(merged), updatedAt = now()))
    }

    suspend fun acceptCollectionProposal(collectionId: Long, key: String) {
        val c = dao.getCollection(collectionId) ?: return
        val props = Proposals.parse(c.proposals)
        val v = props[key] ?: return
        dao.updateCollection(c.withField(key, v).copy(proposals = Proposals.encode(props - key), updatedAt = now()))
    }

    suspend fun dismissCollectionProposal(collectionId: Long, key: String) {
        val c = dao.getCollection(collectionId) ?: return
        dao.updateCollection(c.copy(proposals = Proposals.without(c.proposals, key)))
    }

    // ------------------------------------------------------------------ boxes & folders

    suspend fun ensureFolder(collectionId: Long, boxNumber: Int, folderNumber: Int): FolderEntity =
        db.withTransaction {
            val box = dao.findBox(collectionId, boxNumber)
                ?: BoxEntity(collectionId = collectionId, number = boxNumber).let { it.copy(id = dao.insertBox(it)) }
            dao.findFolder(box.id, folderNumber)
                ?: FolderEntity(boxId = box.id, number = folderNumber).let { it.copy(id = dao.insertFolder(it)) }
        }

    suspend fun findFolder(collectionId: Long, boxNumber: Int, folderNumber: Int): FolderEntity? {
        val box = dao.findBox(collectionId, boxNumber) ?: return null
        return dao.findFolder(box.id, folderNumber)
    }

    suspend fun setFolderLabel(collectionId: Long, boxNumber: Int, folderNumber: Int, label: String) {
        val existing = findFolder(collectionId, boxNumber, folderNumber)
        if (existing == null && label.isBlank()) return
        val f = existing ?: ensureFolder(collectionId, boxNumber, folderNumber)
        dao.updateFolder(f.copy(label = label))
    }

    suspend fun saveFolder(f: FolderEntity) = dao.updateFolder(f)

    suspend fun mergeFolderProposals(folderId: Long, drafts: Map<String, String>) {
        val f = dao.getFolder(folderId) ?: return
        val merged = Proposals.parse(f.proposals) + drafts.filterValues { it.isNotBlank() }
        dao.updateFolder(f.copy(proposals = Proposals.encode(merged)))
    }

    suspend fun acceptFolderProposal(folderId: Long, key: String) {
        val f = dao.getFolder(folderId) ?: return
        val props = Proposals.parse(f.proposals)
        val v = props[key] ?: return
        dao.updateFolder(f.withField(key, v).copy(proposals = Proposals.encode(props - key)))
    }

    suspend fun dismissFolderProposal(folderId: Long, key: String) {
        val f = dao.getFolder(folderId) ?: return
        dao.updateFolder(f.copy(proposals = Proposals.without(f.proposals, key)))
    }

    suspend fun deleteFolder(folderId: Long) {
        val f = dao.getFolder(folderId) ?: return
        val paths = dao.imagePathsForFolder(folderId)
        db.withTransaction {
            dao.deleteFolder(folderId)
            if (dao.folderCountInBox(f.boxId) == 0) dao.deleteBox(f.boxId)
        }
        withContext(Dispatchers.IO) { images.delete(paths) }
    }

    // ------------------------------------------------------------------ capture

    /**
     * Files one scanner result into Box/Folder. By default the whole scan is one document
     * (a three-page letter stays together); quick mode makes one document per page.
     */
    suspend fun addScan(
        collectionId: Long,
        boxNumber: Int,
        folderNumber: Int,
        uris: List<Uri>,
        handwritten: Boolean,
        onePerPage: Boolean,
    ): List<Long> {
        if (uris.isEmpty()) return emptyList()
        val folder = ensureFolder(collectionId, boxNumber, folderNumber)
        val paths = withContext(Dispatchers.IO) { uris.map { images.importUri(collectionId, it) } }
        val ids = mutableListOf<Long>()
        db.withTransaction {
            var seq = dao.maxDocSequence(folder.id)
            val groups = if (onePerPage) paths.map { listOf(it) } else listOf(paths)
            for (group in groups) {
                seq += 1
                val docId = dao.insertDocument(
                    DocumentEntity(folderId = folder.id, sequence = seq, handwritten = handwritten),
                )
                group.forEachIndexed { i, p ->
                    dao.insertPage(PageEntity(documentId = docId, sequence = i + 1, imagePath = p))
                }
                ids += docId
            }
            dao.touchCollection(collectionId, now())
        }
        return ids
    }

    // ------------------------------------------------------------------ single items

    /** The hidden collection that holds single items. Created the first time it's needed. */
    suspend fun inbox(): CollectionEntity = db.withTransaction {
        dao.findInbox() ?: CollectionEntity(title = "Single items", kind = CollectionKind.INBOX).let {
            it.copy(id = dao.insertCollection(it))
        }
    }

    suspend fun addSingleItems(
        uris: List<Uri>,
        handwritten: Boolean,
        onePerPage: Boolean,
        sourceRepository: String,
        sourceCollection: String,
        sourceLocation: String,
    ): List<Long> {
        val box = inbox()
        val ids = addScan(box.id, 1, 1, uris, handwritten, onePerPage)
        if (sourceRepository.isNotBlank() || sourceCollection.isNotBlank() || sourceLocation.isNotBlank()) {
            db.withTransaction {
                ids.forEach { id ->
                    dao.getDocument(id)?.let { d ->
                        dao.updateDocument(
                            d.copy(
                                sourceRepository = sourceRepository.trim(),
                                sourceCollection = sourceCollection.trim(),
                                sourceLocation = sourceLocation.trim(),
                            ),
                        )
                    }
                }
            }
        }
        return ids
    }

    /**
     * Files items into a collection at Box/Folder (creating either if needed), keeping their order.
     * Works for single items and for moving items between collections.
     */
    suspend fun moveToCollection(docIds: List<Long>, collectionId: Long, box: Int, folder: Int, folderLabel: String = ""): Long {
        val target = ensureFolder(collectionId, box, folder)
        if (folderLabel.isNotBlank() && target.label.isBlank()) dao.updateFolder(target.copy(label = folderLabel.trim()))
        db.withTransaction {
            val docs = docIds.mapNotNull { dao.getDocument(it) }.sortedBy { it.id }
            docs.forEach { moveToFolder(it.id, target.id) }
            dao.touchCollection(collectionId, now())
        }
        return target.id
    }

    suspend fun moveToSingleItems(docIds: List<Long>) {
        val inbox = inbox()
        moveToCollection(docIds, inbox.id, 1, 1)
    }

    // ------------------------------------------------------------------ documents

    suspend fun saveDocumentField(docId: Long, key: String, value: String) {
        val d = dao.getDocument(docId) ?: return
        dao.updateDocument(d.withField(key, value.trim()).copy(updatedAt = now()))
    }

    suspend fun saveDocument(d: DocumentEntity) = dao.updateDocument(d.copy(updatedAt = now()))

    /** Saves the display date and keeps the hidden sortable date in step with it. */
    suspend fun saveDocumentDate(docId: Long, text: String) {
        val d = dao.getDocument(docId) ?: return
        dao.updateDocument(d.copy(date = text.trim(), dateSort = Dates.sortFromDacs(text), updatedAt = now()))
    }

    suspend fun saveFolderField(folderId: Long, key: String, value: String) {
        val f = dao.getFolder(folderId) ?: return
        val next = when (key) {
            "label" -> f.copy(label = value.trim())
            "note" -> f.copy(note = value)
            else -> f.withField(key, value.trim())
        }
        if (next != f) dao.updateFolder(next)
    }

    suspend fun setFolderSeries(folderId: Long, seriesId: Long?) {
        val f = dao.getFolder(folderId) ?: return
        dao.updateFolder(f.copy(seriesId = seriesId))
    }

    suspend fun saveCollectionField(collectionId: Long, key: String, value: String) {
        val c = dao.getCollection(collectionId) ?: return
        val next = c.withAnyField(key, value)
        if (next != c) dao.updateCollection(next.copy(updatedAt = now()))
    }

    suspend fun saveCollectionFields(edit: CollectionEntity) {
        val cur = dao.getCollection(edit.id) ?: return
        dao.updateCollection(
            edit.copy(
                proposals = cur.proposals,
                cursorBox = cur.cursorBox,
                cursorFolder = cur.cursorFolder,
                colorIndex = cur.colorIndex,
                updatedAt = now(),
            ),
        )
    }

    /** Add one suggested name/subject from a list proposal, leaving the rest pending. */
    suspend fun acceptListItem(docId: Long, key: String, item: String) {
        val d = dao.getDocument(docId) ?: return
        val props = Proposals.parse(d.proposals).toMutableMap()
        val current = ListField.split(d.field(key))
        val next = d.withField(key, ListField.join(current + item))
        val left = ListField.split(props[key].orEmpty()).filterNot { it.equals(item, ignoreCase = true) }
        if (left.isEmpty()) props.remove(key) else props[key] = ListField.join(left)
        dao.updateDocument(
            next.copy(
                proposals = Proposals.encode(props),
                status = statusAfterReview(d.status, props),
                updatedAt = now(),
            ),
        )
    }

    private fun DocumentEntity.mergedWith(key: String, value: String): DocumentEntity =
        if (key in ListField.KEYS) withField(key, ListField.join(ListField.split(field(key)) + ListField.split(value)))
        else withField(key, value)

    suspend fun acceptProposal(docId: Long, key: String) {
        val d = dao.getDocument(docId) ?: return
        val props = Proposals.parse(d.proposals)
        val v = props[key] ?: return
        var next = d.mergedWith(key, v)
        var remaining = props - key
        if (key == Fields.DATE) {
            props[Fields.DATE_SORT]?.let { next = next.withField(Fields.DATE_SORT, it) }
            remaining = remaining - Fields.DATE_SORT
        }
        dao.updateDocument(
            next.copy(
                proposals = Proposals.encode(remaining),
                status = statusAfterReview(d.status, remaining),
                updatedAt = now(),
            ),
        )
    }

    suspend fun dismissProposal(docId: Long, key: String) {
        val d = dao.getDocument(docId) ?: return
        val remaining = Proposals.parse(d.proposals) - key - (if (key == Fields.DATE) Fields.DATE_SORT else "")
        dao.updateDocument(
            d.copy(
                proposals = Proposals.encode(remaining),
                status = statusAfterReview(d.status, remaining),
                updatedAt = now(),
            ),
        )
    }

    /** Accept every pending draft on the item and mark it reviewed. */
    suspend fun approveDocument(docId: Long) {
        val d = dao.getDocument(docId) ?: return
        var next = d
        Proposals.parse(d.proposals).forEach { (k, v) -> next = next.mergedWith(k, v) }
        dao.updateDocument(
            next.copy(
                proposals = "",
                reviewed = true,
                status = if (d.status == DocStatus.DRAFTED) DocStatus.DONE else d.status,
                updatedAt = now(),
            ),
        )
    }

    suspend fun approveDocuments(ids: List<Long>) = db.withTransaction { ids.forEach { approveDocument(it) } }

    suspend fun setReviewed(docId: Long, reviewed: Boolean) {
        val d = dao.getDocument(docId) ?: return
        dao.updateDocument(d.copy(reviewed = reviewed, updatedAt = now()))
    }

    suspend fun setHandwritten(docId: Long, handwritten: Boolean) {
        val d = dao.getDocument(docId) ?: return
        dao.updateDocument(d.copy(handwritten = handwritten, updatedAt = now()))
    }

    suspend fun setNote(docId: Long, note: String) {
        val d = dao.getDocument(docId) ?: return
        dao.updateDocument(d.copy(note = note, updatedAt = now()))
    }

    suspend fun updatePageText(pageId: Long, text: String) {
        val p = dao.getPage(pageId) ?: return
        if (p.text == text) return
        dao.updatePage(p.copy(text = text, textSource = "edited"))
    }

    /** Re-queue after an error: skip text recognition if every page already has text. */
    suspend fun retry(docId: Long) {
        val pages = dao.pages(docId)
        val status = if (pages.isNotEmpty() && pages.all { it.text.isNotBlank() }) DocStatus.READ else DocStatus.QUEUED
        dao.setStatus(docId, status, "", now())
    }

    suspend fun requeueDescription(docId: Long) = dao.setStatus(docId, DocStatus.READ, "", now())

    suspend fun rereadText(docId: Long) {
        db.withTransaction {
            dao.pages(docId).forEach { dao.updatePage(it.copy(text = "", textSource = "")) }
            dao.setStatus(docId, DocStatus.QUEUED, "", now())
        }
    }

    suspend fun deleteDocument(docId: Long) {
        val d = dao.getDocument(docId) ?: return
        val paths = dao.imagePathsForDocument(docId)
        db.withTransaction {
            dao.deleteDocument(docId)
            resequenceDocuments(d.folderId)
        }
        withContext(Dispatchers.IO) { images.delete(paths) }
    }

    suspend fun deletePage(pageId: Long) {
        val p = dao.getPage(pageId) ?: return
        val pages = dao.pages(p.documentId)
        if (pages.size <= 1) {
            deleteDocument(p.documentId)
            return
        }
        db.withTransaction {
            dao.deletePage(pageId)
            dao.pages(p.documentId).forEachIndexed { i, page ->
                if (page.sequence != i + 1) dao.updatePage(page.copy(sequence = i + 1))
            }
        }
        withContext(Dispatchers.IO) { images.delete(listOf(p.imagePath)) }
    }

    suspend fun moveDocument(docId: Long, up: Boolean) = db.withTransaction {
        val d = dao.getDocument(docId) ?: return@withTransaction
        val docs = dao.documentsInFolder(d.folderId)
        val i = docs.indexOfFirst { it.id == docId }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in docs.indices) return@withTransaction
        val other = docs[j]
        dao.updateDocument(d.copy(sequence = other.sequence))
        dao.updateDocument(other.copy(sequence = d.sequence))
    }

    /** Append the next item's pages to this one (two scans that are really one letter). */
    suspend fun mergeWithNext(docId: Long): Boolean = db.withTransaction {
        val d = dao.getDocument(docId) ?: return@withTransaction false
        val docs = dao.documentsInFolder(d.folderId)
        val i = docs.indexOfFirst { it.id == docId }
        val next = docs.getOrNull(i + 1) ?: return@withTransaction false
        var seq = dao.pages(docId).size
        dao.pages(next.id).forEach { p ->
            seq += 1
            dao.updatePage(p.copy(documentId = docId, sequence = seq))
        }
        dao.deleteDocument(next.id)
        dao.updateDocument(d.copy(status = DocStatus.READ, reviewed = false, updatedAt = now()))
        resequenceDocuments(d.folderId)
        true
    }

    /** Split before [pageIndex] (0-based): those pages become a new item right after this one. */
    suspend fun splitAt(docId: Long, pageIndex: Int): Long? = db.withTransaction {
        val d = dao.getDocument(docId) ?: return@withTransaction null
        val pages = dao.pages(docId)
        if (pageIndex <= 0 || pageIndex >= pages.size) return@withTransaction null
        dao.documentsInFolder(d.folderId).filter { it.sequence > d.sequence }.forEach {
            dao.updateDocument(it.copy(sequence = it.sequence + 1))
        }
        val newId = dao.insertDocument(
            DocumentEntity(
                folderId = d.folderId,
                sequence = d.sequence + 1,
                handwritten = d.handwritten,
                status = DocStatus.READ,
            ),
        )
        pages.drop(pageIndex).forEachIndexed { i, p ->
            dao.updatePage(p.copy(documentId = newId, sequence = i + 1))
        }
        dao.updateDocument(d.copy(status = DocStatus.READ, reviewed = false, updatedAt = now()))
        newId
    }

    suspend fun moveToFolder(docId: Long, targetFolderId: Long) = db.withTransaction {
        val d = dao.getDocument(docId) ?: return@withTransaction
        if (d.folderId == targetFolderId) return@withTransaction
        val seq = dao.maxDocSequence(targetFolderId) + 1
        dao.updateDocument(d.copy(folderId = targetFolderId, sequence = seq, updatedAt = now()))
        resequenceDocuments(d.folderId)
    }

    private suspend fun resequenceDocuments(folderId: Long) {
        dao.documentsInFolder(folderId).forEachIndexed { i, doc ->
            if (doc.sequence != i + 1) dao.updateDocument(doc.copy(sequence = i + 1))
        }
    }

    // ------------------------------------------------------------------ series

    suspend fun createSeries(collectionId: Long, title: String, scopeNote: String = ""): Long {
        val n = dao.maxSeriesNumber(collectionId) + 1
        return dao.insertSeries(SeriesEntity(collectionId = collectionId, number = n, title = title.trim(), scopeNote = scopeNote))
    }

    suspend fun saveSeries(s: SeriesEntity) = dao.updateSeries(s)

    suspend fun deleteSeries(id: Long) = db.withTransaction {
        val s = dao.getSeries(id) ?: return@withTransaction
        dao.deleteSeries(id)
        dao.seriesList(s.collectionId).forEachIndexed { i, other ->
            if (other.number != i + 1) dao.updateSeries(other.copy(number = i + 1))
        }
    }

    suspend fun moveSeries(id: Long, up: Boolean) = db.withTransaction {
        val s = dao.getSeries(id) ?: return@withTransaction
        val all = dao.seriesList(s.collectionId)
        val i = all.indexOfFirst { it.id == id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in all.indices) return@withTransaction
        val other = all[j]
        dao.updateSeries(s.copy(number = other.number))
        dao.updateSeries(other.copy(number = s.number))
    }

    suspend fun assignFolders(folderIds: List<Long>, seriesId: Long?) {
        if (folderIds.isNotEmpty()) dao.assignSeries(folderIds, seriesId)
    }

    companion object {
        const val CollectionColors = 6

        fun statusAfterReview(current: String, remaining: Map<String, String>): String =
            if (current == DocStatus.DRAFTED && remaining.keys.none { it in Fields.DOCUMENT_ORDER }) DocStatus.DONE else current
    }
}

fun CollectionEntity.withField(key: String, value: String): CollectionEntity = when (key) {
    Fields.ABSTRACT -> copy(abstractText = value)
    Fields.SCOPE_CONTENT -> copy(scopeContent = value)
    Fields.ARRANGEMENT -> copy(arrangement = value)
    Fields.DATE_RANGE -> copy(dateRange = value)
    Fields.EXTENT -> copy(extent = value)
    else -> this
}

object CollectionKeys {
    const val TITLE = "title"
    const val CREATOR = "creator"
    const val REPOSITORY = "repository"
    const val IDENTIFIER = "identifier"
    const val BIOHIST = "bioHist"
    const val ACCESS = "accessRestrict"
    const val USE = "useRestrict"
    const val CITATION = "preferredCitation"
    const val PROCESSING = "processingInfo"
}

fun CollectionEntity.withAnyField(key: String, value: String): CollectionEntity = when (key) {
    CollectionKeys.TITLE -> if (value.isBlank()) this else copy(title = value.trim())
    CollectionKeys.CREATOR -> copy(creator = value.trim())
    CollectionKeys.REPOSITORY -> copy(repository = value.trim())
    CollectionKeys.IDENTIFIER -> copy(identifier = value.trim())
    CollectionKeys.BIOHIST -> copy(bioHist = value)
    CollectionKeys.ACCESS -> copy(accessRestrict = value)
    CollectionKeys.USE -> copy(useRestrict = value)
    CollectionKeys.CITATION -> copy(preferredCitation = value)
    CollectionKeys.PROCESSING -> copy(processingInfo = value)
    else -> withField(key, value)
}

fun CollectionEntity.anyField(key: String): String = when (key) {
    CollectionKeys.TITLE -> title
    CollectionKeys.CREATOR -> creator
    CollectionKeys.REPOSITORY -> repository
    CollectionKeys.IDENTIFIER -> identifier
    CollectionKeys.BIOHIST -> bioHist
    CollectionKeys.ACCESS -> accessRestrict
    CollectionKeys.USE -> useRestrict
    CollectionKeys.CITATION -> preferredCitation
    CollectionKeys.PROCESSING -> processingInfo
    Fields.ABSTRACT -> abstractText
    Fields.SCOPE_CONTENT -> scopeContent
    Fields.ARRANGEMENT -> arrangement
    Fields.DATE_RANGE -> dateRange
    Fields.EXTENT -> extent
    else -> ""
}

fun FolderEntity.withField(key: String, value: String): FolderEntity = when (key) {
    Fields.TITLE -> copy(title = value)
    Fields.SCOPE -> copy(scopeNote = value)
    Fields.DATE_RANGE -> copy(dateRange = value)
    else -> this
}

/** Reads "Box 12, Folder 3", "B12 F3", "Bx. 12 / Fol. 3" and the like. */
object LocationParse {
    private val BOX = Regex("""(?i)\b(?:box|bx\.?|b)\s*#?\s*(\d{1,4})""")
    private val FOLDER = Regex("""(?i)\b(?:folder|fol\.?|fldr\.?|f)\s*#?\s*(\d{1,4})""")

    fun box(text: String): Int? = BOX.find(text)?.groupValues?.get(1)?.toIntOrNull()
    fun folder(text: String): Int? = FOLDER.find(text)?.groupValues?.get(1)?.toIntOrNull()
}

object Dates {
    private val MONTHS = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
    )
    private val DACS = Regex("""^(?:circa\s+|ca\.\s*|c\.\s*)?(\d{4})(?:\s+([A-Za-z]+)\.?)?(?:\s+(\d{1,2}))?""", RegexOption.IGNORE_CASE)

    /** "1952 January 5" → "1952-01-05"; "circa 1950" → "1950"; "" if it can't tell. */
    fun sortFromDacs(text: String): String {
        val m = DACS.find(text.trim()) ?: return ""
        val y = m.groupValues[1]
        val monthToken = m.groupValues[2].lowercase()
        val mi = if (monthToken.length >= 3) MONTHS.indexOfFirst { it.startsWith(monthToken.take(3)) } + 1 else 0
        if (mi == 0) return y
        val day = m.groupValues[3].toIntOrNull()?.takeIf { it in 1..31 }
        return buildString {
            append(y).append("-%02d".format(mi))
            if (day != null) append("-%02d".format(day))
        }
    }

    fun year(sort: String?): Int? = sort?.take(4)?.toIntOrNull()?.takeIf { it in 1000..2999 }

    /** DACS-style inclusive range from sortable dates: "1948-1952", "1952", or "". */
    fun range(sorts: List<String?>): String {
        val years = sorts.mapNotNull { year(it) }
        if (years.isEmpty()) return ""
        val lo = years.min()
        val hi = years.max()
        return if (lo == hi) "$lo" else "$lo-$hi"
    }

    fun range(min: String?, max: String?): String = range(listOf(min, max))
}

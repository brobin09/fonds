package com.brianrobinson.fonds.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Physical hierarchy:   Collection → Box → Folder → Document (item) → Page (image)
 * Intellectual layer:   Series, assigned at the folder level. Box/folder order is never
 *                       changed by arrangement, so original order is always preserved.
 *
 * Every describable record carries a `proposals` JSON map (field → AI-drafted value).
 * Proposals are never written into the real fields until the processor accepts them.
 */

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val creator: String = "",
    val repository: String = "",
    val identifier: String = "",
    val dateRange: String = "",
    val extent: String = "",
    val abstractText: String = "",
    val scopeContent: String = "",
    val bioHist: String = "",
    val arrangement: String = "",
    val accessRestrict: String = "",
    val useRestrict: String = "",
    val preferredCitation: String = "",
    val processingInfo: String = "",
    val proposals: String = "",
    val colorIndex: Int = 0,
    /** "collection" for real collections; "inbox" for the hidden home of single items. */
    val kind: String = CollectionKind.COLLECTION,
    val cursorBox: Int = 1,
    val cursorFolder: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

object CollectionKind {
    const val COLLECTION = "collection"
    const val INBOX = "inbox"
}

@Entity(
    tableName = "boxes",
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("collectionId"), Index(value = ["collectionId", "number"], unique = true)],
)
data class BoxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionId: Long,
    val number: Int,
    val note: String = "",
)

@Entity(
    tableName = "series",
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("collectionId")],
)
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionId: Long,
    val number: Int,
    val title: String,
    val dateRange: String = "",
    val scopeNote: String = "",
    val proposals: String = "",
)

@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = BoxEntity::class,
            parentColumns = ["id"],
            childColumns = ["boxId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("boxId"), Index("seriesId"), Index(value = ["boxId", "number"], unique = true)],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val boxId: Long,
    val number: Int,
    /** Exactly as written on the folder tab, typed during capture. */
    val label: String = "",
    /** Approved devised title for the finding aid. */
    val title: String = "",
    /** Approved date range (overrides the computed one when set). */
    val dateRange: String = "",
    val scopeNote: String = "",
    val seriesId: Long? = null,
    val note: String = "",
    val proposals: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

object DocStatus {
    const val QUEUED = "queued"          // waiting for text recognition
    const val READING = "reading"        // text recognition running
    const val READ = "read"              // text ready, waiting for description
    const val DESCRIBING = "describing"  // AI drafting
    const val DRAFTED = "drafted"        // proposals waiting for review
    const val DONE = "done"              // nothing pending from the machine
    const val ERROR = "error"

    val BUSY = listOf(QUEUED, READING, READ, DESCRIBING)
}

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("folderId")],
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val sequence: Int,
    val title: String = "",
    val date: String = "",
    val dateSort: String = "",
    val docType: String = "",
    val creator: String = "",
    val recipient: String = "",
    val summary: String = "",
    val people: String = "",
    val places: String = "",
    val organizations: String = "",
    val subjects: String = "",
    val note: String = "",
    /** Where a single item came from (used for its citation until it joins a collection). */
    val sourceRepository: String = "",
    val sourceCollection: String = "",
    val sourceLocation: String = "",
    val handwritten: Boolean = false,
    val status: String = DocStatus.QUEUED,
    val statusDetail: String = "",
    val engine: String = "",
    val proposals: String = "",
    val reviewed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId")],
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val sequence: Int,
    val imagePath: String,
    val text: String = "",
    /** "mlkit" (on-device OCR), "claude" (cloud transcription) or "edited" (by you). */
    val textSource: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

// ---------- query result rows ----------

data class CollectionSummary(
    @Embedded val collection: CollectionEntity,
    val boxCount: Int,
    val folderCount: Int,
    val documentCount: Int,
    val pageCount: Int,
    val reviewedCount: Int,
    val draftCount: Int,
    val busyCount: Int,
    val thumbPath: String?,
)

data class FolderRow(
    @Embedded val folder: FolderEntity,
    val boxNumber: Int,
    val collectionId: Long,
    val seriesTitle: String?,
    val seriesNumber: Int?,
    val docCount: Int,
    val pageCount: Int,
    val reviewedCount: Int,
    val draftCount: Int,
    val busyCount: Int,
    val errorCount: Int,
    val minDate: String?,
    val maxDate: String?,
    val thumbPath: String?,
)

data class DocumentRow(
    @Embedded val document: DocumentEntity,
    val pageCount: Int,
    val thumbPath: String?,
    val boxNumber: Int,
    val folderNumber: Int,
    val collectionId: Long,
)

data class SearchHit(
    @Embedded val document: DocumentEntity,
    val pageCount: Int,
    val thumbPath: String?,
    val boxNumber: Int,
    val folderNumber: Int,
    val collectionId: Long,
    val collectionTitle: String,
    val collectionKind: String,
    val matchText: String?,
)

data class DocumentContext(
    val documentId: Long,
    val folderId: Long,
    val folderNumber: Int,
    val folderLabel: String,
    val folderTitle: String,
    val boxId: Long,
    val boxNumber: Int,
    val collectionId: Long,
    val collectionTitle: String,
    val repository: String,
    val collectionKind: String,
)

data class FolderContext(
    val folderId: Long,
    val folderNumber: Int,
    val boxId: Long,
    val boxNumber: Int,
    val collectionId: Long,
    val collectionTitle: String,
)

package com.brianrobinson.fonds.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val SUMMARY = """
SELECT c.*,
 (SELECT COUNT(*) FROM boxes b WHERE b.collectionId = c.id) AS boxCount,
 (SELECT COUNT(*) FROM folders f JOIN boxes b ON f.boxId = b.id WHERE b.collectionId = c.id) AS folderCount,
 (SELECT COUNT(*) FROM documents d JOIN folders f ON d.folderId = f.id JOIN boxes b ON f.boxId = b.id
    WHERE b.collectionId = c.id) AS documentCount,
 (SELECT COUNT(*) FROM pages p JOIN documents d ON p.documentId = d.id JOIN folders f ON d.folderId = f.id
    JOIN boxes b ON f.boxId = b.id WHERE b.collectionId = c.id) AS pageCount,
 (SELECT COUNT(*) FROM documents d JOIN folders f ON d.folderId = f.id JOIN boxes b ON f.boxId = b.id
    WHERE b.collectionId = c.id AND d.reviewed = 1) AS reviewedCount,
 (SELECT COUNT(*) FROM documents d JOIN folders f ON d.folderId = f.id JOIN boxes b ON f.boxId = b.id
    WHERE b.collectionId = c.id AND d.reviewed = 0 AND d.status IN ('drafted','done')) AS draftCount,
 (SELECT COUNT(*) FROM documents d JOIN folders f ON d.folderId = f.id JOIN boxes b ON f.boxId = b.id
    WHERE b.collectionId = c.id AND d.status IN ('queued','reading','read','describing')) AS busyCount,
 (SELECT p.imagePath FROM pages p JOIN documents d ON p.documentId = d.id JOIN folders f ON d.folderId = f.id
    JOIN boxes b ON f.boxId = b.id WHERE b.collectionId = c.id ORDER BY p.id DESC LIMIT 1) AS thumbPath
FROM collections c
"""

private const val FOLDER_ROW = """
SELECT f.*, b.number AS boxNumber, b.collectionId AS collectionId,
 s.title AS seriesTitle, s.number AS seriesNumber,
 (SELECT COUNT(*) FROM documents d WHERE d.folderId = f.id) AS docCount,
 (SELECT COUNT(*) FROM pages p JOIN documents d ON p.documentId = d.id WHERE d.folderId = f.id) AS pageCount,
 (SELECT COUNT(*) FROM documents d WHERE d.folderId = f.id AND d.reviewed = 1) AS reviewedCount,
 (SELECT COUNT(*) FROM documents d WHERE d.folderId = f.id AND d.reviewed = 0
    AND d.status IN ('drafted','done')) AS draftCount,
 (SELECT COUNT(*) FROM documents d WHERE d.folderId = f.id
    AND d.status IN ('queued','reading','read','describing')) AS busyCount,
 (SELECT COUNT(*) FROM documents d WHERE d.folderId = f.id AND d.status = 'error') AS errorCount,
 (SELECT MIN(d.dateSort) FROM documents d WHERE d.folderId = f.id AND d.dateSort != '') AS minDate,
 (SELECT MAX(d.dateSort) FROM documents d WHERE d.folderId = f.id AND d.dateSort != '') AS maxDate,
 (SELECT p.imagePath FROM pages p JOIN documents d ON p.documentId = d.id WHERE d.folderId = f.id
    ORDER BY d.sequence, p.sequence LIMIT 1) AS thumbPath
FROM folders f
JOIN boxes b ON f.boxId = b.id
LEFT JOIN series s ON f.seriesId = s.id
"""

private const val DOC_ROW = """
SELECT d.*,
 (SELECT COUNT(*) FROM pages p WHERE p.documentId = d.id) AS pageCount,
 (SELECT p2.imagePath FROM pages p2 WHERE p2.documentId = d.id ORDER BY p2.sequence LIMIT 1) AS thumbPath,
 b.number AS boxNumber, f.number AS folderNumber, b.collectionId AS collectionId
FROM documents d
JOIN folders f ON d.folderId = f.id
JOIN boxes b ON f.boxId = b.id
"""

private const val SEARCH = """
SELECT d.*,
 (SELECT COUNT(*) FROM pages p WHERE p.documentId = d.id) AS pageCount,
 (SELECT p2.imagePath FROM pages p2 WHERE p2.documentId = d.id ORDER BY p2.sequence LIMIT 1) AS thumbPath,
 b.number AS boxNumber, f.number AS folderNumber, b.collectionId AS collectionId,
 c.title AS collectionTitle, c.kind AS collectionKind,
 (SELECT p3.text FROM pages p3 WHERE p3.documentId = d.id AND p3.text LIKE :q ORDER BY p3.sequence LIMIT 1) AS matchText
FROM documents d
JOIN folders f ON d.folderId = f.id
JOIN boxes b ON f.boxId = b.id
JOIN collections c ON b.collectionId = c.id
WHERE (:collectionId = 0 OR b.collectionId = :collectionId) AND (
  d.title LIKE :q OR d.summary LIKE :q OR d.people LIKE :q OR d.places LIKE :q
  OR d.organizations LIKE :q OR d.subjects LIKE :q OR d.creator LIKE :q OR d.recipient LIKE :q
  OR d.note LIKE :q OR d.proposals LIKE :q OR f.label LIKE :q OR f.title LIKE :q
  OR EXISTS (SELECT 1 FROM pages p4 WHERE p4.documentId = d.id AND p4.text LIKE :q)
)
ORDER BY c.title, b.number, f.number, d.sequence
LIMIT 300
"""

@Dao
interface FondsDao {

    // ---------- collections ----------
    @Insert
    suspend fun insertCollection(c: CollectionEntity): Long

    @Update
    suspend fun updateCollection(c: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: Long)

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getCollection(id: Long): CollectionEntity?

    @Query("SELECT * FROM collections WHERE id = :id")
    fun collectionFlow(id: Long): Flow<CollectionEntity?>

    @Query("$SUMMARY WHERE c.kind = 'collection' ORDER BY c.updatedAt DESC")
    fun collectionSummaries(): Flow<List<CollectionSummary>>

    @Query("$SUMMARY WHERE c.kind = 'inbox' LIMIT 1")
    fun inboxSummary(): Flow<CollectionSummary?>

    @Query("SELECT * FROM collections WHERE kind = 'inbox' LIMIT 1")
    suspend fun findInbox(): CollectionEntity?

    @Query("SELECT * FROM collections WHERE kind = 'collection' ORDER BY updatedAt DESC")
    fun regularCollectionsFlow(): Flow<List<CollectionEntity>>

    @Query("$SUMMARY WHERE c.id = :id")
    fun collectionSummary(id: Long): Flow<CollectionSummary?>

    @Query("SELECT * FROM collections ORDER BY title")
    fun collectionsFlow(): Flow<List<CollectionEntity>>

    @Query("UPDATE collections SET updatedAt = :t WHERE id = :id")
    suspend fun touchCollection(id: Long, t: Long)

    @Query("UPDATE collections SET cursorBox = :box, cursorFolder = :folder WHERE id = :id")
    suspend fun setCursor(id: Long, box: Int, folder: Int)

    // ---------- boxes ----------
    @Query("SELECT * FROM boxes WHERE collectionId = :collectionId AND number = :number")
    suspend fun findBox(collectionId: Long, number: Int): BoxEntity?

    @Insert
    suspend fun insertBox(b: BoxEntity): Long

    @Update
    suspend fun updateBox(b: BoxEntity)

    @Query("SELECT * FROM boxes WHERE id = :id")
    suspend fun getBox(id: Long): BoxEntity?

    @Query("SELECT * FROM boxes WHERE collectionId = :collectionId ORDER BY number")
    fun boxesFlow(collectionId: Long): Flow<List<BoxEntity>>

    @Query("SELECT * FROM boxes WHERE collectionId = :collectionId ORDER BY number")
    suspend fun boxes(collectionId: Long): List<BoxEntity>

    @Query("DELETE FROM boxes WHERE id = :id")
    suspend fun deleteBox(id: Long)

    @Query("SELECT COUNT(*) FROM folders WHERE boxId = :boxId")
    suspend fun folderCountInBox(boxId: Long): Int

    // ---------- folders ----------
    @Query("SELECT * FROM folders WHERE boxId = :boxId AND number = :number")
    suspend fun findFolder(boxId: Long, number: Int): FolderEntity?

    @Insert
    suspend fun insertFolder(f: FolderEntity): Long

    @Update
    suspend fun updateFolder(f: FolderEntity)

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolder(id: Long): FolderEntity?

    @Query("$FOLDER_ROW WHERE b.collectionId = :collectionId ORDER BY b.number, f.number")
    fun folderRows(collectionId: Long): Flow<List<FolderRow>>

    @Query("$FOLDER_ROW WHERE b.collectionId = :collectionId ORDER BY b.number, f.number")
    suspend fun folderRowsOnce(collectionId: Long): List<FolderRow>

    @Query("$FOLDER_ROW WHERE f.id = :id")
    fun folderRow(id: Long): Flow<FolderRow?>

    @Query("$FOLDER_ROW WHERE f.id = :id")
    suspend fun folderRowOnce(id: Long): FolderRow?

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Query("UPDATE folders SET seriesId = :seriesId WHERE id IN (:ids)")
    suspend fun assignSeries(ids: List<Long>, seriesId: Long?)

    @Query(
        """SELECT f.id AS folderId, f.number AS folderNumber, b.id AS boxId, b.number AS boxNumber,
           c.id AS collectionId, c.title AS collectionTitle
           FROM folders f JOIN boxes b ON f.boxId = b.id JOIN collections c ON b.collectionId = c.id
           WHERE f.id = :id""",
    )
    fun folderContext(id: Long): Flow<FolderContext?>

    // ---------- series ----------
    @Insert
    suspend fun insertSeries(s: SeriesEntity): Long

    @Update
    suspend fun updateSeries(s: SeriesEntity)

    @Query("DELETE FROM series WHERE id = :id")
    suspend fun deleteSeries(id: Long)

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun getSeries(id: Long): SeriesEntity?

    @Query("SELECT * FROM series WHERE collectionId = :collectionId ORDER BY number")
    fun seriesFlow(collectionId: Long): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE collectionId = :collectionId ORDER BY number")
    suspend fun seriesList(collectionId: Long): List<SeriesEntity>

    @Query("SELECT COALESCE(MAX(number), 0) FROM series WHERE collectionId = :collectionId")
    suspend fun maxSeriesNumber(collectionId: Long): Int

    // ---------- documents ----------
    @Insert
    suspend fun insertDocument(d: DocumentEntity): Long

    @Update
    suspend fun updateDocument(d: DocumentEntity)

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocument(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id")
    fun documentFlow(id: Long): Flow<DocumentEntity?>

    @Query("$DOC_ROW WHERE d.folderId = :folderId ORDER BY d.sequence")
    fun documentRows(folderId: Long): Flow<List<DocumentRow>>

    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY sequence")
    suspend fun documentsInFolder(folderId: Long): List<DocumentEntity>

    @Query(
        "$DOC_ROW WHERE b.collectionId = :collectionId AND d.reviewed = 0 " +
            "AND d.status IN ('drafted','done') ORDER BY b.number, f.number, d.sequence",
    )
    fun reviewQueue(collectionId: Long): Flow<List<DocumentRow>>

    @Query(
        "$DOC_ROW WHERE b.collectionId = :collectionId AND d.status IN " +
            "('queued','reading','read','describing','error') ORDER BY b.number, f.number, d.sequence",
    )
    fun inProgress(collectionId: Long): Flow<List<DocumentRow>>

    @Query("$DOC_ROW WHERE b.collectionId = :collectionId ORDER BY b.number, f.number, d.sequence")
    suspend fun allDocumentRows(collectionId: Long): List<DocumentRow>

    @Query("$DOC_ROW WHERE b.collectionId = :collectionId ORDER BY d.id DESC")
    fun newestDocumentRows(collectionId: Long): Flow<List<DocumentRow>>

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM documents WHERE folderId = :folderId")
    suspend fun maxDocSequence(folderId: Long): Int

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: Long)

    @Query(
        "SELECT * FROM documents WHERE status IN ('queued','read') " +
            "ORDER BY CASE status WHEN 'queued' THEN 0 ELSE 1 END, id LIMIT 1",
    )
    suspend fun nextPending(): DocumentEntity?

    @Query("UPDATE documents SET status = :status, statusDetail = :detail, updatedAt = :t WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, detail: String, t: Long)

    @Query("UPDATE documents SET status = 'queued' WHERE status = 'reading'")
    suspend fun resetReading()

    @Query("UPDATE documents SET status = 'read' WHERE status = 'describing'")
    suspend fun resetDescribing()

    @Query("SELECT COUNT(*) FROM documents WHERE status IN ('queued','reading','read','describing')")
    fun busyCount(): Flow<Int>

    @Query(
        """UPDATE documents SET status = 'read', statusDetail = '' WHERE reviewed = 0 AND (
           (status IN ('drafted','done') AND engine LIKE 'Pattern match (on-device AI failed)%')
           OR (status = 'error' AND statusDetail LIKE '%maxOutputTokens%'))""",
    )
    suspend fun requeueNanoFailures(): Int

    @Query(
        """SELECT d.id AS documentId, f.id AS folderId, f.number AS folderNumber, f.label AS folderLabel,
           f.title AS folderTitle, b.id AS boxId, b.number AS boxNumber, c.id AS collectionId,
           c.title AS collectionTitle, c.repository AS repository, c.kind AS collectionKind
           FROM documents d JOIN folders f ON d.folderId = f.id JOIN boxes b ON f.boxId = b.id
           JOIN collections c ON b.collectionId = c.id WHERE d.id = :id""",
    )
    fun documentContext(id: Long): Flow<DocumentContext?>

    @Query(
        """SELECT d.id AS documentId, f.id AS folderId, f.number AS folderNumber, f.label AS folderLabel,
           f.title AS folderTitle, b.id AS boxId, b.number AS boxNumber, c.id AS collectionId,
           c.title AS collectionTitle, c.repository AS repository, c.kind AS collectionKind
           FROM documents d JOIN folders f ON d.folderId = f.id JOIN boxes b ON f.boxId = b.id
           JOIN collections c ON b.collectionId = c.id WHERE d.id = :id""",
    )
    suspend fun documentContextOnce(id: Long): DocumentContext?

    // ---------- pages ----------
    @Insert
    suspend fun insertPage(p: PageEntity): Long

    @Update
    suspend fun updatePage(p: PageEntity)

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getPage(id: Long): PageEntity?

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY sequence")
    suspend fun pages(documentId: Long): List<PageEntity>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY sequence")
    fun pagesFlow(documentId: Long): Flow<List<PageEntity>>

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePage(id: Long)

    @Query(
        """SELECT p.imagePath FROM pages p JOIN documents d ON p.documentId = d.id
           JOIN folders f ON d.folderId = f.id JOIN boxes b ON f.boxId = b.id WHERE b.collectionId = :collectionId""",
    )
    suspend fun imagePathsForCollection(collectionId: Long): List<String>

    @Query("SELECT p.imagePath FROM pages p JOIN documents d ON p.documentId = d.id WHERE d.folderId = :folderId")
    suspend fun imagePathsForFolder(folderId: Long): List<String>

    @Query("SELECT imagePath FROM pages WHERE documentId = :documentId")
    suspend fun imagePathsForDocument(documentId: Long): List<String>

    @Query(
        """SELECT p.* FROM pages p JOIN documents d ON p.documentId = d.id JOIN folders f ON d.folderId = f.id
           JOIN boxes b ON f.boxId = b.id WHERE b.collectionId = :collectionId
           ORDER BY b.number, f.number, d.sequence, p.sequence""",
    )
    suspend fun allPages(collectionId: Long): List<PageEntity>

    // ---------- search ----------
    @Query(SEARCH)
    fun search(q: String, collectionId: Long): Flow<List<SearchHit>>
}

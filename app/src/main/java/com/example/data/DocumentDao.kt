package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY convertedTimestamp DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    fun getPagesForDocument(documentId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getPagesListForDocument(documentId: String): List<PageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageEntity>)

    @Query("UPDATE documents SET lastReadPage = :pageIndex, lastReadPosition = :position WHERE id = :documentId")
    suspend fun updateReadingPosition(documentId: String, pageIndex: Int, position: Int)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: String)

    @Query("DELETE FROM documents")
    suspend fun deleteAllDocuments()

    @Query("SELECT * FROM documents WHERE fileName LIKE '%' || :query || '%' ORDER BY convertedTimestamp DESC")
    fun searchDocumentsByName(query: String): Flow<List<DocumentEntity>>

    @Query("""
        SELECT DISTINCT d.* FROM documents d 
        INNER JOIN pages p ON d.id = p.documentId 
        WHERE p.markdownContent LIKE '%' || :query || '%' OR d.fileName LIKE '%' || :query || '%'
        ORDER BY d.convertedTimestamp DESC
    """)
    fun searchDocumentsByContent(query: String): Flow<List<DocumentEntity>>
}

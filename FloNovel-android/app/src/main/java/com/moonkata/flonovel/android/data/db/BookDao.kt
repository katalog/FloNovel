package com.moonkata.flonovel.android.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC, addedAt DESC")
    fun getAllOrderByRecent(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun getById(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE documentUri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: BookEntity): Long

    @Query("UPDATE books SET lastReadCharOffset = :offset, lastReadProgressPercent = :progress, lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun updateReadPosition(id: Long, offset: Int, progress: Float, timestamp: Long)

    @Query("UPDATE books SET totalCharCount = :totalCharCount, detectedEncoding = :encoding WHERE id = :id")
    suspend fun updateMeta(id: Long, totalCharCount: Int, encoding: String)

    @Query("UPDATE books SET relativePath = :relativePath WHERE id = :id")
    suspend fun updateRelativePath(id: Long, relativePath: String)

    /**
     * Follows a book whose file was preprocessed after it had been opened: new file, new URI, and
     * text of a new length, so the saved position is clamped into it.
     */
    @Query(
        "UPDATE books SET documentUri = :newUri, displayName = :displayName, relativePath = :relativePath, " +
            "lastReadCharOffset = MIN(lastReadCharOffset, :charCount), totalCharCount = :charCount WHERE documentUri = :oldUri",
    )
    suspend fun relocate(oldUri: String, newUri: String, displayName: String, relativePath: String, charCount: Int)

    /** Follows a book that sync moved or renamed: same text, new file. */
    @Query("UPDATE books SET documentUri = :newUri, displayName = :displayName, relativePath = :newRelativePath WHERE relativePath = :oldRelativePath")
    suspend fun moveByRelativePath(oldRelativePath: String, newRelativePath: String, newUri: String, displayName: String)

    @Delete
    suspend fun delete(book: BookEntity)
}

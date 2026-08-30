package com.huangder.lumibooks.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.data.local.model.QuoteWidgetData
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getNotesByBookId(bookId: String): Flow<List<NoteEntity>>

    @Query(
        "SELECT notes.id AS noteId, notes.bookId AS bookId, " +
            "notes.selectedText AS selectedText, notes.note AS noteText, " +
            "books.title AS bookTitle, books.author AS bookAuthor " +
            "FROM notes INNER JOIN books ON books.id = notes.bookId " +
            "WHERE TRIM(notes.selectedText) <> '' " +
            "ORDER BY RANDOM() LIMIT 1"
    )
    suspend fun getRandomWidgetQuote(): QuoteWidgetData?

    @Query(
        "SELECT notes.id AS noteId, notes.bookId AS bookId, " +
            "notes.selectedText AS selectedText, notes.note AS noteText, " +
            "books.title AS bookTitle, books.author AS bookAuthor " +
            "FROM notes INNER JOIN books ON books.id = notes.bookId " +
            "WHERE TRIM(notes.selectedText) <> '' " +
            "ORDER BY notes.id"
    )
    fun observeWidgetQuotes(): Flow<List<QuoteWidgetData>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Update
    suspend fun updateNotes(notes: List<NoteEntity>)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteNotesByIds(ids: List<Long>)

    @Transaction
    suspend fun applyAnnotationEdit(
        deleteIds: List<Long>,
        updates: List<NoteEntity>,
        inserts: List<NoteEntity>
    ) {
        if (deleteIds.isNotEmpty()) deleteNotesByIds(deleteIds)
        if (updates.isNotEmpty()) updateNotes(updates)
        if (inserts.isNotEmpty()) insertNotes(inserts)
    }

    @Query("DELETE FROM notes WHERE bookId = :bookId")
    suspend fun deleteAllNotesByBookId(bookId: String)

    @Query("DELETE FROM notes WHERE bookId = :bookId AND type NOT LIKE 'pdf_ink_%'")
    suspend fun deleteLegacyPdfAnnotationsByBookId(bookId: String)
}

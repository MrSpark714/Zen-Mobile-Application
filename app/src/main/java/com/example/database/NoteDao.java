package com.example.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.model.Note;

import java.util.List;

/**
 * Data Access Object (DAO) for Notes.
 *
 * Defines the database operations (CRUD) for the 'notes' table.
 * Room generates the implementation of these methods at compile time.
 * Methods returning LiveData notify observers whenever the underlying data changes.
 */
@Dao
public interface NoteDao {

    /**
     * Insert a new note into the database. If a note with the same primary key exists, replace it.
     * @param note The Note entity to be saved.
     * @return The row ID of the newly inserted note.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Note note);

    /**
     * Update an existing note in the database matching its primary key.
     * @param note The Note entity with updated values.
     */
    @Update
    void update(Note note);

    /**
     * Delete a note from the database.
     * @param note The Note entity to be removed.
     */
    @Delete
    void delete(Note note);

    /**
     * Delete a note directly by its unique ID.
     * @param id The ID of the note to delete.
     */
    @Query("DELETE FROM notes WHERE id = :id")
    void deleteById(int id);

    /**
     * Retrieve a specific note by ID synchronously (for background threads).
     * @param id The ID of the note.
     * @return The Note object if found, or null.
     */
    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    Note getNoteById(int id);

    /**
     * Fetch all notes ordered by most recently modified first.
     * Returns LiveData so UI automatically updates when notes are added, edited, or deleted.
     */
    @Query("SELECT * FROM notes ORDER BY is_important DESC, timestamp DESC")
    LiveData<List<Note>> getAllNotes();

    /**
     * Search notes where the title or content contains the search term.
     * @param query The search keyword.
     * @return LiveData list of matching notes.
     */
    @Query("SELECT * FROM notes WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY is_important DESC, timestamp DESC")
    LiveData<List<Note>> searchNotes(String query);
}

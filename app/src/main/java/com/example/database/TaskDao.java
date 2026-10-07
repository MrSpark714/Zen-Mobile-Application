package com.example.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.model.Task;

import java.util.List;

/**
 * Data Access Object (DAO) for Tasks.
 *
 * Defines CRUD methods for managing tasks in the SQLite database.
 */
@Dao
public interface TaskDao {

    /**
     * Insert a new task into the database.
     * @param task The Task entity to insert.
     * @return The generated row ID.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Task task);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Task> tasks);

    /**
     * Update an existing task (e.g. toggle completion state or update text).
     * @param task The Task entity to update.
     */
    @Update
    void update(Task task);

    /**
     * Delete a task from the database.
     * @param task The Task entity to delete.
     */
    @Delete
    void delete(Task task);

    /**
     * Delete a task by its unique ID.
     * @param id The ID of the task to delete.
     */
    @Query("DELETE FROM tasks WHERE id = :id")
    void deleteById(int id);

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    Task getTaskById(int id);

    /**
     * Fetch all tasks.
     * Uncompleted tasks (is_completed = 0) are listed first, followed by completed ones.
     * Within each group, items are ordered by newest timestamp first.
     */
    @Query("SELECT * FROM tasks ORDER BY is_completed ASC, timestamp DESC")
    LiveData<List<Task>> getAllTasks();

    @Query("SELECT * FROM tasks ORDER BY is_completed ASC, timestamp DESC")
    List<Task> getAllTasksSync();

    /**
     * Search tasks matching a query string in the description.
     * @param query The search keyword.
     * @return LiveData list of filtered tasks.
     */
    @Query("SELECT * FROM tasks WHERE description LIKE '%' || :query || '%' ORDER BY is_completed ASC, timestamp DESC")
    LiveData<List<Task>> searchTasks(String query);
}

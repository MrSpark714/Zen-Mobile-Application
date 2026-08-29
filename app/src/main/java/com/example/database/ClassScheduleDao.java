package com.example.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.model.ClassSchedule;

import java.util.List;

/**
 * Data Access Object for ClassSchedule operations.
 */
@Dao
public interface ClassScheduleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ClassSchedule schedule);

    @Update
    void update(ClassSchedule schedule);

    @Delete
    void delete(ClassSchedule schedule);

    @Query("SELECT * FROM class_schedules WHERE id = :id LIMIT 1")
    ClassSchedule getScheduleByIdSync(int id);

    @Query("SELECT * FROM class_schedules WHERE id = :id LIMIT 1")
    LiveData<ClassSchedule> getScheduleById(int id);

    @Query("SELECT * FROM class_schedules WHERE day_of_week = :dayOfWeek ORDER BY start_time ASC")
    LiveData<List<ClassSchedule>> getClassesForDay(String dayOfWeek);

    @Query("SELECT * FROM class_schedules WHERE day_of_week = :dayOfWeek ORDER BY start_time ASC")
    List<ClassSchedule> getClassesForDaySync(String dayOfWeek);

    @Query("SELECT * FROM class_schedules ORDER BY CASE day_of_week " +
            "WHEN 'Monday' THEN 1 " +
            "WHEN 'Tuesday' THEN 2 " +
            "WHEN 'Wednesday' THEN 3 " +
            "WHEN 'Thursday' THEN 4 " +
            "WHEN 'Friday' THEN 5 ELSE 6 END, start_time ASC")
    LiveData<List<ClassSchedule>> getAllSchedules();

    @Query("SELECT * FROM class_schedules ORDER BY CASE day_of_week " +
            "WHEN 'Monday' THEN 1 " +
            "WHEN 'Tuesday' THEN 2 " +
            "WHEN 'Wednesday' THEN 3 " +
            "WHEN 'Thursday' THEN 4 " +
            "WHEN 'Friday' THEN 5 ELSE 6 END, start_time ASC")
    List<ClassSchedule> getAllSchedulesSync();

    @Query("SELECT DISTINCT subject_name FROM class_schedules ORDER BY subject_name ASC")
    LiveData<List<String>> getDistinctSubjects();

    @Query("SELECT * FROM class_schedules WHERE subject_name LIKE '%' || :query || '%' " +
            "OR room_number LIKE '%' || :query || '%' " +
            "OR day_of_week LIKE '%' || :query || '%' " +
            "ORDER BY start_time ASC")
    LiveData<List<ClassSchedule>> searchSchedules(String query);

    @Query("DELETE FROM class_schedules WHERE id = :id")
    void deleteById(int id);
}

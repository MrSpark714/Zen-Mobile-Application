package com.example.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.model.ActivityLog;

import java.util.List;

@Dao
public interface ActivityLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ActivityLog log);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ActivityLog> logs);

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    LiveData<List<ActivityLog>> getAllLogs();

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    List<ActivityLog> getAllLogsSync();

    @Query("SELECT COUNT(*) FROM activity_logs")
    int getLogCountSync();

    @Query("DELETE FROM activity_logs")
    void deleteAllLogs();
}

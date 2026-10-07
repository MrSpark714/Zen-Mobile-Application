package com.example.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.io.Serializable;

/**
 * Task Entity representing a to-do item in the ZEN productivity application.
 *
 * Supports task timing and duration:
 * - Start time (epoch millis)
 * - End / Due time (epoch millis, e.g., "in next 3 Hours")
 * - Duration in minutes
 * - Alarm notification flag
 */
@Entity(tableName = "tasks")
public class Task implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "description")
    private String description;

    @ColumnInfo(name = "is_completed")
    private boolean isCompleted;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    @ColumnInfo(name = "start_time", defaultValue = "0")
    private long startTime;

    @ColumnInfo(name = "end_time", defaultValue = "0")
    private long endTime;

    @ColumnInfo(name = "duration_minutes", defaultValue = "0")
    private int durationMinutes;

    /**
     * Primary Room constructor.
     */
    public Task(int id, String description, boolean isCompleted, long timestamp, long startTime, long endTime, int durationMinutes) {
        this.id = id;
        this.description = description;
        this.isCompleted = isCompleted;
        this.timestamp = timestamp;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMinutes = durationMinutes;
    }

    /**
     * Legacy helper constructor.
     */
    @Ignore
    public Task(int id, String description, boolean isCompleted, long timestamp) {
        this(id, description, isCompleted, timestamp, 0, 0, 0);
    }

    @Ignore
    public Task(String description, boolean isCompleted, long timestamp) {
        this(0, description, isCompleted, timestamp, 0, 0, 0);
    }

    @Ignore
    public Task(String description, boolean isCompleted, long timestamp, long startTime, long endTime, int durationMinutes) {
        this(0, description, isCompleted, timestamp, startTime, endTime, durationMinutes);
    }

    // --- Getters and Setters ---

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public boolean hasTimeWindow() {
        return endTime > 0;
    }
}

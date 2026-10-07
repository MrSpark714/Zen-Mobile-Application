package com.example.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

/**
 * Entity representing an individual system activity log event.
 * Displays timestamp and message on a single, clean line.
 */
@Entity(tableName = "activity_logs")
public class ActivityLog implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    @ColumnInfo(name = "message")
    private String message;

    public ActivityLog() {
    }

    @androidx.room.Ignore
    public ActivityLog(long timestamp, String message) {
        this.timestamp = timestamp;
        this.message = message;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

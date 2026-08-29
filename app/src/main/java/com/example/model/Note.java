package com.example.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.io.Serializable;

/**
 * Note Entity representing a note item stored in the SQLite database via Room.
 *
 * Supports rich note properties:
 * - Rich text content (HTML / styled formatting)
 * - Important / Priority tag
 * - Color theme / accent highlight
 * - Explicit target date
 */
@Entity(tableName = "notes")
public class Note implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "title")
    private String title;

    @ColumnInfo(name = "content")
    private String content;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    @ColumnInfo(name = "is_important", defaultValue = "0")
    private boolean isImportant;

    @ColumnInfo(name = "color_hex", defaultValue = "")
    private String colorHex;

    @ColumnInfo(name = "target_date", defaultValue = "")
    private String targetDate;

    /**
     * Primary Room constructor.
     */
    public Note(int id, String title, String content, long timestamp, boolean isImportant, String colorHex, String targetDate) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.timestamp = timestamp;
        this.isImportant = isImportant;
        this.colorHex = colorHex != null ? colorHex : "";
        this.targetDate = targetDate != null ? targetDate : "";
    }

    /**
     * Legacy/Helper constructor.
     */
    @Ignore
    public Note(int id, String title, String content, long timestamp) {
        this(id, title, content, timestamp, false, "", "");
    }

    /**
     * Convenience constructor for new notes before DB insertion.
     */
    @Ignore
    public Note(String title, String content, long timestamp) {
        this(0, title, content, timestamp, false, "", "");
    }

    @Ignore
    public Note(String title, String content, long timestamp, boolean isImportant, String colorHex, String targetDate) {
        this(0, title, content, timestamp, isImportant, colorHex, targetDate);
    }

    // --- Getters and Setters ---

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isImportant() {
        return isImportant;
    }

    public void setImportant(boolean important) {
        isImportant = important;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public String getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(String targetDate) {
        this.targetDate = targetDate;
    }
}

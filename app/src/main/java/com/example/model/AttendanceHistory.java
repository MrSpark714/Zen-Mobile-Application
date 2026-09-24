package com.example.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.Serializable;

/**
 * AttendanceHistory Entity ("Big Box" Ledger).
 * Operates as an independent, decoupled snapshot record without foreign key constraints.
 *
 * Status values:
 * - "Present"
 * - "Absent"
 * - "Holiday"
 */
@Entity(
        tableName = "attendance_history",
        indices = {
                @Index(value = {"subject_name"}),
                @Index(value = {"date"}),
                @Index(value = {"subject_name", "date"})
        }
)
public class AttendanceHistory implements Serializable {

    public static final String STATUS_PRESENT = "Present";
    public static final String STATUS_ABSENT = "Absent";
    public static final String STATUS_HOLIDAY = "Holiday";

    @PrimaryKey(autoGenerate = true)
    private int id;

    @com.google.gson.annotations.SerializedName(value = "subject_name", alternate = {"subjectName"})
    @ColumnInfo(name = "subject_name")
    private String subjectName;

    @com.google.gson.annotations.SerializedName(value = "credit_hours", alternate = {"creditHours"})
    @ColumnInfo(name = "credit_hours")
    private int creditHours;

    @com.google.gson.annotations.SerializedName(value = "class_type", alternate = {"classType"})
    @ColumnInfo(name = "class_type")
    private String classType; // "Theory" or "Lab"

    @com.google.gson.annotations.SerializedName("date")
    @ColumnInfo(name = "date")
    private long date; // Epoch timestamp (normalized to midnight 00:00:00)

    @com.google.gson.annotations.SerializedName("status")
    @ColumnInfo(name = "status")
    private String status; // "Present", "Absent", "Holiday"

    // Optional legacy field for migration lookup: in legacy backups, attendance records had scheduleId
    @com.google.gson.annotations.SerializedName(value = "schedule_id", alternate = {"scheduleId"})
    @Ignore
    private Integer legacyScheduleId;

    public AttendanceHistory() {
    }

    @Ignore
    public AttendanceHistory(String subjectName, int creditHours, String classType, long date, String status) {
        this.subjectName = subjectName;
        this.creditHours = creditHours;
        this.classType = classType;
        this.date = date;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public int getCreditHours() {
        return creditHours;
    }

    public void setCreditHours(int creditHours) {
        this.creditHours = creditHours;
    }

    public String getClassType() {
        return classType != null ? classType : "Theory";
    }

    public void setClassType(String classType) {
        this.classType = classType;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getLegacyScheduleId() {
        return legacyScheduleId;
    }

    public void setLegacyScheduleId(Integer legacyScheduleId) {
        this.legacyScheduleId = legacyScheduleId;
    }
}

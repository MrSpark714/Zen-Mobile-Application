package com.example.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.Serializable;

/**
 * AttendanceRecord Entity representing attendance for a specific class date.
 *
 * Status values:
 * - "Present"
 * - "Absent"
 * - "Holiday"
 *
 * Includes Foreign Key to ClassSchedule with CASCADE deletion.
 */
@Entity(
        tableName = "attendance_records",
        foreignKeys = @ForeignKey(
                entity = ClassSchedule.class,
                parentColumns = "id",
                childColumns = "schedule_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index(value = {"schedule_id", "date"}, unique = true),
                @Index(value = {"subject_name"}),
                @Index(value = {"date"})
        }
)
public class AttendanceRecord implements Serializable {

    public static final String STATUS_PRESENT = "Present";
    public static final String STATUS_ABSENT = "Absent";
    public static final String STATUS_HOLIDAY = "Holiday";

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "schedule_id")
    private int scheduleId;

    @ColumnInfo(name = "subject_name")
    private String subjectName;

    @ColumnInfo(name = "date")
    private long date; // Normalized timestamp of day (start of day at 00:00:00)

    @ColumnInfo(name = "status")
    private String status; // "Present", "Absent", "Holiday"

    public AttendanceRecord() {
    }

    @Ignore
    public AttendanceRecord(int scheduleId, String subjectName, long date, String status) {
        this.scheduleId = scheduleId;
        this.subjectName = subjectName;
        this.date = date;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(int scheduleId) {
        this.scheduleId = scheduleId;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
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
}

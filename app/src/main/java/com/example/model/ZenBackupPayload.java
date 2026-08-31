package com.example.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * POJO representing a portable JSON backup payload for the ZEN application.
 *
 * Supports flexible modular backups (Full, Notes & Attendance, Attendance Only, or Cohort Timetable).
 */
public class ZenBackupPayload implements Serializable {

    public static final String TYPE_FULL = "FULL";
    public static final String TYPE_NOTES_ATTENDANCE = "NOTES_ATTENDANCE";
    public static final String TYPE_ATTENDANCE_ONLY = "ATTENDANCE_ONLY";
    public static final String TYPE_TIMETABLE_ONLY = "TIMETABLE_ONLY";

    @SerializedName("app_name")
    private String appName = "ZEN";

    @SerializedName("version")
    private String version = "1.0";

    @SerializedName("backup_type")
    private String backupType = TYPE_FULL;

    @SerializedName("export_timestamp")
    private long exportTimestamp = System.currentTimeMillis();

    @SerializedName("notes")
    private List<Note> notes = new ArrayList<>();

    @SerializedName("tasks")
    private List<Task> tasks = new ArrayList<>();

    @SerializedName("schedules")
    private List<ClassSchedule> schedules = new ArrayList<>();

    @SerializedName("attendance_records")
    private List<AttendanceRecord> attendanceRecords = new ArrayList<>();

    public ZenBackupPayload() {
        this.exportTimestamp = System.currentTimeMillis();
    }

    public ZenBackupPayload(String backupType, List<Note> notes, List<Task> tasks,
                            List<ClassSchedule> schedules, List<AttendanceRecord> attendanceRecords) {
        this.appName = "ZEN";
        this.version = "1.0";
        this.backupType = backupType;
        this.exportTimestamp = System.currentTimeMillis();
        this.notes = notes != null ? notes : new ArrayList<>();
        this.tasks = tasks != null ? tasks : new ArrayList<>();
        this.schedules = schedules != null ? schedules : new ArrayList<>();
        this.attendanceRecords = attendanceRecords != null ? attendanceRecords : new ArrayList<>();
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getBackupType() {
        return backupType;
    }

    public void setBackupType(String backupType) {
        this.backupType = backupType;
    }

    public long getExportTimestamp() {
        return exportTimestamp;
    }

    public void setExportTimestamp(long exportTimestamp) {
        this.exportTimestamp = exportTimestamp;
    }

    public List<Note> getNotes() {
        return notes != null ? notes : new ArrayList<>();
    }

    public void setNotes(List<Note> notes) {
        this.notes = notes;
    }

    public List<Task> getTasks() {
        return tasks != null ? tasks : new ArrayList<>();
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }

    public List<ClassSchedule> getSchedules() {
        return schedules != null ? schedules : new ArrayList<>();
    }

    public void setSchedules(List<ClassSchedule> schedules) {
        this.schedules = schedules;
    }

    public List<AttendanceRecord> getAttendanceRecords() {
        return attendanceRecords != null ? attendanceRecords : new ArrayList<>();
    }

    public void setAttendanceRecords(List<AttendanceRecord> attendanceRecords) {
        this.attendanceRecords = attendanceRecords;
    }
}

package com.example.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Locale;
import com.example.util.NotificationHelper;

/**
 * ClassSchedule Entity representing a scheduled recurring class in the ZEN Attendance Tracker.
 *
 * Restricted Days: Monday, Tuesday, Wednesday, Thursday, Friday.
 * Class Types: "Theory", "Lab".
 */
@Entity(tableName = "class_schedules")
public class ClassSchedule implements Serializable {

    @PrimaryKey(autoGenerate = true)
    @com.google.gson.annotations.SerializedName(value = "id", alternate = {"scheduleId", "schedule_id"})
    private int id;

    @com.google.gson.annotations.SerializedName(value = "day_of_week", alternate = {"dayOfWeek", "day"})
    @ColumnInfo(name = "day_of_week")
    private String dayOfWeek; // "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"

    @com.google.gson.annotations.SerializedName(value = "subject_name", alternate = {"subjectName", "subject"})
    @ColumnInfo(name = "subject_name")
    private String subjectName;

    @com.google.gson.annotations.SerializedName(value = "start_time", alternate = {"startTime"})
    @ColumnInfo(name = "start_time")
    private String startTime; // e.g. "09:00 AM" or "14:30"

    @com.google.gson.annotations.SerializedName(value = "end_time", alternate = {"endTime"})
    @ColumnInfo(name = "end_time")
    private String endTime; // e.g. "10:30 AM" or "16:00"

    @com.google.gson.annotations.SerializedName(value = "credit_hours", alternate = {"creditHours", "credits"})
    @ColumnInfo(name = "credit_hours")
    private int creditHours;

    @com.google.gson.annotations.SerializedName(value = "class_type", alternate = {"classType", "type"})
    @ColumnInfo(name = "class_type")
    private String classType; // "Theory" or "Lab"

    @com.google.gson.annotations.SerializedName(value = "room_number", alternate = {"roomNumber", "room"})
    @ColumnInfo(name = "room_number")
    private String roomNumber;

    public ClassSchedule() {
    }

    @Ignore
    public ClassSchedule(String dayOfWeek, String subjectName, String startTime,
                         String endTime, int creditHours, String classType, String roomNumber) {
        this.dayOfWeek = dayOfWeek;
        this.subjectName = subjectName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.creditHours = creditHours;
        this.classType = classType;
        this.roomNumber = roomNumber;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
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

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    /**
     * Parses a time string (e.g. "09:00 AM", "1:30 PM", "14:15") into total minutes from midnight (0-1439).
     */
    public static int parseTimeToMinutes(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return Integer.MAX_VALUE;
        }
        int[] hm = NotificationHelper.parseHourAndMinute(timeStr);
        return hm[0] * 60 + hm[1];
    }

    public int getStartTimeInMinutes() {
        return parseTimeToMinutes(this.startTime);
    }

    /**
     * Chronological Comparator: Sorts ClassSchedule objects purely by start time.
     * Ties are broken alphabetically by subject name.
     */
    public static final Comparator<ClassSchedule> CHRONOLOGICAL_COMPARATOR = (c1, c2) -> {
        if (c1 == null && c2 == null) return 0;
        if (c1 == null) return 1;
        if (c2 == null) return -1;
        int t1 = c1.getStartTimeInMinutes();
        int t2 = c2.getStartTimeInMinutes();
        if (t1 != t2) {
            return Integer.compare(t1, t2);
        }
        String s1 = c1.getSubjectName() != null ? c1.getSubjectName() : "";
        String s2 = c2.getSubjectName() != null ? c2.getSubjectName() : "";
        return s1.compareToIgnoreCase(s2);
    };

    /**
     * Day of week sorting index (Monday=1 .. Friday=5).
     */
    public static int getDayOfWeekIndex(String day) {
        if (day == null) return 99;
        switch (day.trim().toLowerCase(Locale.ROOT)) {
            case "monday":
            case "mon":
                return 1;
            case "tuesday":
            case "tue":
                return 2;
            case "wednesday":
            case "wed":
                return 3;
            case "thursday":
            case "thu":
                return 4;
            case "friday":
            case "fri":
                return 5;
            default:
                return 6;
        }
    }

    /**
     * Timetable comparator: First sorts by weekday (Mon-Fri), then chronologically by start time within each day.
     */
    public static final Comparator<ClassSchedule> TIMETABLE_COMPARATOR = (c1, c2) -> {
        if (c1 == null && c2 == null) return 0;
        if (c1 == null) return 1;
        if (c2 == null) return -1;
        int dayOrder1 = getDayOfWeekIndex(c1.getDayOfWeek());
        int dayOrder2 = getDayOfWeekIndex(c2.getDayOfWeek());
        if (dayOrder1 != dayOrder2) {
            return Integer.compare(dayOrder1, dayOrder2);
        }
        return CHRONOLOGICAL_COMPARATOR.compare(c1, c2);
    };
}

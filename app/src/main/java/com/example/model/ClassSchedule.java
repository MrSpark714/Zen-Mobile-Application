package com.example.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.io.Serializable;

/**
 * ClassSchedule Entity representing a scheduled recurring class in the ZEN Attendance Tracker.
 *
 * Restricted Days: Monday, Tuesday, Wednesday, Thursday, Friday.
 * Class Types: "Theory", "Lab".
 */
@Entity(tableName = "class_schedules")
public class ClassSchedule implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "day_of_week")
    private String dayOfWeek; // "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"

    @ColumnInfo(name = "subject_name")
    private String subjectName;

    @ColumnInfo(name = "start_time")
    private String startTime; // e.g. "09:00 AM" or "14:30"

    @ColumnInfo(name = "end_time")
    private String endTime; // e.g. "10:30 AM" or "16:00"

    @ColumnInfo(name = "credit_hours")
    private int creditHours;

    @ColumnInfo(name = "class_type")
    private String classType; // "Theory" or "Lab"

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
}

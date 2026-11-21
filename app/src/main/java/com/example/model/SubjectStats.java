package com.example.model;

import androidx.room.ColumnInfo;

import java.io.Serializable;

/**
 * POJO data class for Subject Attendance Statistics calculated from Room SQL query.
 *
 * Math Rule: Attendance % = (Total Present / (Total Present + Total Absent)) * 100.
 * Holiday status is ignored in the denominator and numerator math.
 */
public class SubjectStats implements Serializable {

    @ColumnInfo(name = "subject_name")
    private String subjectName;

    @ColumnInfo(name = "total_classes")
    private int totalClassesMarked;

    @ColumnInfo(name = "present_count")
    private int presentCount;

    @ColumnInfo(name = "absent_count")
    private int absentCount;

    @ColumnInfo(name = "holiday_count")
    private int holidayCount;

    @ColumnInfo(name = "attendance_percentage")
    private double attendancePercentage;

    @ColumnInfo(name = "class_type")
    private String classType = "Theory";

    @ColumnInfo(name = "credit_hours")
    private int creditHours = 1;

    public SubjectStats() {
    }

    public String getSubjectName() {
        return subjectName != null ? subjectName : "Unknown Subject";
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public int getTotalClassesMarked() {
        return totalClassesMarked;
    }

    public void setTotalClassesMarked(int totalClassesMarked) {
        this.totalClassesMarked = totalClassesMarked;
    }

    public int getPresentCount() {
        return presentCount;
    }

    public void setPresentCount(int presentCount) {
        this.presentCount = presentCount;
    }

    public int getAbsentCount() {
        return absentCount;
    }

    public void setAbsentCount(int absentCount) {
        this.absentCount = absentCount;
    }

    public int getHolidayCount() {
        return holidayCount;
    }

    public void setHolidayCount(int holidayCount) {
        this.holidayCount = holidayCount;
    }

    public double getAttendancePercentage() {
        int validTotal = presentCount + absentCount;
        if (validTotal <= 0) {
            return 0.0;
        }
        return (presentCount * 100.0) / validTotal;
    }

    public void setAttendancePercentage(double attendancePercentage) {
        this.attendancePercentage = attendancePercentage;
    }

    public int getEffectiveTotal() {
        return presentCount + absentCount;
    }

    public String getClassType() {
        return classType != null && !classType.trim().isEmpty() ? classType.trim() : "Theory";
    }

    public void setClassType(String classType) {
        this.classType = classType;
    }

    public int getCreditHours() {
        return creditHours > 0 ? creditHours : 1;
    }

    public void setCreditHours(int creditHours) {
        this.creditHours = creditHours;
    }
}

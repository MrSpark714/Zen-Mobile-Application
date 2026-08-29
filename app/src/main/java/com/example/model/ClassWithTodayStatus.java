package com.example.model;

import java.io.Serializable;

/**
 * Composite model binding a ClassSchedule with its marked status for today.
 */
public class ClassWithTodayStatus implements Serializable {

    private ClassSchedule schedule;
    private AttendanceRecord todayRecord;

    public ClassWithTodayStatus(ClassSchedule schedule, AttendanceRecord todayRecord) {
        this.schedule = schedule;
        this.todayRecord = todayRecord;
    }

    public ClassSchedule getSchedule() {
        return schedule;
    }

    public void setSchedule(ClassSchedule schedule) {
        this.schedule = schedule;
    }

    public AttendanceRecord getTodayRecord() {
        return todayRecord;
    }

    public void setTodayRecord(AttendanceRecord todayRecord) {
        this.todayRecord = todayRecord;
    }

    public String getCurrentStatus() {
        return todayRecord != null ? todayRecord.getStatus() : null;
    }
}

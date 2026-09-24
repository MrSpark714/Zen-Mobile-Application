package com.example.model;

import java.io.Serializable;

/**
 * Composite model binding a ClassSchedule with its marked status for today.
 */
public class ClassWithTodayStatus implements Serializable {

    private ClassSchedule schedule;
    private AttendanceHistory todayRecord;

    public ClassWithTodayStatus(ClassSchedule schedule, AttendanceHistory todayRecord) {
        this.schedule = schedule;
        this.todayRecord = todayRecord;
    }

    public ClassSchedule getSchedule() {
        return schedule;
    }

    public void setSchedule(ClassSchedule schedule) {
        this.schedule = schedule;
    }

    public AttendanceHistory getTodayRecord() {
        return todayRecord;
    }

    public void setTodayRecord(AttendanceHistory todayRecord) {
        this.todayRecord = todayRecord;
    }

    public String getCurrentStatus() {
        return todayRecord != null ? todayRecord.getStatus() : null;
    }
}

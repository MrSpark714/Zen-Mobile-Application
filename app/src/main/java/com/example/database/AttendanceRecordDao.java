package com.example.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.model.AttendanceRecord;
import com.example.model.SubjectStats;

import java.util.List;

/**
 * Data Access Object for AttendanceRecord operations and relational statistics.
 *
 * Weighted Attendance Formula Rule:
 * (Total Present Credits / (Total Present Credits + Total Absent Credits)) * 100.
 * Records with status 'Holiday' are excluded from calculations, but retained in database logs.
 */
@Dao
public interface AttendanceRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertOrUpdate(AttendanceRecord record);

    @Update
    void update(AttendanceRecord record);

    @Delete
    void delete(AttendanceRecord record);

    @Query("SELECT * FROM attendance_records WHERE schedule_id = :scheduleId AND date = :date LIMIT 1")
    LiveData<AttendanceRecord> getRecordByScheduleAndDate(int scheduleId, long date);

    @Query("SELECT * FROM attendance_records WHERE schedule_id = :scheduleId AND date = :date LIMIT 1")
    AttendanceRecord getRecordByScheduleAndDateSync(int scheduleId, long date);

    @Query("SELECT * FROM attendance_records WHERE date = :date")
    LiveData<List<AttendanceRecord>> getRecordsForDate(long date);

    @Query("SELECT * FROM attendance_records WHERE date = :date")
    List<AttendanceRecord> getRecordsForDateSync(long date);

    @Query("SELECT * FROM attendance_records WHERE subject_name = :subjectName ORDER BY date DESC")
    LiveData<List<AttendanceRecord>> getHistoryForSubject(String subjectName);

    @Query("SELECT * FROM attendance_records WHERE subject_name = :subjectName ORDER BY date DESC")
    List<AttendanceRecord> getHistoryForSubjectSync(String subjectName);

    /**
     * Relational Query calculating credit-hour weighted attendance percentage for a SPECIFIC Subject.
     * Performs a LEFT JOIN with class_schedules to weight each record by credit_hours.
     * Holiday records are excluded from the math denominator and numerator.
     */
    @Query("SELECT " +
            "a.subject_name AS subject_name, " +
            "COUNT(a.id) AS total_classes, " +
            "SUM(CASE WHEN a.status = 'Present' THEN COALESCE(c.credit_hours, 1) ELSE 0 END) AS present_count, " +
            "SUM(CASE WHEN a.status = 'Absent' THEN COALESCE(c.credit_hours, 1) ELSE 0 END) AS absent_count, " +
            "SUM(CASE WHEN a.status = 'Holiday' THEN COALESCE(c.credit_hours, 1) ELSE 0 END) AS holiday_count, " +
            "CASE " +
            "    WHEN (SUM(CASE WHEN a.status = 'Present' THEN COALESCE(c.credit_hours, 1) ELSE 0 END) + SUM(CASE WHEN a.status = 'Absent' THEN COALESCE(c.credit_hours, 1) ELSE 0 END)) = 0 THEN 0.0 " +
            "    ELSE (CAST(SUM(CASE WHEN a.status = 'Present' THEN COALESCE(c.credit_hours, 1) ELSE 0 END) AS REAL) * 100.0) / " +
            "         CAST((SUM(CASE WHEN a.status = 'Present' THEN COALESCE(c.credit_hours, 1) ELSE 0 END) + SUM(CASE WHEN a.status = 'Absent' THEN COALESCE(c.credit_hours, 1) ELSE 0 END)) AS REAL) " +
            "END AS attendance_percentage " +
            "FROM attendance_records a " +
            "LEFT JOIN class_schedules c ON a.schedule_id = c.id " +
            "WHERE a.subject_name = :subjectName " +
            "GROUP BY a.subject_name")
    LiveData<SubjectStats> getStatsForSubject(String subjectName);

    @Query("SELECT " +
            "a.subject_name AS subject_name, " +
            "COUNT(a.id) AS total_classes, " +
            "SUM(CASE WHEN a.status = 'Present' THEN COALESCE(c.credit_hours, 1) ELSE 0 END) AS present_count, " +
            "SUM(CASE WHEN a.status = 'Absent' THEN COALESCE(c.credit_hours, 1) ELSE 0 END) AS absent_count, " +
            "SUM(CASE WHEN a.status = 'Holiday' THEN COALESCE(c.credit_hours, 1) ELSE 0 END) AS holiday_count, " +
            "CASE " +
            "    WHEN (SUM(CASE WHEN a.status = 'Present' THEN COALESCE(c.credit_hours, 1) ELSE 0 END) + SUM(CASE WHEN a.status = 'Absent' THEN COALESCE(c.credit_hours, 1) ELSE 0 END)) = 0 THEN 0.0 " +
            "    ELSE (CAST(SUM(CASE WHEN a.status = 'Present' THEN COALESCE(c.credit_hours, 1) ELSE 0 END) AS REAL) * 100.0) / " +
            "         CAST((SUM(CASE WHEN a.status = 'Present' THEN COALESCE(c.credit_hours, 1) ELSE 0 END) + SUM(CASE WHEN a.status = 'Absent' THEN COALESCE(c.credit_hours, 1) ELSE 0 END)) AS REAL) " +
            "END AS attendance_percentage " +
            "FROM attendance_records a " +
            "LEFT JOIN class_schedules c ON a.schedule_id = c.id " +
            "WHERE a.subject_name = :subjectName " +
            "GROUP BY a.subject_name")
    SubjectStats getStatsForSubjectSync(String subjectName);

    /**
     * Relational Query calculating credit-hour weighted statistics & percentage for ALL distinct enrolled subjects.
     * Performs a LEFT JOIN on attendance_records and class_schedules.
     */
    @Query("SELECT " +
            "s.subject_name AS subject_name, " +
            "COUNT(a.id) AS total_classes, " +
            "SUM(CASE WHEN a.status = 'Present' THEN COALESCE(c.credit_hours, 1) ELSE 0 END) AS present_count, " +
            "SUM(CASE WHEN a.status = 'Absent' THEN COALESCE(c.credit_hours, 1) ELSE 0 END) AS absent_count, " +
            "SUM(CASE WHEN a.status = 'Holiday' THEN COALESCE(c.credit_hours, 1) ELSE 0 END) AS holiday_count, " +
            "CASE " +
            "    WHEN (SUM(CASE WHEN a.status = 'Present' THEN COALESCE(c.credit_hours, 1) ELSE 0 END) + SUM(CASE WHEN a.status = 'Absent' THEN COALESCE(c.credit_hours, 1) ELSE 0 END)) = 0 THEN 0.0 " +
            "    ELSE (CAST(SUM(CASE WHEN a.status = 'Present' THEN COALESCE(c.credit_hours, 1) ELSE 0 END) AS REAL) * 100.0) / " +
            "         CAST((SUM(CASE WHEN a.status = 'Present' THEN COALESCE(c.credit_hours, 1) ELSE 0 END) + SUM(CASE WHEN a.status = 'Absent' THEN COALESCE(c.credit_hours, 1) ELSE 0 END)) AS REAL) " +
            "END AS attendance_percentage " +
            "FROM (SELECT DISTINCT subject_name FROM class_schedules) s " +
            "LEFT JOIN attendance_records a ON s.subject_name = a.subject_name " +
            "LEFT JOIN class_schedules c ON a.schedule_id = c.id " +
            "GROUP BY s.subject_name " +
            "ORDER BY s.subject_name ASC")
    LiveData<List<SubjectStats>> getAllSubjectStats();

    @Query("DELETE FROM attendance_records WHERE schedule_id = :scheduleId AND date = :date")
    void deleteByScheduleAndDate(int scheduleId, long date);
}

package com.example.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.model.AttendanceHistory;
import com.example.model.SubjectStats;

import java.util.List;

/**
 * Data Access Object for AttendanceHistory operations and snapshot-based analytics.
 *
 * Formula:
 * (Total Present Credits / (Total Present Credits + Total Absent Credits)) * 100.0.
 * Records with status 'Holiday' are excluded from attendance percentage calculations,
 * but retained in logs and stats totals.
 */
@Dao
public interface AttendanceHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(AttendanceHistory record);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AttendanceHistory> records);

    @Update
    void update(AttendanceHistory record);

    @Delete
    void delete(AttendanceHistory record);

    @Query("DELETE FROM attendance_history WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM attendance_history WHERE subject_name = :subjectName")
    void deleteRecordsBySubject(String subjectName);

    @Query("DELETE FROM attendance_history")
    void deleteAllHistory();

    @Query("UPDATE attendance_history SET status = :status WHERE id = :id")
    void updateStatus(int id, String status);

    @Query("SELECT * FROM attendance_history ORDER BY date DESC")
    LiveData<List<AttendanceHistory>> getAllRecords();

    @Query("SELECT * FROM attendance_history ORDER BY date DESC")
    List<AttendanceHistory> getAllRecordsSync();

    @Query("SELECT * FROM attendance_history WHERE date = :date")
    LiveData<List<AttendanceHistory>> getRecordsForDate(long date);

    @Query("SELECT * FROM attendance_history WHERE date = :date")
    List<AttendanceHistory> getRecordsForDateSync(long date);

    @Query("SELECT * FROM attendance_history WHERE subject_name = :subjectName ORDER BY date DESC")
    LiveData<List<AttendanceHistory>> getHistoryForSubject(String subjectName);

    @Query("SELECT * FROM attendance_history WHERE subject_name = :subjectName ORDER BY date DESC")
    List<AttendanceHistory> getHistoryForSubjectSync(String subjectName);

    @Query("SELECT * FROM attendance_history WHERE subject_name = :subjectName AND date = :date LIMIT 1")
    AttendanceHistory getRecordBySubjectAndDateSync(String subjectName, long date);

    /**
     * Standalone Weighted Analytics Query for ALL enrolled subjects.
     * Calculates stats directly from attendance_history snapshot data.
     */
    @Query("SELECT " +
            "subject_name AS subject_name, " +
            "COUNT(id) AS total_classes, " +
            "SUM(CASE WHEN status = 'Present' THEN COALESCE(credit_hours, 1) ELSE 0 END) AS present_count, " +
            "SUM(CASE WHEN status = 'Absent' THEN COALESCE(credit_hours, 1) ELSE 0 END) AS absent_count, " +
            "SUM(CASE WHEN status = 'Holiday' THEN COALESCE(credit_hours, 1) ELSE 0 END) AS holiday_count, " +
            "CASE " +
            "    WHEN (SUM(CASE WHEN status = 'Present' THEN COALESCE(credit_hours, 1) ELSE 0 END) + " +
            "          SUM(CASE WHEN status = 'Absent' THEN COALESCE(credit_hours, 1) ELSE 0 END)) = 0 THEN 0.0 " +
            "    ELSE (CAST(SUM(CASE WHEN status = 'Present' THEN COALESCE(credit_hours, 1) ELSE 0 END) AS REAL) * 100.0) / " +
            "         CAST((SUM(CASE WHEN status = 'Present' THEN COALESCE(credit_hours, 1) ELSE 0 END) + " +
            "               SUM(CASE WHEN status = 'Absent' THEN COALESCE(credit_hours, 1) ELSE 0 END)) AS REAL) " +
            "END AS attendance_percentage " +
            "FROM attendance_history " +
            "GROUP BY subject_name " +
            "ORDER BY subject_name ASC")
    LiveData<List<SubjectStats>> getAllSubjectStats();

    @Query("SELECT " +
            "subject_name AS subject_name, " +
            "COUNT(id) AS total_classes, " +
            "SUM(CASE WHEN status = 'Present' THEN COALESCE(credit_hours, 1) ELSE 0 END) AS present_count, " +
            "SUM(CASE WHEN status = 'Absent' THEN COALESCE(credit_hours, 1) ELSE 0 END) AS absent_count, " +
            "SUM(CASE WHEN status = 'Holiday' THEN COALESCE(credit_hours, 1) ELSE 0 END) AS holiday_count, " +
            "CASE " +
            "    WHEN (SUM(CASE WHEN status = 'Present' THEN COALESCE(credit_hours, 1) ELSE 0 END) + " +
            "          SUM(CASE WHEN status = 'Absent' THEN COALESCE(credit_hours, 1) ELSE 0 END)) = 0 THEN 0.0 " +
            "    ELSE (CAST(SUM(CASE WHEN status = 'Present' THEN COALESCE(credit_hours, 1) ELSE 0 END) AS REAL) * 100.0) / " +
            "         CAST((SUM(CASE WHEN status = 'Present' THEN COALESCE(credit_hours, 1) ELSE 0 END) + " +
            "               SUM(CASE WHEN status = 'Absent' THEN COALESCE(credit_hours, 1) ELSE 0 END)) AS REAL) " +
            "END AS attendance_percentage " +
            "FROM attendance_history " +
            "GROUP BY subject_name " +
            "ORDER BY subject_name ASC")
    List<SubjectStats> getAllSubjectStatsSync();

    /**
     * Standalone Weighted Analytics Query for a SINGLE specific subject.
     */
    @Query("SELECT " +
            "subject_name AS subject_name, " +
            "COUNT(id) AS total_classes, " +
            "SUM(CASE WHEN status = 'Present' THEN COALESCE(credit_hours, 1) ELSE 0 END) AS present_count, " +
            "SUM(CASE WHEN status = 'Absent' THEN COALESCE(credit_hours, 1) ELSE 0 END) AS absent_count, " +
            "SUM(CASE WHEN status = 'Holiday' THEN COALESCE(credit_hours, 1) ELSE 0 END) AS holiday_count, " +
            "CASE " +
            "    WHEN (SUM(CASE WHEN status = 'Present' THEN COALESCE(credit_hours, 1) ELSE 0 END) + " +
            "          SUM(CASE WHEN status = 'Absent' THEN COALESCE(credit_hours, 1) ELSE 0 END)) = 0 THEN 0.0 " +
            "    ELSE (CAST(SUM(CASE WHEN status = 'Present' THEN COALESCE(credit_hours, 1) ELSE 0 END) AS REAL) * 100.0) / " +
            "         CAST((SUM(CASE WHEN status = 'Present' THEN COALESCE(credit_hours, 1) ELSE 0 END) + " +
            "               SUM(CASE WHEN status = 'Absent' THEN COALESCE(credit_hours, 1) ELSE 0 END)) AS REAL) " +
            "END AS attendance_percentage " +
            "FROM attendance_history " +
            "WHERE subject_name = :subjectName " +
            "GROUP BY subject_name")
    LiveData<SubjectStats> getStatsForSubject(String subjectName);

    @Query("SELECT " +
            "subject_name AS subject_name, " +
            "COUNT(id) AS total_classes, " +
            "SUM(CASE WHEN status = 'Present' THEN COALESCE(credit_hours, 1) ELSE 0 END) AS present_count, " +
            "SUM(CASE WHEN status = 'Absent' THEN COALESCE(credit_hours, 1) ELSE 0 END) AS absent_count, " +
            "SUM(CASE WHEN status = 'Holiday' THEN COALESCE(credit_hours, 1) ELSE 0 END) AS holiday_count, " +
            "CASE " +
            "    WHEN (SUM(CASE WHEN status = 'Present' THEN COALESCE(credit_hours, 1) ELSE 0 END) + " +
            "          SUM(CASE WHEN status = 'Absent' THEN COALESCE(credit_hours, 1) ELSE 0 END)) = 0 THEN 0.0 " +
            "    ELSE (CAST(SUM(CASE WHEN status = 'Present' THEN COALESCE(credit_hours, 1) ELSE 0 END) AS REAL) * 100.0) / " +
            "         CAST((SUM(CASE WHEN status = 'Present' THEN COALESCE(credit_hours, 1) ELSE 0 END) + " +
            "               SUM(CASE WHEN status = 'Absent' THEN COALESCE(credit_hours, 1) ELSE 0 END)) AS REAL) " +
            "END AS attendance_percentage " +
            "FROM attendance_history " +
            "WHERE subject_name = :subjectName " +
            "GROUP BY subject_name")
    SubjectStats getStatsForSubjectSync(String subjectName);
}

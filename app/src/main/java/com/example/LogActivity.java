package com.example;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adapter.AttendanceHistoryAdapter;
import com.example.database.AppDatabase;
import com.example.model.AttendanceRecord;
import com.example.model.SubjectStats;
import com.example.util.ThemeHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * LogActivity: Dedicated full-screen attendance log management screen.
 *
 * Implements:
 * - Full-page view of date-by-date attendance sessions with back navigation.
 * - Real-time Room database LiveData observation for session history and weighted statistics.
 * - History Edit via Long-Press: MaterialAlertDialog to update status between Present, Absent, and Holiday.
 */
public class LogActivity extends AppCompatActivity {

    public static final String EXTRA_SUBJECT_NAME = "extra_subject_name";

    private AppDatabase database;
    private AttendanceHistoryAdapter historyAdapter;

    private ImageView btnBack;
    private TextView tvHeaderTitle;
    private TextView tvHeaderSubtitle;
    private TextView tvLogBadgePercentage;
    private MaterialCardView cardStatsSummary;
    private TextView tvLogSubjectName;
    private TextView tvLogBreakdown;
    private TextView tvTotalRecordsCount;
    private RecyclerView rvLogHistory;
    private LinearLayout layoutEmptyLog;

    private String subjectName;
    private final SimpleDateFormat recordDateFormat = new SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        database = AppDatabase.getInstance(this);

        subjectName = getIntent().getStringExtra(EXTRA_SUBJECT_NAME);

        initViews();
        setupRecyclerView();
        observeData();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        tvHeaderTitle = findViewById(R.id.tv_header_title);
        tvHeaderSubtitle = findViewById(R.id.tv_header_subtitle);
        tvLogBadgePercentage = findViewById(R.id.tv_log_badge_percentage);
        cardStatsSummary = findViewById(R.id.card_stats_summary);
        tvLogSubjectName = findViewById(R.id.tv_log_subject_name);
        tvLogBreakdown = findViewById(R.id.tv_log_breakdown);
        tvTotalRecordsCount = findViewById(R.id.tv_total_records_count);
        rvLogHistory = findViewById(R.id.rv_log_history);
        layoutEmptyLog = findViewById(R.id.layout_empty_log);

        btnBack.setOnClickListener(v -> finish());

        if (subjectName != null && !subjectName.trim().isEmpty()) {
            tvHeaderTitle.setText(subjectName.toUpperCase(Locale.ROOT));
            tvLogSubjectName.setText(subjectName);
            tvHeaderSubtitle.setText("Tap status tag to edit past attendance");
        } else {
            tvHeaderTitle.setText("ALL ATTENDANCE LOGS");
            tvLogSubjectName.setText("All Enrolled Subjects");
            tvHeaderSubtitle.setText("Tap status tag to edit past attendance");
        }
    }

    private void setupRecyclerView() {
        historyAdapter = new AttendanceHistoryAdapter(this);
        rvLogHistory.setLayoutManager(new LinearLayoutManager(this));
        rvLogHistory.setAdapter(historyAdapter);

        // Add subtle divider between session rows
        DividerItemDecoration divider = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        rvLogHistory.addItemDecoration(divider);

        // Tap exclusively on the status tag (Present/Absent/Holiday) to change status
        historyAdapter.setOnStatusTagClickListener((record, position) -> {
            showEditStatusDialog(record);
        });
    }

    private void observeData() {
        if (subjectName != null && !subjectName.trim().isEmpty()) {
            // Observe session records for this subject
            database.attendanceRecordDao().getHistoryForSubject(subjectName).observe(this, this::updateRecordsList);

            // Observe live credit-hour weighted statistics for this subject
            database.attendanceRecordDao().getStatsForSubject(subjectName).observe(this, this::updateStatsUI);
        } else {
            // Observe all session records across all subjects
            database.attendanceRecordDao().getAllRecords().observe(this, this::updateRecordsList);
            cardStatsSummary.setVisibility(View.GONE);
            tvLogBadgePercentage.setVisibility(View.GONE);
        }
    }

    private void updateRecordsList(List<AttendanceRecord> records) {
        if (records == null || records.isEmpty()) {
            layoutEmptyLog.setVisibility(View.VISIBLE);
            rvLogHistory.setVisibility(View.GONE);
            tvTotalRecordsCount.setText("0 records");
            historyAdapter.setHistory(null);
        } else {
            layoutEmptyLog.setVisibility(View.GONE);
            rvLogHistory.setVisibility(View.VISIBLE);
            tvTotalRecordsCount.setText(records.size() + (records.size() == 1 ? " record" : " records"));
            historyAdapter.setHistory(records);
        }
    }

    private void updateStatsUI(SubjectStats stats) {
        if (stats == null) {
            tvLogBadgePercentage.setText("—");
            tvLogBreakdown.setText("No marked sessions recorded yet.");
            return;
        }

        int totalEffective = stats.getEffectiveTotal();
        if (totalEffective == 0) {
            tvLogBadgePercentage.setText("—");
            tvLogBreakdown.setText(stats.getHolidayCount() + " Holiday sessions recorded (no classes held)");
        } else {
            double percentage = stats.getAttendancePercentage();
            tvLogBadgePercentage.setText(String.format(Locale.getDefault(), "%.1f%%", percentage));
            tvLogBreakdown.setText(String.format(Locale.getDefault(),
                    "%d Present • %d Absent • %d Holiday (%d total)",
                    stats.getPresentCount(),
                    stats.getAbsentCount(),
                    stats.getHolidayCount(),
                    stats.getTotalClassesMarked()));
        }
    }

    /**
     * Shows a beautifully styled modal bottom sheet dialog allowing the user to select
     * between "Present", "Absent", and "Holiday" options with clear visual cards and radio check marks.
     */
    private void showEditStatusDialog(AttendanceRecord record) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_ZEN);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_attendance_status, null);
        dialog.setContentView(dialogView);

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_status_title);
        TextView tvSubtitle = dialogView.findViewById(R.id.tv_dialog_status_subtitle);
        View btnPresent = dialogView.findViewById(R.id.btn_option_present);
        View btnAbsent = dialogView.findViewById(R.id.btn_option_absent);
        View btnHoliday = dialogView.findViewById(R.id.btn_option_holiday);
        ImageView ivCheckPresent = dialogView.findViewById(R.id.iv_check_present);
        ImageView ivCheckAbsent = dialogView.findViewById(R.id.iv_check_absent);
        ImageView ivCheckHoliday = dialogView.findViewById(R.id.iv_check_holiday);
        View btnCancel = dialogView.findViewById(R.id.btn_cancel_status_dialog);

        String titleSubject = record.getSubjectName() != null ? record.getSubjectName() : "Class Session";
        String formattedDate = recordDateFormat.format(new Date(record.getDate()));

        tvTitle.setText(titleSubject.toUpperCase(Locale.ROOT));
        tvSubtitle.setText("Past session: " + formattedDate);

        // Highlight current status
        String currentStatus = record.getStatus();
        if (AttendanceRecord.STATUS_PRESENT.equalsIgnoreCase(currentStatus)) {
            ivCheckPresent.setVisibility(View.VISIBLE);
        } else if (AttendanceRecord.STATUS_ABSENT.equalsIgnoreCase(currentStatus)) {
            ivCheckAbsent.setVisibility(View.VISIBLE);
        } else if (AttendanceRecord.STATUS_HOLIDAY.equalsIgnoreCase(currentStatus)) {
            ivCheckHoliday.setVisibility(View.VISIBLE);
        }

        btnPresent.setOnClickListener(v -> {
            dialog.dismiss();
            if (!AttendanceRecord.STATUS_PRESENT.equalsIgnoreCase(record.getStatus())) {
                updateRecordInDatabase(record, AttendanceRecord.STATUS_PRESENT);
            }
        });

        btnAbsent.setOnClickListener(v -> {
            dialog.dismiss();
            if (!AttendanceRecord.STATUS_ABSENT.equalsIgnoreCase(record.getStatus())) {
                updateRecordInDatabase(record, AttendanceRecord.STATUS_ABSENT);
            }
        });

        btnHoliday.setOnClickListener(v -> {
            dialog.dismiss();
            if (!AttendanceRecord.STATUS_HOLIDAY.equalsIgnoreCase(record.getStatus())) {
                updateRecordInDatabase(record, AttendanceRecord.STATUS_HOLIDAY);
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Executes the Room Database update query to persist the new attendance status.
     */
    private void updateRecordInDatabase(AttendanceRecord record, String newStatus) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // Update the entity status in memory
            record.setStatus(newStatus);

            // Execute Room database update queries
            database.attendanceRecordDao().update(record);
            database.attendanceRecordDao().updateStatus(record.getId(), newStatus);

            // Notify UI on main thread
            runOnUiThread(() -> {
                Toast.makeText(LogActivity.this, "Status updated to " + newStatus, Toast.LENGTH_SHORT).show();
            });
        });
    }
}

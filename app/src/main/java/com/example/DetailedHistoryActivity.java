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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adapter.AttendanceHistoryAdapter;
import com.example.database.AppDatabase;
import com.example.model.ActivityLog;
import com.example.model.AttendanceHistory;
import com.example.model.SubjectStats;
import com.example.util.ThemeHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * DetailedHistoryActivity: Dedicated full-screen attendance log and subject ledger management.
 *
 * Implements:
 * - Full-page view of date-by-date attendance sessions with back navigation.
 * - Single-subject deletion via MaterialAlertDialogBuilder and Room DAO query.
 * - 7-day edit validation check on history modifications.
 */
public class DetailedHistoryActivity extends AppCompatActivity {

    public static final String EXTRA_SUBJECT_NAME = "extra_subject_name";

    private AppDatabase database;
    private AttendanceHistoryAdapter historyAdapter;

    private ImageView btnBack;
    private ImageView btnDeleteSubjectHistory;
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
        setContentView(R.layout.activity_detailed_history);

        database = AppDatabase.getInstance(this);

        subjectName = getIntent().getStringExtra(EXTRA_SUBJECT_NAME);
        if (subjectName == null || subjectName.trim().isEmpty()) {
            subjectName = "Attendance";
        }

        initViews();
        setupRecyclerView();
        setupListeners();
        observeData();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        btnDeleteSubjectHistory = findViewById(R.id.btn_delete_subject_history);
        tvHeaderTitle = findViewById(R.id.tv_header_title);
        tvHeaderSubtitle = findViewById(R.id.tv_header_subtitle);
        tvLogBadgePercentage = findViewById(R.id.tv_log_badge_percentage);
        cardStatsSummary = findViewById(R.id.card_stats_summary);
        tvLogSubjectName = findViewById(R.id.tv_log_subject_name);
        tvLogBreakdown = findViewById(R.id.tv_log_breakdown);
        tvTotalRecordsCount = findViewById(R.id.tv_total_records_count);
        rvLogHistory = findViewById(R.id.rv_log_history);
        layoutEmptyLog = findViewById(R.id.layout_empty_log);

        tvHeaderTitle.setText(subjectName.toUpperCase(Locale.ROOT));
        tvLogSubjectName.setText(subjectName);
    }

    private void setupRecyclerView() {
        rvLogHistory.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new AttendanceHistoryAdapter(this);
        rvLogHistory.setAdapter(historyAdapter);

        DividerItemDecoration divider = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        rvLogHistory.addItemDecoration(divider);

        // Tap exclusively on status badge to edit past attendance
        historyAdapter.setOnStatusTagClickListener((record, position) -> {
            showEditStatusDialog(record);
        });
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        // Subject deletion via MaterialAlertDialogBuilder
        btnDeleteSubjectHistory.setOnClickListener(v -> showDeleteSubjectConfirmationDialog());
    }

    private void showDeleteSubjectConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete History")
                .setMessage("Are you sure you want to delete all attendance records for \"" + subjectName + "\"? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        database.attendanceHistoryDao().deleteRecordsBySubject(subjectName);
                        // Record activity log
                        database.activityLogDao().insert(new ActivityLog(
                                System.currentTimeMillis(),
                                "Deleted all attendance records for " + subjectName
                        ));
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Attendance history deleted for " + subjectName, Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void observeData() {
        // Observe Date-Sorted Attendance Records for this subject
        database.attendanceHistoryDao().getHistoryForSubject(subjectName).observe(this, records -> {
            if (records == null || records.isEmpty()) {
                historyAdapter.setHistory(records);
                layoutEmptyLog.setVisibility(View.VISIBLE);
                rvLogHistory.setVisibility(View.GONE);
                tvTotalRecordsCount.setText("0 Records");
            } else {
                layoutEmptyLog.setVisibility(View.GONE);
                rvLogHistory.setVisibility(View.VISIBLE);
                historyAdapter.setHistory(records);
                tvTotalRecordsCount.setText(records.size() + (records.size() == 1 ? " Record" : " Records"));
            }
        });

        // Observe Subject Stats (Exact weighted % calculated via Room SQLite)
        database.attendanceHistoryDao().getStatsForSubject(subjectName).observe(this, this::updateStatsUI);
    }

    private void updateStatsUI(SubjectStats stats) {
        if (stats == null) {
            tvLogBadgePercentage.setText("—");
            tvLogBreakdown.setText("0 Present • 0 Absent • 0 Holiday");
            return;
        }

        double percentage = stats.getAttendancePercentage();
        int totalMarked = stats.getPresentCount() + stats.getAbsentCount();

        if (totalMarked == 0) {
            tvLogBadgePercentage.setText("—");
            tvLogBadgePercentage.setTextColor(ContextCompat.getColor(this, R.color.zen_accent));
        } else {
            tvLogBadgePercentage.setText(String.format(Locale.getDefault(), "%.1f%%", percentage));
            int color = percentage >= 75.0 ?
                    ContextCompat.getColor(this, R.color.status_present) :
                    (percentage >= 60.0 ?
                            ContextCompat.getColor(this, R.color.status_holiday) :
                            ContextCompat.getColor(this, R.color.status_absent));
            tvLogBadgePercentage.setTextColor(color);
        }

        String breakdown = String.format(Locale.getDefault(), "%d Present • %d Absent • %d Holiday",
                stats.getPresentCount(), stats.getAbsentCount(), stats.getHolidayCount());
        tvLogBreakdown.setText(breakdown);
    }

    /**
     * Show Modal BottomSheet Dialog to edit past attendance status.
     * Enforces strict 7-day validation.
     */
    private void showEditStatusDialog(AttendanceHistory record) {
        if (record == null) return;

        // 7-Day Edit Validation
        long diffMillis = System.currentTimeMillis() - record.getDate();
        long sevenDaysMillis = 7L * 24 * 60 * 60 * 1000;
        if (diffMillis > sevenDaysMillis) {
            Toast.makeText(this, "Edits are only permitted for the past 7 days.", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_ZEN);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_attendance_status, null);
        dialog.setContentView(dialogView);

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_status_title);
        TextView tvSubtitle = dialogView.findViewById(R.id.tv_dialog_status_subtitle);
        View optionPresent = dialogView.findViewById(R.id.btn_option_present);
        View optionAbsent = dialogView.findViewById(R.id.btn_option_absent);
        View optionHoliday = dialogView.findViewById(R.id.btn_option_holiday);

        if (tvTitle != null) tvTitle.setText(record.getSubjectName());
        if (tvSubtitle != null) tvSubtitle.setText(recordDateFormat.format(new Date(record.getDate())));

        if (optionPresent != null) {
            optionPresent.setOnClickListener(v -> {
                updateRecordStatus(record, AttendanceHistory.STATUS_PRESENT);
                dialog.dismiss();
            });
        }

        if (optionAbsent != null) {
            optionAbsent.setOnClickListener(v -> {
                updateRecordStatus(record, AttendanceHistory.STATUS_ABSENT);
                dialog.dismiss();
            });
        }

        if (optionHoliday != null) {
            optionHoliday.setOnClickListener(v -> {
                updateRecordStatus(record, AttendanceHistory.STATUS_HOLIDAY);
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private void updateRecordStatus(AttendanceHistory record, String newStatus) {
        if (record == null || newStatus == null || newStatus.equalsIgnoreCase(record.getStatus())) {
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            database.attendanceHistoryDao().updateStatus(record.getId(), newStatus);
            database.activityLogDao().insert(new ActivityLog(
                    System.currentTimeMillis(),
                    "Updated " + record.getSubjectName() + " to " + newStatus
            ));
            runOnUiThread(() -> {
                Toast.makeText(DetailedHistoryActivity.this, "Status updated to " + newStatus, Toast.LENGTH_SHORT).show();
            });
        });
    }
}

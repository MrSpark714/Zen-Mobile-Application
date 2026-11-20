package com.example;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.database.AppDatabase;
import com.example.model.AttendanceHistory;
import com.example.model.ClassSchedule;
import com.example.model.Note;
import com.example.model.Task;
import com.example.model.ZenBackupPayload;
import com.example.util.BackupHelper;
import com.example.util.ThemeHelper;
import com.example.util.UpdateManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * BackupActivity manages the Settings & Storage vault experience:
 * local SQLite health metrics, modular offline JSON export/restore,
 * cohort timetable package sharing, haptic/update preferences,
 * and PRAGMA database integrity verification.
 */
public class BackupActivity extends AppCompatActivity {

    private AppDatabase database;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearProgressIndicator progressIndicator;

    // Local Storage Health UI
    private TextView tvHealthStatusPill;
    private TextView tvStorageSizeValue;
    private TextView tvStorageSizeUnit;
    private TextView tvItemsIndexed;
    private View segClasses;
    private View segLogs;
    private View segTasks;
    private View segNotes;
    private TextView tvCountClasses;
    private TextView tvCountAttendance;
    private TextView tvCountTasks;
    private TextView tvCountNotes;

    // Backup & Snapshot Scope UI
    private TextView tvLastBackupTime;
    private View cardScopeFull;
    private View cardScopeAttendance;
    private View cardScopeNotes;
    private View radioScopeFull;
    private View radioScopeAttendance;
    private View radioScopeNotes;
    private TextView tvScopeAttendanceSize;
    private TextView tvScopeNotesSize;
    private MaterialButton btnExportBackup;
    private MaterialButton btnImportBackup;

    // Preferences & Security UI
    private MaterialSwitch switchAutoUpdate;
    private MaterialSwitch switchHapticFeedback;

    // Peer Collaboration UI
    private MaterialButton btnShareTimetable;

    // System & Community UI
    private View btnDbIntegrityCheck;
    private TextView tvIntegrityStatus;
    private MaterialButton btnOpenGitHubBackup;
    private MaterialButton btnCheckUpdatesManual;

    private String selectedScope = ZenBackupPayload.TYPE_FULL;
    private int countClasses = 0;
    private int countLogs = 0;
    private int countTasks = 0;
    private int countNotes = 0;

    // In-memory cache of serialized backup payload awaiting SAF write callback
    private String pendingExportJson = null;

    // Storage Access Framework: Save document (Export)
    private final ActivityResultLauncher<String> createDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), this::handleCreateDocumentResult);

    // Storage Access Framework: Open document (Import)
    private final ActivityResultLauncher<String[]> openDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleOpenDocumentResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);

        database = AppDatabase.getInstance(this);
        initViews();
        setupListeners();
        selectBackupScope(ZenBackupPayload.TYPE_FULL, null);
        updateLastBackupLabel();
        loadLocalDatabaseStats();
        runDatabaseIntegrityCheck(false);

        applyDynamicTheme(ThemeHelper.getAccentColor(this));
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> {
            ThemeHelper.performHaptic(v);
            finish();
        });

        progressIndicator = findViewById(R.id.progress_indicator);

        tvHealthStatusPill = findViewById(R.id.tv_health_status_pill);
        tvStorageSizeValue = findViewById(R.id.tv_storage_size_value);
        tvStorageSizeUnit = findViewById(R.id.tv_storage_size_unit);
        tvItemsIndexed = findViewById(R.id.tv_items_indexed);
        segClasses = findViewById(R.id.seg_classes);
        segLogs = findViewById(R.id.seg_logs);
        segTasks = findViewById(R.id.seg_tasks);
        segNotes = findViewById(R.id.seg_notes);
        tvCountClasses = findViewById(R.id.tv_count_classes);
        tvCountAttendance = findViewById(R.id.tv_count_attendance);
        tvCountTasks = findViewById(R.id.tv_count_tasks);
        tvCountNotes = findViewById(R.id.tv_count_notes);

        tvLastBackupTime = findViewById(R.id.tv_last_backup_time);
        cardScopeFull = findViewById(R.id.card_scope_full);
        cardScopeAttendance = findViewById(R.id.card_scope_attendance);
        cardScopeNotes = findViewById(R.id.card_scope_notes);
        radioScopeFull = findViewById(R.id.radio_scope_full);
        radioScopeAttendance = findViewById(R.id.radio_scope_attendance);
        radioScopeNotes = findViewById(R.id.radio_scope_notes);
        tvScopeAttendanceSize = findViewById(R.id.tv_scope_attendance_size);
        tvScopeNotesSize = findViewById(R.id.tv_scope_notes_size);

        btnExportBackup = findViewById(R.id.btn_export_backup);
        btnImportBackup = findViewById(R.id.btn_import_backup);
        btnShareTimetable = findViewById(R.id.btn_share_timetable);

        switchAutoUpdate = findViewById(R.id.switch_auto_update);
        switchHapticFeedback = findViewById(R.id.switch_haptic_feedback);

        btnDbIntegrityCheck = findViewById(R.id.btn_db_integrity_check);
        tvIntegrityStatus = findViewById(R.id.tv_integrity_status);
        btnOpenGitHubBackup = findViewById(R.id.btn_open_github_backup);
        btnCheckUpdatesManual = findViewById(R.id.btn_check_updates_manual);
    }

    private void applyDynamicTheme(int accentColor) {
        if (progressIndicator != null) {
            progressIndicator.setIndicatorColor(accentColor);
        }
        if (btnExportBackup != null) {
            ThemeHelper.applyAccentToPrimaryButton(btnExportBackup, accentColor);
        }
        if (btnShareTimetable != null) {
            ThemeHelper.applyAccentToPrimaryButton(btnShareTimetable, accentColor);
        }
        if (switchAutoUpdate != null) {
            ThemeHelper.applyAccentToSwitch(switchAutoUpdate, accentColor);
        }
        if (switchHapticFeedback != null) {
            ThemeHelper.applyAccentToSwitch(switchHapticFeedback, accentColor);
        }
    }

    private void setupListeners() {
        if (cardScopeFull != null) {
            cardScopeFull.setOnClickListener(v -> selectBackupScope(ZenBackupPayload.TYPE_FULL, v));
        }
        if (cardScopeAttendance != null) {
            cardScopeAttendance.setOnClickListener(v -> selectBackupScope(ZenBackupPayload.TYPE_ATTENDANCE_ONLY, v));
        }
        if (cardScopeNotes != null) {
            cardScopeNotes.setOnClickListener(v -> selectBackupScope(ZenBackupPayload.TYPE_NOTES_ATTENDANCE, v));
        }

        if (btnExportBackup != null) {
            btnExportBackup.setOnClickListener(v -> {
                ThemeHelper.performHaptic(v);
                startExportFlow();
            });
        }
        if (btnImportBackup != null) {
            btnImportBackup.setOnClickListener(v -> {
                ThemeHelper.performHaptic(v);
                startImportFlow();
            });
        }
        if (btnShareTimetable != null) {
            btnShareTimetable.setOnClickListener(v -> {
                ThemeHelper.performHaptic(v);
                startCohortShareFlow();
            });
        }

        if (switchAutoUpdate != null) {
            switchAutoUpdate.setChecked(UpdateManager.isAutoUpdateEnabled(this));
            switchAutoUpdate.setOnCheckedChangeListener((buttonView, isChecked) -> {
                UpdateManager.setAutoUpdateEnabled(this, isChecked);
                ThemeHelper.performHaptic(buttonView);
                Toast.makeText(this,
                        isChecked ? "Auto-update checks enabled" : "Auto-update checks disabled",
                        Toast.LENGTH_SHORT).show();
            });
        }

        if (switchHapticFeedback != null) {
            switchHapticFeedback.setChecked(ThemeHelper.isHapticEnabled(this));
            switchHapticFeedback.setOnCheckedChangeListener((buttonView, isChecked) -> {
                ThemeHelper.setHapticEnabled(this, isChecked);
                if (isChecked) {
                    ThemeHelper.performHaptic(buttonView);
                }
            });
        }

        if (btnDbIntegrityCheck != null) {
            btnDbIntegrityCheck.setOnClickListener(v -> {
                ThemeHelper.performHaptic(v);
                runDatabaseIntegrityCheck(true);
            });
        }

        if (btnCheckUpdatesManual != null) {
            btnCheckUpdatesManual.setOnClickListener(v -> {
                ThemeHelper.performHaptic(v);
                UpdateManager.checkForUpdates(this, UpdateManager.DEFAULT_UPDATE_URL, true);
            });
        }

        View statusInfo = findViewById(R.id.tv_status_info);
        if (statusInfo != null) {
            statusInfo.setOnClickListener(v -> handleEasterEggTap());
        }

        TextView tvVersion = findViewById(R.id.tv_settings_version_backup);
        if (tvVersion != null) {
            tvVersion.setText(String.format(Locale.getDefault(), "V%s", BuildConfig.VERSION_NAME));
        }

        if (btnOpenGitHubBackup != null) {
            btnOpenGitHubBackup.setOnClickListener(v -> {
                ThemeHelper.performHaptic(v);
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/MrSpark714/Zen-Mobile-Application"));
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "Could not open browser: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        View cardActivityLog = findViewById(R.id.card_settings_activity_log);
        if (cardActivityLog != null) {
            cardActivityLog.setOnClickListener(v -> {
                ThemeHelper.performHaptic(v);
                Intent logIntent = new Intent(this, LogActivity.class);
                startActivity(logIntent);
            });
        }
    }

    private void selectBackupScope(String scope, View hapticSource) {
        selectedScope = scope;
        if (hapticSource != null) {
            ThemeHelper.performHaptic(hapticSource);
        }

        boolean isFull = ZenBackupPayload.TYPE_FULL.equals(scope);
        boolean isAttendance = ZenBackupPayload.TYPE_ATTENDANCE_ONLY.equals(scope);
        boolean isNotes = ZenBackupPayload.TYPE_NOTES_ATTENDANCE.equals(scope);

        if (cardScopeFull != null) {
            cardScopeFull.setBackgroundResource(isFull ? R.drawable.bg_card_scope_selected : R.drawable.bg_card_scope_unselected);
        }
        if (radioScopeFull != null) {
            radioScopeFull.setBackgroundResource(isFull ? R.drawable.bg_radio_selected : R.drawable.bg_radio_unselected);
        }

        if (cardScopeAttendance != null) {
            cardScopeAttendance.setBackgroundResource(isAttendance ? R.drawable.bg_card_scope_selected : R.drawable.bg_card_scope_unselected);
        }
        if (radioScopeAttendance != null) {
            radioScopeAttendance.setBackgroundResource(isAttendance ? R.drawable.bg_radio_selected : R.drawable.bg_radio_unselected);
        }

        if (cardScopeNotes != null) {
            cardScopeNotes.setBackgroundResource(isNotes ? R.drawable.bg_card_scope_selected : R.drawable.bg_card_scope_unselected);
        }
        if (radioScopeNotes != null) {
            radioScopeNotes.setBackgroundResource(isNotes ? R.drawable.bg_radio_selected : R.drawable.bg_radio_unselected);
        }
    }

    // Easter Egg Watermark: 7-tap detection within 3 seconds
    private int easterEggTapCount = 0;
    private long easterEggFirstTapTime = 0;
    private static final int REQUIRED_EASTER_EGG_TAPS = 7;
    private static final long EASTER_EGG_TIME_WINDOW_MS = 3000L;

    private void handleEasterEggTap() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - easterEggFirstTapTime > EASTER_EGG_TIME_WINDOW_MS) {
            easterEggTapCount = 1;
            easterEggFirstTapTime = currentTime;
        } else {
            easterEggTapCount++;
            if (easterEggTapCount >= REQUIRED_EASTER_EGG_TAPS) {
                easterEggTapCount = 0;
                easterEggFirstTapTime = 0;
                showEasterEggWatermark();
            }
        }
    }

    private void showEasterEggWatermark() {
        String watermark = "ZEN v" + BuildConfig.VERSION_NAME + " — Architected and Built by Muhammad Sohaib";
        Toast.makeText(this, watermark, Toast.LENGTH_LONG).show();

        new MaterialAlertDialogBuilder(this, R.style.Theme_ZEN)
                .setTitle("ZEN Architect")
                .setMessage(watermark)
                .setIcon(R.drawable.ic_star_filled)
                .setPositiveButton("Close", null)
                .show();
    }

    /**
     * Loads live counts of classes, attendance logs, tasks, notes, and local SQLite size.
     */
    private void loadLocalDatabaseStats() {
        database.noteDao().getAllNotes().observe(this, notes -> {
            countNotes = notes != null ? notes.size() : 0;
            if (tvCountNotes != null) {
                tvCountNotes.setText(String.valueOf(countNotes));
            }
            updateStorageSummary();
        });

        database.taskDao().getAllTasks().observe(this, tasks -> {
            countTasks = tasks != null ? tasks.size() : 0;
            if (tvCountTasks != null) {
                tvCountTasks.setText(String.valueOf(countTasks));
            }
            updateStorageSummary();
        });

        database.classScheduleDao().getAllSchedules().observe(this, schedules -> {
            countClasses = schedules != null ? schedules.size() : 0;
            if (tvCountClasses != null) {
                tvCountClasses.setText(String.valueOf(countClasses));
            }
            updateStorageSummary();
        });

        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<AttendanceHistory> records = database.attendanceHistoryDao().getAllRecordsSync();
            int count = records != null ? records.size() : 0;
            long dbBytes = computeDatabaseBytes();
            mainHandler.post(() -> {
                countLogs = count;
                if (tvCountAttendance != null) {
                    tvCountAttendance.setText(String.valueOf(countLogs));
                }
                applyDatabaseSizeText(dbBytes);
                updateStorageSummary();
            });
        });
    }

    private long computeDatabaseBytes() {
        long total = 0L;
        File dbFile = getDatabasePath("zen_database.db");
        if (dbFile != null) {
            if (dbFile.exists()) total += dbFile.length();
            File wal = new File(dbFile.getPath() + "-wal");
            if (wal.exists()) total += wal.length();
            File shm = new File(dbFile.getPath() + "-shm");
            if (shm.exists()) total += shm.length();
        }
        return total;
    }

    private void applyDatabaseSizeText(long totalBytes) {
        if (tvStorageSizeValue == null || tvStorageSizeUnit == null) return;
        if (totalBytes >= 1024L * 1024L) {
            double mb = totalBytes / (1024.0 * 1024.0);
            tvStorageSizeValue.setText(String.format(Locale.getDefault(), "%.1f", mb));
            tvStorageSizeUnit.setText("MB USED");
        } else {
            double kb = Math.max(1.0, totalBytes / 1024.0);
            tvStorageSizeValue.setText(String.format(Locale.getDefault(), "%.1f", kb));
            tvStorageSizeUnit.setText("KB USED");
        }
    }

    private void updateStorageSummary() {
        int totalItems = countClasses + countLogs + countTasks + countNotes;
        if (tvItemsIndexed != null) {
            tvItemsIndexed.setText(String.format(Locale.getDefault(), "%d Items Indexed", totalItems));
        }

        if (totalItems <= 0) {
            setSegmentWeight(segClasses, 1f);
            setSegmentWeight(segLogs, 1f);
            setSegmentWeight(segTasks, 1f);
            setSegmentWeight(segNotes, 1f);
        } else {
            setSegmentWeight(segClasses, Math.max(0.25f, (float) countClasses));
            setSegmentWeight(segLogs, Math.max(0.25f, (float) countLogs));
            setSegmentWeight(segTasks, Math.max(0.25f, (float) countTasks));
            setSegmentWeight(segNotes, Math.max(0.25f, (float) countNotes));
        }

        if (tvScopeAttendanceSize != null) {
            long estKb = Math.max(1L, Math.round((countLogs * 180L + 512L) / 1024.0));
            tvScopeAttendanceSize.setText(String.format(Locale.getDefault(), "~%d KB", estKb));
        }
        if (tvScopeNotesSize != null) {
            long estKb = Math.max(1L, Math.round((countNotes * 640L + countClasses * 280L + 768L) / 1024.0));
            tvScopeNotesSize.setText(String.format(Locale.getDefault(), "~%d KB", estKb));
        }
    }

    private void setSegmentWeight(View segment, float weight) {
        if (segment == null) return;
        if (segment.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) segment.getLayoutParams();
            params.weight = weight;
            segment.setLayoutParams(params);
        }
    }

    private void updateLastBackupLabel() {
        if (tvLastBackupTime == null) return;
        long millis = ThemeHelper.getLastBackupMillis(this);
        if (millis <= 0L) {
            tvLastBackupTime.setText("Last backup: Never");
            return;
        }
        if (DateUtils.isToday(millis)) {
            SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());
            tvLastBackupTime.setText("Last backup: Today, " + timeFmt.format(new Date(millis)));
        } else {
            SimpleDateFormat dateFmt = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
            tvLastBackupTime.setText("Last backup: " + dateFmt.format(new Date(millis)));
        }
    }

    private void runDatabaseIntegrityCheck(boolean userTriggered) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            boolean passed = false;
            try {
                SupportSQLiteDatabase db = database.getOpenHelper().getReadableDatabase();
                try (Cursor cursor = db.query("PRAGMA quick_check")) {
                    if (cursor.moveToFirst()) {
                        String result = cursor.getString(0);
                        passed = "ok".equalsIgnoreCase(result != null ? result.trim() : "");
                    }
                }
            } catch (Exception ignored) {
                passed = false;
            }
            final boolean isOk = passed;
            mainHandler.post(() -> {
                if (tvIntegrityStatus != null) {
                    tvIntegrityStatus.setText(isOk ? "Pass" : "Issue");
                    tvIntegrityStatus.setTextColor(getColor(isOk ? R.color.zen_accent : R.color.zen_danger));
                }
                if (tvHealthStatusPill != null) {
                    tvHealthStatusPill.setText(isOk ? "Status: Optimal" : "Status: Attention");
                    tvHealthStatusPill.setTextColor(getColor(isOk ? R.color.zen_accent : R.color.zen_danger));
                }
                if (userTriggered) {
                    if (isOk) {
                        showSuccessSnackbar("Database integrity verified: PRAGMA quick_check OK");
                    } else {
                        Toast.makeText(this, "Database integrity check reported an issue", Toast.LENGTH_LONG).show();
                    }
                }
            });
        });
    }

    // ==========================================
    // 1. EXPORT BACKUP FLOW (SAF CreateDocument)
    // ==========================================

    private void startExportFlow() {
        final String backupType = selectedScope;
        showProgress(true);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<Note> notes = new ArrayList<>();
                List<Task> tasks = new ArrayList<>();
                List<ClassSchedule> schedules = new ArrayList<>();
                List<AttendanceHistory> records = database.attendanceHistoryDao().getAllRecordsSync();

                if (backupType.equals(ZenBackupPayload.TYPE_FULL)) {
                    notes = database.noteDao().getAllNotesSync();
                    tasks = database.taskDao().getAllTasksSync();
                    schedules = database.classScheduleDao().getAllSchedulesSync();
                } else if (backupType.equals(ZenBackupPayload.TYPE_ATTENDANCE_ONLY)) {
                    notes = new ArrayList<>();
                    tasks = new ArrayList<>();
                    schedules = new ArrayList<>();
                } else if (backupType.equals(ZenBackupPayload.TYPE_NOTES_ATTENDANCE)) {
                    notes = database.noteDao().getAllNotesSync();
                    schedules = database.classScheduleDao().getAllSchedulesSync();
                }

                ZenBackupPayload payload = new ZenBackupPayload(backupType, notes, tasks, schedules, records);
                pendingExportJson = BackupHelper.serialize(payload);

                String defaultFileName = BackupHelper.generateBackupFileName(backupType);

                mainHandler.post(() -> {
                    showProgress(false);
                    createDocumentLauncher.launch(defaultFileName);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showProgress(false);
                    Toast.makeText(this, "Failed to prepare export: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void handleCreateDocumentResult(Uri uri) {
        if (uri == null || pendingExportJson == null) {
            return;
        }

        final String jsonToWrite = pendingExportJson;
        pendingExportJson = null;
        showProgress(true);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                    if (outputStream == null) {
                        throw new IllegalStateException("Unable to open output stream for selected file");
                    }
                    BackupHelper.writeStringToStream(outputStream, jsonToWrite);
                }

                ThemeHelper.recordLastBackupNow(this);

                mainHandler.post(() -> {
                    showProgress(false);
                    updateLastBackupLabel();
                    showSuccessSnackbar("Vault snapshot successfully exported!");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showProgress(false);
                    Toast.makeText(this, "Failed to write backup file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ==========================================
    // 2. IMPORT BACKUP FLOW (SAF OpenDocument)
    // ==========================================

    private void startImportFlow() {
        openDocumentLauncher.launch(new String[]{"application/json", "application/octet-stream", "text/plain", "*/*"});
    }

    private void handleOpenDocumentResult(Uri uri) {
        if (uri == null) {
            return;
        }

        showProgress(true);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String json;
                try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                    if (inputStream == null) {
                        throw new IllegalStateException("Unable to open input stream for selected file");
                    }
                    json = BackupHelper.readStringFromStream(inputStream);
                }

                ZenBackupPayload payload = BackupHelper.deserialize(json);

                mainHandler.post(() -> {
                    showProgress(false);
                    showImportConfirmationDialog(payload);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showProgress(false);
                    Toast.makeText(this, "Invalid backup file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showImportConfirmationDialog(ZenBackupPayload payload) {
        int noteCount = payload.getNotes().size();
        int taskCount = payload.getTasks().size();
        int scheduleCount = payload.getSchedules().size();
        int recordCount = payload.getAttendanceRecords().size();

        String message = String.format(
                Locale.getDefault(),
                "Found in backup package:\n\n• %d Notes\n• %d Tasks\n• %d Class Schedules\n• %d Attendance Logs\n\nMerge these items into your local database?",
                noteCount, taskCount, scheduleCount, recordCount
        );

        new MaterialAlertDialogBuilder(this, R.style.Theme_ZEN)
                .setTitle("Confirm Restore")
                .setMessage(message)
                .setPositiveButton("Restore & Merge", (dialog, which) -> executeDatabaseImport(payload))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeDatabaseImport(ZenBackupPayload payload) {
        showProgress(true);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                if (!payload.getNotes().isEmpty()) {
                    database.noteDao().insertAll(payload.getNotes());
                }
                if (!payload.getTasks().isEmpty()) {
                    database.taskDao().insertAll(payload.getTasks());
                }
                if (!payload.getSchedules().isEmpty()) {
                    database.classScheduleDao().insertAll(payload.getSchedules());
                }
                if (!payload.getAttendanceRecords().isEmpty()) {
                    database.attendanceHistoryDao().insertAll(payload.getAttendanceRecords());
                }

                long dbBytes = computeDatabaseBytes();
                mainHandler.post(() -> {
                    showProgress(false);
                    applyDatabaseSizeText(dbBytes);
                    loadLocalDatabaseStats();
                    runDatabaseIntegrityCheck(false);
                    showSuccessSnackbar("Restore completed successfully!");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showProgress(false);
                    Toast.makeText(this, "Error inserting backup data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ==========================================
    // 3. COHORT SHARING (Share Class Timetable)
    // ==========================================

    private void startCohortShareFlow() {
        showProgress(true);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<ClassSchedule> schedules = database.classScheduleDao().getAllSchedulesSync();
                if (schedules == null || schedules.isEmpty()) {
                    mainHandler.post(() -> {
                        showProgress(false);
                        Toast.makeText(this, "No class schedules found to share. Please add classes first!", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                String json = BackupHelper.serializeSchedulesForCohort(schedules);

                File cacheDir = new File(getCacheDir(), "shared");
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs();
                }
                File shareFile = new File(cacheDir, "zen_class_timetable.json");
                try (FileOutputStream fos = new FileOutputStream(shareFile)) {
                    BackupHelper.writeStringToStream(fos, json);
                }

                Uri contentUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        shareFile
                );

                mainHandler.post(() -> {
                    showProgress(false);
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("application/json");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, "ZEN Class Timetable");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, "Here is our weekly class schedule from ZEN. Open this JSON file in ZEN to import the timetable!");
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    startActivity(Intent.createChooser(shareIntent, "Share Timetable via"));
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showProgress(false);
                    Toast.makeText(this, "Failed to share timetable: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ==========================================
    // 4. INCOMING INTENT INTERCEPTION (.json/.zen)
    // ==========================================

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;

        String actionMode = intent.getStringExtra("ACTION_MODE");
        if (actionMode != null) {
            intent.removeExtra("ACTION_MODE");
            mainHandler.postDelayed(() -> {
                if ("EXPORT_FULL".equals(actionMode)) {
                    selectBackupScope(ZenBackupPayload.TYPE_FULL, null);
                    startExportFlow();
                } else if ("IMPORT".equals(actionMode)) {
                    startImportFlow();
                } else if ("SHARE_TIMETABLE".equals(actionMode)) {
                    startCohortShareFlow();
                }
            }, 300);
            return;
        }

        Uri targetUri = null;
        String action = intent.getAction();

        if (Intent.ACTION_VIEW.equals(action)) {
            targetUri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            targetUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }

        if (targetUri != null) {
            handleOpenDocumentResult(targetUri);
        }
    }

    private void showProgress(boolean show) {
        if (progressIndicator != null) {
            progressIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showSuccessSnackbar(String message) {
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
                    .setBackgroundTint(getColor(R.color.zen_surface))
                    .setTextColor(getColor(R.color.zen_accent))
                    .show();
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }
}

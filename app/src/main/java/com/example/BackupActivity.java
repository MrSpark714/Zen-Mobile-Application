package com.example;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.database.AppDatabase;
import com.example.model.AttendanceRecord;
import com.example.model.ClassSchedule;
import com.example.model.Note;
import com.example.model.Task;
import com.example.model.ZenBackupPayload;
import com.example.util.BackupHelper;
import com.example.util.ThemeHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * BackupActivity manages offline JSON export, restore, cohort timetable sharing,
 * and dynamic accent theme customization in one unified Settings & Data screen.
 */
public class BackupActivity extends AppCompatActivity {

    private AppDatabase database;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearProgressIndicator progressIndicator;
    private TextView tvCountNotes;
    private TextView tvCountTasks;
    private TextView tvCountClasses;
    private TextView tvCountAttendance;
    private TextView tvDbStatsHeader;
    private TextView tvThemeAppliedHint;
    private RadioGroup rgBackupOptions;
    private RadioButton rbFullBackup;
    private RadioButton rbNotesAttendance;
    private RadioButton rbAttendanceOnly;
    private MaterialButton btnExportBackup;
    private MaterialButton btnImportBackup;
    private MaterialButton btnShareTimetable;
    private MaterialCardView cardCohortSharing;
    private ImageView ivCohortIcon;

    // Theme selector components
    private FrameLayout frameMint, frameTeal, frameCyan, frameSage, frameLime;
    private View ringMint, ringTeal, ringCyan, ringSage, ringLime;
    private ImageView checkMint, checkTeal, checkCyan, checkSage, checkLime;

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
        setupThemeSelector();
        setupListeners();
        loadLocalDatabaseStats();

        // Apply saved accent theme
        applyDynamicTheme(ThemeHelper.getAccentColor(this));

        // Handle incoming shared file (from WhatsApp, Email, File Manager, etc.)
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        progressIndicator = findViewById(R.id.progress_indicator);
        tvCountNotes = findViewById(R.id.tv_count_notes);
        tvCountTasks = findViewById(R.id.tv_count_tasks);
        tvCountClasses = findViewById(R.id.tv_count_classes);
        tvCountAttendance = findViewById(R.id.tv_count_attendance);
        tvDbStatsHeader = findViewById(R.id.tv_db_stats_header);
        tvThemeAppliedHint = findViewById(R.id.tv_theme_applied_hint);
        rgBackupOptions = findViewById(R.id.rg_backup_options);
        rbFullBackup = findViewById(R.id.rb_full_backup);
        rbNotesAttendance = findViewById(R.id.rb_notes_attendance);
        rbAttendanceOnly = findViewById(R.id.rb_attendance_only);
        btnExportBackup = findViewById(R.id.btn_export_backup);
        btnImportBackup = findViewById(R.id.btn_import_backup);
        btnShareTimetable = findViewById(R.id.btn_share_timetable);
        cardCohortSharing = findViewById(R.id.card_cohort_sharing);
        ivCohortIcon = findViewById(R.id.iv_cohort_icon);

        frameMint = findViewById(R.id.frame_color_mint);
        frameTeal = findViewById(R.id.frame_color_teal);
        frameCyan = findViewById(R.id.frame_color_cyan);
        frameSage = findViewById(R.id.frame_color_sage);
        frameLime = findViewById(R.id.frame_color_lime);

        ringMint = findViewById(R.id.ring_color_mint);
        ringTeal = findViewById(R.id.ring_color_teal);
        ringCyan = findViewById(R.id.ring_color_cyan);
        ringSage = findViewById(R.id.ring_color_sage);
        ringLime = findViewById(R.id.ring_color_lime);

        checkMint = findViewById(R.id.check_color_mint);
        checkTeal = findViewById(R.id.check_color_teal);
        checkCyan = findViewById(R.id.check_color_cyan);
        checkSage = findViewById(R.id.check_color_sage);
        checkLime = findViewById(R.id.check_color_lime);
    }

    private void setupThemeSelector() {
        updateThemeSelectionIndicators(ThemeHelper.getAccentColorHex(this));

        if (frameMint != null) {
            frameMint.setOnClickListener(v -> selectThemeColor(ThemeHelper.COLOR_MINT_DEFAULT));
        }
        if (frameTeal != null) {
            frameTeal.setOnClickListener(v -> selectThemeColor(ThemeHelper.COLOR_NEON_TEAL));
        }
        if (frameCyan != null) {
            frameCyan.setOnClickListener(v -> selectThemeColor(ThemeHelper.COLOR_CYAN_BLUE));
        }
        if (frameSage != null) {
            frameSage.setOnClickListener(v -> selectThemeColor(ThemeHelper.COLOR_SAGE_GREEN));
        }
        if (frameLime != null) {
            frameLime.setOnClickListener(v -> selectThemeColor(ThemeHelper.COLOR_ELECTRIC_LIME));
        }
    }

    private void selectThemeColor(String hexColor) {
        ThemeHelper.setAccentColor(this, hexColor);
        int newColor = ThemeHelper.getAccentColor(this);
        updateThemeSelectionIndicators(hexColor);
        applyDynamicTheme(newColor);
        Toast.makeText(this, "Theme accent updated", Toast.LENGTH_SHORT).show();
    }

    private void updateThemeSelectionIndicators(String selectedHex) {
        String hex = (selectedHex != null) ? selectedHex.toLowerCase(Locale.ROOT) : ThemeHelper.COLOR_MINT_DEFAULT;
        boolean isMint = hex.equalsIgnoreCase(ThemeHelper.COLOR_MINT_DEFAULT);
        boolean isTeal = hex.equalsIgnoreCase(ThemeHelper.COLOR_NEON_TEAL);
        boolean isCyan = hex.equalsIgnoreCase(ThemeHelper.COLOR_CYAN_BLUE);
        boolean isSage = hex.equalsIgnoreCase(ThemeHelper.COLOR_SAGE_GREEN);
        boolean isLime = hex.equalsIgnoreCase(ThemeHelper.COLOR_ELECTRIC_LIME);

        if (ringMint != null) ringMint.setVisibility(isMint ? View.VISIBLE : View.GONE);
        if (checkMint != null) checkMint.setVisibility(isMint ? View.VISIBLE : View.GONE);

        if (ringTeal != null) ringTeal.setVisibility(isTeal ? View.VISIBLE : View.GONE);
        if (checkTeal != null) checkTeal.setVisibility(isTeal ? View.VISIBLE : View.GONE);

        if (ringCyan != null) ringCyan.setVisibility(isCyan ? View.VISIBLE : View.GONE);
        if (checkCyan != null) checkCyan.setVisibility(isCyan ? View.VISIBLE : View.GONE);

        if (ringSage != null) ringSage.setVisibility(isSage ? View.VISIBLE : View.GONE);
        if (checkSage != null) checkSage.setVisibility(isSage ? View.VISIBLE : View.GONE);

        if (ringLime != null) ringLime.setVisibility(isLime ? View.VISIBLE : View.GONE);
        if (checkLime != null) checkLime.setVisibility(isLime ? View.VISIBLE : View.GONE);
    }

    /**
     * Applies dynamic accent theme across the Backup & Cohort sharing screen components.
     */
    private void applyDynamicTheme(int accentColor) {
        if (progressIndicator != null) {
            progressIndicator.setIndicatorColor(accentColor);
        }
        if (tvDbStatsHeader != null) {
            tvDbStatsHeader.setTextColor(accentColor);
        }
        if (rbFullBackup != null) {
            ThemeHelper.applyAccentToRadioButton(rbFullBackup, accentColor);
        }
        if (rbNotesAttendance != null) {
            ThemeHelper.applyAccentToRadioButton(rbNotesAttendance, accentColor);
        }
        if (rbAttendanceOnly != null) {
            ThemeHelper.applyAccentToRadioButton(rbAttendanceOnly, accentColor);
        }
        if (btnExportBackup != null) {
            ThemeHelper.applyAccentToPrimaryButton(btnExportBackup, accentColor);
        }
        if (btnShareTimetable != null) {
            ThemeHelper.applyAccentToPrimaryButton(btnShareTimetable, accentColor);
        }
        if (cardCohortSharing != null) {
            cardCohortSharing.setStrokeColor(accentColor);
            cardCohortSharing.setCardBackgroundColor(ThemeHelper.getAccentContainerColor(accentColor));
        }
        if (ivCohortIcon != null) {
            ivCohortIcon.setColorFilter(accentColor);
        }
    }

    private void setupListeners() {
        btnExportBackup.setOnClickListener(v -> startExportFlow());
        btnImportBackup.setOnClickListener(v -> startImportFlow());
        btnShareTimetable.setOnClickListener(v -> startCohortShareFlow());

        View statusInfo = findViewById(R.id.tv_status_info);
        if (statusInfo != null) {
            statusInfo.setOnClickListener(v -> handleEasterEggTap());
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
        String watermark = "ZEN v0.9.2 — Architected and Built by Muhammad Sohaib";
        Toast.makeText(this, watermark, Toast.LENGTH_LONG).show();

        new MaterialAlertDialogBuilder(this, R.style.Theme_ZEN)
                .setTitle("ZEN Architect")
                .setMessage(watermark)
                .setIcon(R.drawable.ic_star_filled)
                .setPositiveButton("Close", null)
                .show();
    }

    /**
     * Loads live counts of notes, tasks, classes, and attendance logs for display.
     */
    private void loadLocalDatabaseStats() {
        database.noteDao().getAllNotes().observe(this, notes -> {
            if (notes != null) {
                tvCountNotes.setText(String.valueOf(notes.size()));
            }
        });

        database.taskDao().getAllTasks().observe(this, tasks -> {
            if (tasks != null) {
                tvCountTasks.setText(String.valueOf(tasks.size()));
            }
        });

        database.classScheduleDao().getAllSchedules().observe(this, schedules -> {
            if (schedules != null) {
                tvCountClasses.setText(String.valueOf(schedules.size()));
            }
        });

        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<AttendanceRecord> records = database.attendanceRecordDao().getAllRecordsSync();
            int count = records != null ? records.size() : 0;
            mainHandler.post(() -> tvCountAttendance.setText(String.valueOf(count)));
        });
    }

    // ==========================================
    // 1. EXPORT BACKUP FLOW (SAF CreateDocument)
    // ==========================================

    private void startExportFlow() {
        String backupType;
        if (rbAttendanceOnly.isChecked()) {
            backupType = ZenBackupPayload.TYPE_ATTENDANCE_ONLY;
        } else if (rbNotesAttendance.isChecked()) {
            backupType = ZenBackupPayload.TYPE_NOTES_ATTENDANCE;
        } else {
            backupType = ZenBackupPayload.TYPE_FULL;
        }

        showProgress(true);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<Note> notes = new ArrayList<>();
                List<Task> tasks = new ArrayList<>();
                List<ClassSchedule> schedules = database.classScheduleDao().getAllSchedulesSync();
                List<AttendanceRecord> records = database.attendanceRecordDao().getAllRecordsSync();

                if (backupType.equals(ZenBackupPayload.TYPE_FULL) || backupType.equals(ZenBackupPayload.TYPE_NOTES_ATTENDANCE)) {
                    notes = database.noteDao().getAllNotesSync();
                }

                if (backupType.equals(ZenBackupPayload.TYPE_FULL)) {
                    tasks = database.taskDao().getAllTasksSync();
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

                mainHandler.post(() -> {
                    showProgress(false);
                    showSuccessSnackbar("Backup file successfully exported!");
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
                    database.attendanceRecordDao().insertAll(payload.getAttendanceRecords());
                }

                mainHandler.post(() -> {
                    showProgress(false);
                    loadLocalDatabaseStats();
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

                // Write to cache directory for FileProvider sharing
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
                    if (rbFullBackup != null) rbFullBackup.setChecked(true);
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

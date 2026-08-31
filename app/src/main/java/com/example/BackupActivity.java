package com.example;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * BackupActivity manages offline JSON export, restore, and cohort timetable sharing
 * using Android's Storage Access Framework (SAF) and FileProvider.
 */
public class BackupActivity extends AppCompatActivity {

    private AppDatabase database;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearProgressIndicator progressIndicator;
    private TextView tvCountNotes;
    private TextView tvCountTasks;
    private TextView tvCountClasses;
    private TextView tvCountAttendance;
    private RadioGroup rgBackupOptions;
    private RadioButton rbFullBackup;
    private RadioButton rbNotesAttendance;
    private RadioButton rbAttendanceOnly;
    private MaterialButton btnExportBackup;
    private MaterialButton btnImportBackup;
    private MaterialButton btnShareTimetable;

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
        loadLocalDatabaseStats();

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
        rgBackupOptions = findViewById(R.id.rg_backup_options);
        rbFullBackup = findViewById(R.id.rb_full_backup);
        rbNotesAttendance = findViewById(R.id.rb_notes_attendance);
        rbAttendanceOnly = findViewById(R.id.rb_attendance_only);
        btnExportBackup = findViewById(R.id.btn_export_backup);
        btnImportBackup = findViewById(R.id.btn_import_backup);
        btnShareTimetable = findViewById(R.id.btn_share_timetable);
    }

    private void setupListeners() {
        btnExportBackup.setOnClickListener(v -> startExportFlow());
        btnImportBackup.setOnClickListener(v -> startImportFlow());
        btnShareTimetable.setOnClickListener(v -> startCohortShareFlow());
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

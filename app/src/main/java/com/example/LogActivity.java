package com.example;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adapter.ActivityLogAdapter;
import com.example.database.AppDatabase;
import com.example.model.ActivityLog;
import com.example.util.ThemeHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.List;

/**
 * LogActivity: Dedicated full-screen activity displaying the system activity logs.
 * Does not use dialogs for viewing; items are rendered on single clean lines
 * separated by MaterialDividerItemDecoration.
 */
public class LogActivity extends AppCompatActivity {

    public static final String EXTRA_SUBJECT_NAME = "extra_subject_name";

    private AppDatabase database;
    private ActivityLogAdapter adapter;

    private ImageView btnBack;
    private ImageView btnClearLogs;
    private TextView tvHeaderTitle;
    private TextView tvHeaderSubtitle;
    private RecyclerView rvActivityLogs;
    private LinearLayout layoutEmptyLogs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If extra_subject_name is passed by any legacy caller, forward seamlessly to DetailedHistoryActivity
        if (getIntent().hasExtra(EXTRA_SUBJECT_NAME)) {
            Intent forwardIntent = new Intent(this, DetailedHistoryActivity.class);
            forwardIntent.putExtras(getIntent());
            startActivity(forwardIntent);
            finish();
            return;
        }

        setContentView(R.layout.activity_log);

        database = AppDatabase.getInstance(this);

        initViews();
        setupRecyclerView();
        setupListeners();
        observeLogs();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        btnClearLogs = findViewById(R.id.btn_clear_logs);
        tvHeaderTitle = findViewById(R.id.tv_header_title);
        tvHeaderSubtitle = findViewById(R.id.tv_header_subtitle);
        rvActivityLogs = findViewById(R.id.rv_activity_logs);
        layoutEmptyLogs = findViewById(R.id.layout_empty_logs);
    }

    private void setupRecyclerView() {
        rvActivityLogs.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ActivityLogAdapter(this);
        rvActivityLogs.setAdapter(adapter);

        // MaterialDividerItemDecoration to separate items with thin lines
        MaterialDividerItemDecoration divider = new MaterialDividerItemDecoration(this, LinearLayoutManager.VERTICAL);
        divider.setDividerColor(ContextCompat.getColor(this, R.color.zen_border));
        divider.setDividerThickness((int) (1 * getResources().getDisplayMetrics().density));
        divider.setLastItemDecorated(false);
        rvActivityLogs.addItemDecoration(divider);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnClearLogs.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Clear Activity Logs")
                    .setMessage("Are you sure you want to clear all activity log entries?")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            database.activityLogDao().deleteAllLogs();
                            runOnUiThread(() -> {
                                Toast.makeText(this, "Activity logs cleared", Toast.LENGTH_SHORT).show();
                            });
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void observeLogs() {
        // Pre-seed an initial log if table is empty
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (database.activityLogDao().getLogCountSync() == 0) {
                database.activityLogDao().insert(new ActivityLog(
                        System.currentTimeMillis(),
                        "System initialized and ready"
                ));
            }
        });

        database.activityLogDao().getAllLogs().observe(this, logs -> {
            if (logs == null || logs.isEmpty()) {
                adapter.setLogs(logs);
                layoutEmptyLogs.setVisibility(View.VISIBLE);
                rvActivityLogs.setVisibility(View.GONE);
                btnClearLogs.setVisibility(View.GONE);
            } else {
                layoutEmptyLogs.setVisibility(View.GONE);
                rvActivityLogs.setVisibility(View.VISIBLE);
                btnClearLogs.setVisibility(View.VISIBLE);
                adapter.setLogs(logs);
            }
        });
    }
}

package com.example;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * Full-screen Onboarding experience displayed upon first app launch
 * following the v9.3 database architecture migration.
 *
 * Provides a dedicated, distraction-free welcome screen with the ZEN logo,
 * migration context, and stacked primary ("Import Data") and secondary ("Start Fresh") actions.
 */
public class OnboardingActivity extends AppCompatActivity {

    public static final String PREF_KEY_MIGRATION = "is_first_launch_v9_3_migration";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        MaterialButton btnImport = findViewById(R.id.btn_onboarding_import);
        MaterialButton btnFresh = findViewById(R.id.btn_onboarding_fresh);

        if (btnImport != null) {
            btnImport.setOnClickListener(v -> handleImportData());
        }

        if (btnFresh != null) {
            btnFresh.setOnClickListener(v -> handleStartFresh());
        }
    }

    private void markOnboardingCompleted() {
        SharedPreferences prefs = getSharedPreferences("zen_prefs", Context.MODE_PRIVATE);
        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(PREF_KEY_MIGRATION, false).apply();
        defaultPrefs.edit().putBoolean(PREF_KEY_MIGRATION, false).apply();
    }

    /**
     * User chose to import an existing backup.
     * Transitions to BackupActivity placed on top of MainActivity in the task stack.
     */
    private void handleImportData() {
        markOnboardingCompleted();
        Intent mainIntent = new Intent(this, MainActivity.class);
        Intent backupIntent = new Intent(this, BackupActivity.class);
        startActivities(new Intent[]{mainIntent, backupIntent});
        finish();
    }

    /**
     * User chose to start fresh with a clean database.
     * Transitions straight to the primary MainActivity dashboard.
     */
    private void handleStartFresh() {
        markOnboardingCompleted();
        Intent mainIntent = new Intent(this, MainActivity.class);
        startActivity(mainIntent);
        finish();
    }
}

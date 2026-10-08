package com.example.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.BackupActivity;
import com.example.LogActivity;
import com.example.R;

/**
 * SettingsFragment: Navigating to Activity Logs and opening Backup & Restore Hub.
 */
public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupNavigation(view);
    }

    private void setupNavigation(View root) {
        View cardActivityLog = root.findViewById(R.id.card_settings_activity_log);
        if (cardActivityLog != null) {
            cardActivityLog.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), LogActivity.class);
                startActivity(intent);
            });
        }

        View cardFullBackup = root.findViewById(R.id.card_settings_full_backup);
        if (cardFullBackup != null) {
            cardFullBackup.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), BackupActivity.class);
                startActivity(intent);
            });
        }
    }
}

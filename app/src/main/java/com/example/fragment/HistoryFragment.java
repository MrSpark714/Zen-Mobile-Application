package com.example.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.DetailedHistoryActivity;
import com.example.MainActivity;
import com.example.R;
import com.example.adapter.SubjectAnalyticsAdapter;
import com.example.database.AppDatabase;
import com.example.model.SubjectStats;
import com.example.util.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * HistoryFragment: Displays overall attendance ledger and subject analytics.
 */
public class HistoryFragment extends Fragment implements SubjectAnalyticsAdapter.OnSubjectClickListener {

    private AppDatabase database;
    private SubjectAnalyticsAdapter analyticsAdapter;

    private TextView tvCountBadge;
    private RecyclerView rvAnalytics;
    private View layoutEmptyAnalytics;

    private List<SubjectStats> currentStats = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        database = AppDatabase.getInstance(requireContext());

        bindViews(view);
        setupRecyclerView();
        observeAnalytics();
    }

    private void bindViews(View root) {
        tvCountBadge = root.findViewById(R.id.tv_history_count_badge);
        rvAnalytics = root.findViewById(R.id.rv_subject_analytics);
        layoutEmptyAnalytics = root.findViewById(R.id.layout_empty_analytics);

        if (isAdded()) {
            int accentColor = ThemeHelper.getAccentColor(requireContext());
            ThemeHelper.applyAccentToBadge(tvCountBadge, accentColor);
        }
    }

    private void setupRecyclerView() {
        analyticsAdapter = new SubjectAnalyticsAdapter(requireContext(), this);
        rvAnalytics.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvAnalytics.setAdapter(analyticsAdapter);
    }

    private void observeAnalytics() {
        database.attendanceHistoryDao().getAllSubjectStats().observe(getViewLifecycleOwner(), stats -> {
            this.currentStats = stats != null ? stats : new ArrayList<>();
            analyticsAdapter.setStats(currentStats);

            if (currentStats.isEmpty()) {
                layoutEmptyAnalytics.setVisibility(View.VISIBLE);
                rvAnalytics.setVisibility(View.GONE);
                tvCountBadge.setText("0 subjects");
            } else {
                layoutEmptyAnalytics.setVisibility(View.GONE);
                rvAnalytics.setVisibility(View.VISIBLE);
                tvCountBadge.setText(currentStats.size() + (currentStats.size() == 1 ? " subject" : " subjects"));
            }
        });
    }

    @Override
    public void onSubjectClick(SubjectStats stats) {
        if (getActivity() != null) {
            Intent intent = new Intent(getActivity(), DetailedHistoryActivity.class);
            if (stats != null) {
                intent.putExtra(DetailedHistoryActivity.EXTRA_SUBJECT_NAME, stats.getSubjectName());
            }
            startActivity(intent);
        }
    }
}

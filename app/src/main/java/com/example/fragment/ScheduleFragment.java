package com.example.fragment;

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

import com.example.MainActivity;
import com.example.R;
import com.example.adapter.WeeklyScheduleAdapter;
import com.example.database.AppDatabase;
import com.example.model.ClassSchedule;
import com.example.util.ThemeHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ScheduleFragment: Displays and manages weekly class schedules (Timetable).
 */
public class ScheduleFragment extends Fragment implements WeeklyScheduleAdapter.OnScheduleActionListener {

    private AppDatabase database;
    private WeeklyScheduleAdapter scheduleAdapter;

    private TextView tvCountBadge;
    private TextView filterDayAll, filterDayMon, filterDayTue, filterDayWed, filterDayThu, filterDayFri;
    private RecyclerView rvSchedule;
    private View layoutEmptySchedule;
    private FloatingActionButton fabAddSchedule;

    private List<ClassSchedule> allSchedules = new ArrayList<>();
    private String currentFilterDay = "All";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_schedule, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        database = AppDatabase.getInstance(requireContext());

        bindViews(view);
        setupRecyclerView();
        setupDayFilters();
        setupFab();
        observeSchedules();
    }

    private void bindViews(View root) {
        tvCountBadge = root.findViewById(R.id.tv_schedule_count_badge);
        filterDayAll = root.findViewById(R.id.filter_day_all);
        filterDayMon = root.findViewById(R.id.filter_day_mon);
        filterDayTue = root.findViewById(R.id.filter_day_tue);
        filterDayWed = root.findViewById(R.id.filter_day_wed);
        filterDayThu = root.findViewById(R.id.filter_day_thu);
        filterDayFri = root.findViewById(R.id.filter_day_fri);
        rvSchedule = root.findViewById(R.id.rv_weekly_schedule);
        layoutEmptySchedule = root.findViewById(R.id.layout_empty_schedule);
        fabAddSchedule = root.findViewById(R.id.fab_add_schedule);
    }

    private void setupRecyclerView() {
        scheduleAdapter = new WeeklyScheduleAdapter(requireContext(), this);
        rvSchedule.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvSchedule.setAdapter(scheduleAdapter);
    }

    private void setupDayFilters() {
        TextView[] filterChips = {filterDayAll, filterDayMon, filterDayTue, filterDayWed, filterDayThu, filterDayFri};
        String[] dayNames = {"All", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};

        for (int i = 0; i < filterChips.length; i++) {
            final int index = i;
            filterChips[i].setOnClickListener(v -> {
                currentFilterDay = dayNames[index];
                if (!isAdded()) return;
                int accentColor = ThemeHelper.getAccentColor(requireContext());
                for (int j = 0; j < filterChips.length; j++) {
                    ThemeHelper.applyChipSelected(filterChips[j], j == index, accentColor);
                }
                refreshScheduleList();
            });
        }

        if (isAdded()) {
            int accentColor = ThemeHelper.getAccentColor(requireContext());
            ThemeHelper.applyAccentToFab(fabAddSchedule, accentColor);
            ThemeHelper.applyAccentToBadge(tvCountBadge, accentColor);
            for (int i = 0; i < filterChips.length; i++) {
                boolean isSelected = dayNames[i].equalsIgnoreCase(currentFilterDay);
                ThemeHelper.applyChipSelected(filterChips[i], isSelected, accentColor);
            }
        }
    }

    private void setupFab() {
        fabAddSchedule.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showAddClassBottomSheet(null);
            }
        });
    }

    private void observeSchedules() {
        database.classScheduleDao().getAllSchedules().observe(getViewLifecycleOwner(), schedules -> {
            this.allSchedules = schedules != null ? new ArrayList<>(schedules) : new ArrayList<>();
            Collections.sort(this.allSchedules, ClassSchedule.TIMETABLE_COMPARATOR);
            refreshScheduleList();
        });
    }

    private void refreshScheduleList() {
        List<ClassSchedule> filtered = new ArrayList<>();
        if ("All".equalsIgnoreCase(currentFilterDay)) {
            filtered.addAll(allSchedules);
            Collections.sort(filtered, ClassSchedule.TIMETABLE_COMPARATOR);
        } else {
            for (ClassSchedule cs : allSchedules) {
                if (currentFilterDay.equalsIgnoreCase(cs.getDayOfWeek())) {
                    filtered.add(cs);
                }
            }
            Collections.sort(filtered, ClassSchedule.CHRONOLOGICAL_COMPARATOR);
        }

        scheduleAdapter.setSchedules(filtered);

        if (filtered.isEmpty()) {
            layoutEmptySchedule.setVisibility(View.VISIBLE);
            rvSchedule.setVisibility(View.GONE);
        } else {
            layoutEmptySchedule.setVisibility(View.GONE);
            rvSchedule.setVisibility(View.VISIBLE);
        }

        tvCountBadge.setText(allSchedules.size() + (allSchedules.size() == 1 ? " class" : " classes"));
    }

    @Override
    public void onEditSchedule(ClassSchedule schedule) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onEditSchedule(schedule);
        }
    }

    @Override
    public void onDeleteSchedule(ClassSchedule schedule) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onDeleteSchedule(schedule);
        }
    }
}

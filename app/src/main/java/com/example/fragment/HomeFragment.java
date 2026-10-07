package com.example.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.MainActivity;
import com.example.NoteEditorActivity;
import com.example.R;
import com.example.adapter.NoteAdapter;
import com.example.adapter.TaskAdapter;
import com.example.adapter.TodayClassAdapter;
import com.example.database.AppDatabase;
import com.example.model.AttendanceHistory;
import com.example.model.ClassSchedule;
import com.example.model.ClassWithTodayStatus;
import com.example.model.Note;
import com.example.model.Task;
import com.example.util.ThemeHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HomeFragment: Dashboard displaying Today's Classes, Active Tasks, and Recent Notes.
 */
public class HomeFragment extends Fragment implements
        TodayClassAdapter.OnAttendanceActionListener,
        TaskAdapter.OnTaskActionListener,
        NoteAdapter.OnNoteClickListener {

    private static final int SUB_TAB_CLASSES = 0;
    private static final int SUB_TAB_TASKS = 1;
    private static final int SUB_TAB_NOTES = 2;
    private int currentSubTab = SUB_TAB_CLASSES;

    private AppDatabase database;

    private TextView tvHeaderTitle;
    private TextView tvHeaderSubtitle;
    private TextView tvItemCountBadge;
    private TextView tabClasses, tabTasks, tabNotes;
    private LinearLayout searchBarContainer;
    private EditText etSearch;
    private ImageView btnClearSearch;

    private LinearLayout viewClasses, viewTasks, viewNotes;
    private TextView tvTodaySummary;
    private RecyclerView rvClasses, rvTasks, rvNotes;
    private View layoutEmptyClasses, layoutEmptyTasks, layoutEmptyNotes;
    private TextView tvEmptyClassesTitle, tvEmptyClassesDesc;
    private FloatingActionButton fabAdd;

    private TodayClassAdapter todayClassAdapter;
    private TaskAdapter taskAdapter;
    private NoteAdapter noteAdapter;

    private List<ClassSchedule> allSchedules = new ArrayList<>();
    private List<AttendanceHistory> todayAttendance = new ArrayList<>();
    private List<Task> currentTasks = new ArrayList<>();
    private List<Note> currentNotes = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        database = AppDatabase.getInstance(requireContext());

        bindViews(view);
        setupAdapters();
        setupSubTabs();
        setupSearch();
        setupFab();
        observeData("");
    }

    private void bindViews(View root) {
        tvHeaderTitle = root.findViewById(R.id.tv_home_header_title);
        tvHeaderSubtitle = root.findViewById(R.id.tv_home_header_subtitle);
        tvItemCountBadge = root.findViewById(R.id.tv_home_item_count_badge);
        tabClasses = root.findViewById(R.id.tab_home_classes);
        tabTasks = root.findViewById(R.id.tab_home_tasks);
        tabNotes = root.findViewById(R.id.tab_home_notes);

        searchBarContainer = root.findViewById(R.id.home_search_bar_container);
        etSearch = root.findViewById(R.id.et_home_search);
        btnClearSearch = root.findViewById(R.id.btn_home_clear_search);

        viewClasses = root.findViewById(R.id.home_view_classes);
        viewTasks = root.findViewById(R.id.home_view_tasks);
        viewNotes = root.findViewById(R.id.home_view_notes);

        tvTodaySummary = root.findViewById(R.id.tv_home_today_summary);
        rvClasses = root.findViewById(R.id.rv_home_classes);
        rvTasks = root.findViewById(R.id.rv_home_tasks);
        rvNotes = root.findViewById(R.id.rv_home_notes);

        layoutEmptyClasses = root.findViewById(R.id.layout_empty_home_classes);
        layoutEmptyTasks = root.findViewById(R.id.layout_empty_home_tasks);
        layoutEmptyNotes = root.findViewById(R.id.layout_empty_home_notes);
        tvEmptyClassesTitle = root.findViewById(R.id.tv_empty_classes_title);
        tvEmptyClassesDesc = root.findViewById(R.id.tv_empty_classes_desc);

        fabAdd = root.findViewById(R.id.fab_home_add);
    }

    private void setupAdapters() {
        Context context = requireContext();
        todayClassAdapter = new TodayClassAdapter(context, this);
        rvClasses.setLayoutManager(new LinearLayoutManager(context));
        rvClasses.setAdapter(todayClassAdapter);

        taskAdapter = new TaskAdapter(this);
        rvTasks.setLayoutManager(new LinearLayoutManager(context));
        rvTasks.setAdapter(taskAdapter);

        noteAdapter = new NoteAdapter(this);
        rvNotes.setLayoutManager(new LinearLayoutManager(context));
        rvNotes.setAdapter(noteAdapter);
    }

    private void setupSubTabs() {
        tabClasses.setOnClickListener(v -> switchSubTab(SUB_TAB_CLASSES));
        tabTasks.setOnClickListener(v -> switchSubTab(SUB_TAB_TASKS));
        tabNotes.setOnClickListener(v -> switchSubTab(SUB_TAB_NOTES));
        switchSubTab(currentSubTab);
    }

    private void switchSubTab(int subTab) {
        currentSubTab = subTab;
        if (!isAdded()) return;

        int accentColor = ThemeHelper.getAccentColor(requireContext());
        int onAccentColor = ThemeHelper.getOnAccentColor(accentColor);
        int inactiveColor = ContextCompat.getColor(requireContext(), R.color.zen_text_secondary);

        tabClasses.setBackgroundResource(R.drawable.bg_nav_tab_inactive);
        tabClasses.setTextColor(inactiveColor);
        tabTasks.setBackgroundResource(R.drawable.bg_nav_tab_inactive);
        tabTasks.setTextColor(inactiveColor);
        tabNotes.setBackgroundResource(R.drawable.bg_nav_tab_inactive);
        tabNotes.setTextColor(inactiveColor);

        viewClasses.setVisibility(View.GONE);
        viewTasks.setVisibility(View.GONE);
        viewNotes.setVisibility(View.GONE);

        if (subTab == SUB_TAB_CLASSES) {
            tabClasses.setBackground(ThemeHelper.createActiveTabDrawable(accentColor));
            tabClasses.setTextColor(onAccentColor);
            viewClasses.setVisibility(View.VISIBLE);
            searchBarContainer.setVisibility(View.GONE);
            tvHeaderSubtitle.setText("TODAY'S LECTURES");
            updateClassesBadge();
        } else if (subTab == SUB_TAB_TASKS) {
            tabTasks.setBackground(ThemeHelper.createActiveTabDrawable(accentColor));
            tabTasks.setTextColor(onAccentColor);
            viewTasks.setVisibility(View.VISIBLE);
            searchBarContainer.setVisibility(View.VISIBLE);
            etSearch.setHint("Search tasks…");
            tvHeaderSubtitle.setText("ACTIVE TASKS");
            updateTasksBadge();
        } else {
            tabNotes.setBackground(ThemeHelper.createActiveTabDrawable(accentColor));
            tabNotes.setTextColor(onAccentColor);
            viewNotes.setVisibility(View.VISIBLE);
            searchBarContainer.setVisibility(View.VISIBLE);
            etSearch.setHint("Search notes…");
            tvHeaderSubtitle.setText("RECENT NOTES");
            updateNotesBadge();
        }

        ThemeHelper.applyAccentToFab(fabAdd, accentColor);
        ThemeHelper.applyAccentToBadge(tvItemCountBadge, accentColor);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                btnClearSearch.setVisibility(TextUtils.isEmpty(query) ? View.GONE : View.VISIBLE);
                observeData(query);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            hideKeyboard();
        });
    }

    private void setupFab() {
        fabAdd.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                MainActivity activity = (MainActivity) getActivity();
                if (currentSubTab == SUB_TAB_CLASSES) {
                    activity.showAddClassBottomSheet(null);
                } else if (currentSubTab == SUB_TAB_TASKS) {
                    activity.showAddTaskBottomSheet();
                } else {
                    Intent intent = new Intent(activity, NoteEditorActivity.class);
                    startActivity(intent);
                }
            }
        });
    }

    private void observeData(String query) {
        if (!isAdded()) return;

        // Observe Classes & Attendance
        long todayStartOfDay = MainActivity.getStartOfDayMillis(Calendar.getInstance());
        database.attendanceHistoryDao().getRecordsForDate(todayStartOfDay).observe(getViewLifecycleOwner(), records -> {
            this.todayAttendance = records != null ? records : new ArrayList<>();
            refreshTodayClassesView();
        });

        database.classScheduleDao().getAllSchedules().observe(getViewLifecycleOwner(), schedules -> {
            this.allSchedules = schedules != null ? new ArrayList<>(schedules) : new ArrayList<>();
            Collections.sort(this.allSchedules, ClassSchedule.TIMETABLE_COMPARATOR);
            refreshTodayClassesView();
        });

        // Observe Tasks
        if (TextUtils.isEmpty(query)) {
            database.taskDao().getAllTasks().observe(getViewLifecycleOwner(), this::updateTasksList);
        } else {
            database.taskDao().searchTasks(query).observe(getViewLifecycleOwner(), this::updateTasksList);
        }

        // Observe Notes
        if (TextUtils.isEmpty(query)) {
            database.noteDao().getAllNotes().observe(getViewLifecycleOwner(), this::updateNotesList);
        } else {
            database.noteDao().searchNotes(query).observe(getViewLifecycleOwner(), this::updateNotesList);
        }
    }

    private void refreshTodayClassesView() {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        boolean isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY);

        if (isWeekend) {
            layoutEmptyClasses.setVisibility(View.VISIBLE);
            rvClasses.setVisibility(View.GONE);
            tvEmptyClassesTitle.setText("No classes this weekend");
            tvEmptyClassesDesc.setText("Enjoy your rest and mindfulness. Weekends are strictly free of scheduled classes.");
            tvTodaySummary.setText("Weekend Relaxation • No scheduled lectures");
            if (currentSubTab == SUB_TAB_CLASSES) updateClassesBadge();
            return;
        }

        String todayDayName = MainActivity.getCurrentDayOfWeekName();
        List<ClassSchedule> todayList = new ArrayList<>();
        for (ClassSchedule cs : allSchedules) {
            if (todayDayName.equalsIgnoreCase(cs.getDayOfWeek())) {
                todayList.add(cs);
            }
        }
        Collections.sort(todayList, ClassSchedule.CHRONOLOGICAL_COMPARATOR);

        Map<String, AttendanceHistory> recordMap = new HashMap<>();
        for (AttendanceHistory ar : todayAttendance) {
            if (ar.getSubjectName() != null) {
                recordMap.put(ar.getSubjectName().trim().toLowerCase(Locale.ROOT), ar);
            }
        }

        List<ClassWithTodayStatus> compositeList = new ArrayList<>();
        for (ClassSchedule cs : todayList) {
            String key = cs.getSubjectName() != null ? cs.getSubjectName().trim().toLowerCase(Locale.ROOT) : "";
            compositeList.add(new ClassWithTodayStatus(cs, recordMap.get(key)));
        }

        todayClassAdapter.setClasses(compositeList);

        if (compositeList.isEmpty()) {
            layoutEmptyClasses.setVisibility(View.VISIBLE);
            rvClasses.setVisibility(View.GONE);
            tvEmptyClassesTitle.setText("No classes today (" + todayDayName + ")");
            tvEmptyClassesDesc.setText("No recurring classes scheduled for " + todayDayName + ". Configure schedules in the Schedule tab.");
            tvTodaySummary.setText("0 classes scheduled for " + todayDayName);
        } else {
            layoutEmptyClasses.setVisibility(View.GONE);
            rvClasses.setVisibility(View.VISIBLE);
            tvTodaySummary.setText(compositeList.size() + (compositeList.size() == 1 ? " class" : " classes") + " scheduled for " + todayDayName);
        }

        if (currentSubTab == SUB_TAB_CLASSES) {
            updateClassesBadge();
        }
    }

    private void updateTasksList(List<Task> tasks) {
        this.currentTasks = tasks != null ? tasks : new ArrayList<>();
        taskAdapter.setTasks(currentTasks);

        if (currentTasks.isEmpty()) {
            layoutEmptyTasks.setVisibility(View.VISIBLE);
            rvTasks.setVisibility(View.GONE);
        } else {
            layoutEmptyTasks.setVisibility(View.GONE);
            rvTasks.setVisibility(View.VISIBLE);
        }

        if (currentSubTab == SUB_TAB_TASKS) {
            updateTasksBadge();
        }
    }

    private void updateNotesList(List<Note> notes) {
        this.currentNotes = notes != null ? notes : new ArrayList<>();
        noteAdapter.setNotes(currentNotes);

        if (currentNotes.isEmpty()) {
            layoutEmptyNotes.setVisibility(View.VISIBLE);
            rvNotes.setVisibility(View.GONE);
        } else {
            layoutEmptyNotes.setVisibility(View.GONE);
            rvNotes.setVisibility(View.VISIBLE);
        }

        if (currentSubTab == SUB_TAB_NOTES) {
            updateNotesBadge();
        }
    }

    private void updateClassesBadge() {
        String todayDayName = MainActivity.getCurrentDayOfWeekName();
        int count = 0;
        for (ClassSchedule cs : allSchedules) {
            if (todayDayName.equalsIgnoreCase(cs.getDayOfWeek())) count++;
        }
        tvItemCountBadge.setText(count + (count == 1 ? " class" : " classes"));
    }

    private void updateTasksBadge() {
        long pending = 0;
        for (Task t : currentTasks) {
            if (!t.isCompleted()) pending++;
        }
        tvItemCountBadge.setText(pending + " pending");
    }

    private void updateNotesBadge() {
        tvItemCountBadge.setText(currentNotes.size() + (currentNotes.size() == 1 ? " note" : " notes"));
    }

    private void hideKeyboard() {
        if (getActivity() != null && getView() != null) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
            }
        }
    }

    // Callbacks
    @Override
    public void onMarkAttendance(String subjectName, int creditHours, String classType, String status) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onMarkAttendance(subjectName, creditHours, classType, status);
        }
    }

    @Override
    public void onMarkAttendance(ClassSchedule schedule, String status) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onMarkAttendance(schedule, status);
        }
    }

    @Override
    public void onTaskToggle(Task task, boolean isCompleted) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onTaskToggle(task, isCompleted);
        }
    }

    @Override
    public void onTaskDelete(Task task) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onTaskDelete(task);
        }
    }

    @Override
    public void onNoteClick(Note note) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onNoteClick(note);
        }
    }

    @Override
    public void onNoteDelete(Note note) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onNoteDelete(note);
        }
    }
}

package com.example;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.adapter.AttendanceHistoryAdapter;
import com.example.adapter.NoteAdapter;
import com.example.adapter.SubjectAnalyticsAdapter;
import com.example.adapter.TaskAdapter;
import com.example.adapter.TodayClassAdapter;
import com.example.adapter.WeeklyScheduleAdapter;
import com.example.database.AppDatabase;
import com.example.model.AttendanceRecord;
import com.example.model.ClassSchedule;
import com.example.model.ClassWithTodayStatus;
import com.example.model.Note;
import com.example.model.SubjectStats;
import com.example.model.Task;
import com.example.util.NotificationHelper;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MainActivity is the core screen of the ZEN minimalist dark-themed productivity application.
 *
 * Modules:
 * 1. Notes (Zen minimalist note taking)
 * 2. Tasks (Scheduled task management with precise alarms)
 * 3. Attendance Tracker (Today's classes, Mon-Fri schedule config, relational percentage analytics, 5-min alarms)
 */
public class MainActivity extends AppCompatActivity implements
        NoteAdapter.OnNoteClickListener,
        TaskAdapter.OnTaskActionListener,
        TodayClassAdapter.OnAttendanceActionListener,
        WeeklyScheduleAdapter.OnScheduleActionListener,
        SubjectAnalyticsAdapter.OnSubjectClickListener {

    public static final int TAB_NOTES = 0;
    public static final int TAB_TASKS = 1;
    public static final int TAB_ATTENDANCE = 2;
    private int currentTab = TAB_NOTES;

    // Attendance Sub-Tab States
    public static final int SUB_TAB_TODAY = 0;
    public static final int SUB_TAB_TIMETABLE = 1;
    public static final int SUB_TAB_ANALYTICS = 2;
    private int currentAttendanceSubTab = SUB_TAB_TODAY;

    // View References - Header
    private TextView tvHeaderTitle;
    private TextView tvHeaderSubtitle;
    private TextView tvItemCountBadge;
    private EditText etSearch;
    private ImageView btnClearSearch;
    private LinearLayout searchBarContainer;

    // View References - Navigation & Pager
    private ViewPager2 viewPager;
    private TextView btnNavNotes;
    private TextView btnNavTasks;
    private TextView btnNavAttendance;
    private FloatingActionButton fabAdd;

    // Page Views & Adapters - Notes & Tasks
    private RecyclerView rvNotes;
    private RecyclerView rvTasks;
    private View layoutEmptyNotes;
    private View layoutEmptyTasks;
    private NoteAdapter noteAdapter;
    private TaskAdapter taskAdapter;

    // Page Views & Adapters - Attendance Tracker
    private AttendanceViewHolder attendanceViewHolder;
    private TodayClassAdapter todayClassAdapter;
    private WeeklyScheduleAdapter weeklyScheduleAdapter;
    private SubjectAnalyticsAdapter subjectAnalyticsAdapter;

    private MainPagerAdapter pagerAdapter;

    // Cached Data
    private List<Note> currentNotes = new ArrayList<>();
    private List<Task> currentTasks = new ArrayList<>();
    private List<ClassSchedule> currentSchedules = new ArrayList<>();
    private List<AttendanceRecord> todayAttendanceRecords = new ArrayList<>();
    private List<SubjectStats> currentSubjectStats = new ArrayList<>();
    private String currentScheduleFilterDay = "All";

    // Database Reference
    private AppDatabase database;

    // Notification Permission Launcher (Android 13+)
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    NotificationHelper.createNotificationChannel(this);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        database = AppDatabase.getInstance(this);

        NotificationHelper.createNotificationChannel(this);
        checkNotificationPermission();

        bindViews();
        setupAdapters();
        setupViewPager();
        setupNavigation();
        setupSearch();
        setupFab();

        handleIntent(getIntent());

        observeDatabase("");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null) {
            String tab = intent.getStringExtra("NAV_TAB");
            if ("tasks".equalsIgnoreCase(tab)) {
                if (viewPager != null) viewPager.setCurrentItem(TAB_TASKS, true);
            } else if ("attendance".equalsIgnoreCase(tab) || "classes".equalsIgnoreCase(tab)) {
                if (viewPager != null) viewPager.setCurrentItem(TAB_ATTENDANCE, true);
            }
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void bindViews() {
        tvHeaderTitle = findViewById(R.id.tv_header_title);
        tvHeaderSubtitle = findViewById(R.id.tv_header_subtitle);
        tvItemCountBadge = findViewById(R.id.tv_item_count_badge);
        searchBarContainer = findViewById(R.id.search_bar_container);
        etSearch = findViewById(R.id.et_search);
        btnClearSearch = findViewById(R.id.btn_clear_search);

        viewPager = findViewById(R.id.view_pager);
        btnNavNotes = findViewById(R.id.btn_nav_notes);
        btnNavTasks = findViewById(R.id.btn_nav_tasks);
        btnNavAttendance = findViewById(R.id.btn_nav_attendance);
        fabAdd = findViewById(R.id.fab_add);

        View btnBackup = findViewById(R.id.btn_header_backup);
        if (btnBackup != null) {
            btnBackup.setOnClickListener(v -> {
                Intent backupIntent = new Intent(MainActivity.this, BackupActivity.class);
                startActivity(backupIntent);
            });
        }
    }

    private void setupAdapters() {
        noteAdapter = new NoteAdapter(this);
        taskAdapter = new TaskAdapter(this);
        todayClassAdapter = new TodayClassAdapter(this, this);
        weeklyScheduleAdapter = new WeeklyScheduleAdapter(this, this);
        subjectAnalyticsAdapter = new SubjectAnalyticsAdapter(this, this);
    }

    private void setupViewPager() {
        pagerAdapter = new MainPagerAdapter();
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(2);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                onTabChanged(position);
            }
        });
    }

    private void setupNavigation() {
        btnNavNotes.setOnClickListener(v -> viewPager.setCurrentItem(TAB_NOTES, true));
        btnNavTasks.setOnClickListener(v -> viewPager.setCurrentItem(TAB_TASKS, true));
        btnNavAttendance.setOnClickListener(v -> viewPager.setCurrentItem(TAB_ATTENDANCE, true));
    }

    private void onTabChanged(int tab) {
        currentTab = tab;
        String query = etSearch.getText().toString().trim();

        // Reset bottom navigation styles
        btnNavNotes.setBackgroundResource(R.drawable.bg_nav_tab_inactive);
        btnNavNotes.setTextColor(ContextCompat.getColor(this, R.color.zen_text_secondary));
        btnNavTasks.setBackgroundResource(R.drawable.bg_nav_tab_inactive);
        btnNavTasks.setTextColor(ContextCompat.getColor(this, R.color.zen_text_secondary));
        btnNavAttendance.setBackgroundResource(R.drawable.bg_nav_tab_inactive);
        btnNavAttendance.setTextColor(ContextCompat.getColor(this, R.color.zen_text_secondary));

        if (tab == TAB_NOTES) {
            btnNavNotes.setBackgroundResource(R.drawable.bg_nav_tab_active);
            btnNavNotes.setTextColor(ContextCompat.getColor(this, R.color.zen_on_accent));

            tvHeaderTitle.setText("ZEN");
            tvHeaderSubtitle.setText("RECENT NOTES");
            searchBarContainer.setVisibility(View.VISIBLE);
            etSearch.setHint(R.string.search_notes_hint);
            fabAdd.setVisibility(View.VISIBLE);
            fabAdd.setContentDescription("Add Note");
            updateNotesBadge();
        } else if (tab == TAB_TASKS) {
            btnNavTasks.setBackgroundResource(R.drawable.bg_nav_tab_active);
            btnNavTasks.setTextColor(ContextCompat.getColor(this, R.color.zen_on_accent));

            tvHeaderTitle.setText("ZEN");
            tvHeaderSubtitle.setText("ACTIVE TASKS");
            searchBarContainer.setVisibility(View.VISIBLE);
            etSearch.setHint(R.string.search_tasks_hint);
            fabAdd.setVisibility(View.VISIBLE);
            fabAdd.setContentDescription("Add Task");
            updateTasksBadge();
        } else {
            btnNavAttendance.setBackgroundResource(R.drawable.bg_nav_tab_active);
            btnNavAttendance.setTextColor(ContextCompat.getColor(this, R.color.zen_on_accent));

            tvHeaderTitle.setText("ZEN");
            tvHeaderSubtitle.setText("ATTENDANCE TRACKER");
            searchBarContainer.setVisibility(View.GONE);
            fabAdd.setVisibility(View.GONE); // Attendance page has its own specialized FAB
            updateAttendanceBadge();
        }

        observeDatabase(query);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                btnClearSearch.setVisibility(TextUtils.isEmpty(query) ? View.GONE : View.VISIBLE);
                observeDatabase(query);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            hideKeyboard(etSearch);
        });
    }

    private void setupFab() {
        fabAdd.setOnClickListener(v -> {
            if (currentTab == TAB_NOTES) {
                Intent intent = new Intent(MainActivity.this, NoteEditorActivity.class);
                startActivity(intent);
            } else if (currentTab == TAB_TASKS) {
                showAddTaskBottomSheet();
            } else {
                showAddClassBottomSheet(null);
            }
        });
    }

    private void observeDatabase(String query) {
        if (currentTab == TAB_NOTES) {
            if (TextUtils.isEmpty(query)) {
                database.noteDao().getAllNotes().observe(this, this::updateNotesList);
            } else {
                database.noteDao().searchNotes(query).observe(this, this::updateNotesList);
            }
        } else if (currentTab == TAB_TASKS) {
            if (TextUtils.isEmpty(query)) {
                database.taskDao().getAllTasks().observe(this, this::updateTasksList);
            } else {
                database.taskDao().searchTasks(query).observe(this, this::updateTasksList);
            }
        } else {
            observeAttendanceData();
        }
    }

    private void observeAttendanceData() {
        long todayStartOfDay = getStartOfDayMillis(Calendar.getInstance());

        // Observe Today's Attendance Records
        database.attendanceRecordDao().getRecordsForDate(todayStartOfDay).observe(this, records -> {
            this.todayAttendanceRecords = records != null ? records : new ArrayList<>();
            refreshTodayClassesView();
        });

        // Observe All Class Schedules
        database.classScheduleDao().getAllSchedules().observe(this, schedules -> {
            this.currentSchedules = schedules != null ? schedules : new ArrayList<>();
            refreshTodayClassesView();
            refreshWeeklyScheduleView();
            updateAttendanceBadge();
        });

        // Observe Subject Analytics with exact SQL percentage math
        database.attendanceRecordDao().getAllSubjectStats().observe(this, stats -> {
            this.currentSubjectStats = stats != null ? stats : new ArrayList<>();
            if (attendanceViewHolder != null) {
                attendanceViewHolder.updateAnalyticsView(currentSubjectStats);
            }
        });
    }

    private void refreshTodayClassesView() {
        if (attendanceViewHolder == null) return;

        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        boolean isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY);

        if (isWeekend) {
            attendanceViewHolder.showTodayWeekendEmptyState();
            return;
        }

        String todayDayName = getCurrentDayOfWeekName();
        List<ClassSchedule> todaySchedules = new ArrayList<>();
        for (ClassSchedule cs : currentSchedules) {
            if (todayDayName.equalsIgnoreCase(cs.getDayOfWeek())) {
                todaySchedules.add(cs);
            }
        }

        // Map today's attendance records by scheduleId
        Map<Integer, AttendanceRecord> recordMap = new HashMap<>();
        for (AttendanceRecord ar : todayAttendanceRecords) {
            recordMap.put(ar.getScheduleId(), ar);
        }

        List<ClassWithTodayStatus> compositeList = new ArrayList<>();
        for (ClassSchedule cs : todaySchedules) {
            compositeList.add(new ClassWithTodayStatus(cs, recordMap.get(cs.getId())));
        }

        todayClassAdapter.setClasses(compositeList);
        attendanceViewHolder.updateTodayClassesUI(compositeList, todayDayName);
    }

    private void refreshWeeklyScheduleView() {
        if (attendanceViewHolder == null) return;

        List<ClassSchedule> filteredList = new ArrayList<>();
        if ("All".equalsIgnoreCase(currentScheduleFilterDay)) {
            filteredList.addAll(currentSchedules);
        } else {
            for (ClassSchedule cs : currentSchedules) {
                if (currentScheduleFilterDay.equalsIgnoreCase(cs.getDayOfWeek())) {
                    filteredList.add(cs);
                }
            }
        }

        weeklyScheduleAdapter.setSchedules(filteredList);
        attendanceViewHolder.updateScheduleUI(filteredList);
    }

    private void updateNotesList(List<Note> notes) {
        this.currentNotes = notes != null ? notes : new ArrayList<>();
        noteAdapter.setNotes(currentNotes);

        if (rvNotes != null && layoutEmptyNotes != null) {
            if (currentNotes.isEmpty()) {
                layoutEmptyNotes.setVisibility(View.VISIBLE);
                rvNotes.setVisibility(View.GONE);
            } else {
                layoutEmptyNotes.setVisibility(View.GONE);
                rvNotes.setVisibility(View.VISIBLE);
            }
        }

        if (currentTab == TAB_NOTES) {
            updateNotesBadge();
        }
    }

    private void updateNotesBadge() {
        if (currentNotes == null || currentNotes.isEmpty()) {
            tvItemCountBadge.setText("0 notes");
        } else {
            tvItemCountBadge.setText(currentNotes.size() + (currentNotes.size() == 1 ? " note" : " notes"));
        }
    }

    private void updateTasksList(List<Task> tasks) {
        this.currentTasks = tasks != null ? tasks : new ArrayList<>();
        taskAdapter.setTasks(currentTasks);

        if (rvTasks != null && layoutEmptyTasks != null) {
            if (currentTasks.isEmpty()) {
                layoutEmptyTasks.setVisibility(View.VISIBLE);
                rvTasks.setVisibility(View.GONE);
            } else {
                layoutEmptyTasks.setVisibility(View.GONE);
                rvTasks.setVisibility(View.VISIBLE);
            }
        }

        if (currentTab == TAB_TASKS) {
            updateTasksBadge();
        }
    }

    private void updateTasksBadge() {
        if (currentTasks == null || currentTasks.isEmpty()) {
            tvItemCountBadge.setText("0 tasks");
        } else {
            long pendingCount = 0;
            for (Task t : currentTasks) {
                if (!t.isCompleted()) pendingCount++;
            }
            tvItemCountBadge.setText(pendingCount + " pending");
        }
    }

    private void updateAttendanceBadge() {
        if (currentSchedules == null || currentSchedules.isEmpty()) {
            tvItemCountBadge.setText("0 classes");
        } else {
            tvItemCountBadge.setText(currentSchedules.size() + (currentSchedules.size() == 1 ? " class" : " classes"));
        }
    }

    // =========================================================================
    // ViewPager2 Adapter
    // =========================================================================

    private class MainPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TAB_NOTES) {
                View view = inflater.inflate(R.layout.page_notes, parent, false);
                return new NotesViewHolder(view);
            } else if (viewType == TAB_TASKS) {
                View view = inflater.inflate(R.layout.page_tasks, parent, false);
                return new TasksViewHolder(view);
            } else {
                View view = inflater.inflate(R.layout.page_attendance, parent, false);
                return new AttendanceViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof NotesViewHolder) {
                NotesViewHolder nHolder = (NotesViewHolder) holder;
                rvNotes = nHolder.rvNotes;
                layoutEmptyNotes = nHolder.layoutEmptyNotes;

                rvNotes.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                rvNotes.setAdapter(noteAdapter);
                updateNotesList(currentNotes);
            } else if (holder instanceof TasksViewHolder) {
                TasksViewHolder tHolder = (TasksViewHolder) holder;
                rvTasks = tHolder.rvTasks;
                layoutEmptyTasks = tHolder.layoutEmptyTasks;

                rvTasks.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                rvTasks.setAdapter(taskAdapter);
                updateTasksList(currentTasks);
            } else if (holder instanceof AttendanceViewHolder) {
                attendanceViewHolder = (AttendanceViewHolder) holder;
                attendanceViewHolder.setup();
                observeAttendanceData();
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }

    static class NotesViewHolder extends RecyclerView.ViewHolder {
        RecyclerView rvNotes;
        View layoutEmptyNotes;

        NotesViewHolder(@NonNull View itemView) {
            super(itemView);
            rvNotes = itemView.findViewById(R.id.rv_notes);
            layoutEmptyNotes = itemView.findViewById(R.id.layout_empty_notes);
        }
    }

    static class TasksViewHolder extends RecyclerView.ViewHolder {
        RecyclerView rvTasks;
        View layoutEmptyTasks;

        TasksViewHolder(@NonNull View itemView) {
            super(itemView);
            rvTasks = itemView.findViewById(R.id.rv_tasks);
            layoutEmptyTasks = itemView.findViewById(R.id.layout_empty_tasks);
        }
    }

    // =========================================================================
    // Attendance Tracker View Holder & Sub-Tab Architecture
    // =========================================================================

    public class AttendanceViewHolder extends RecyclerView.ViewHolder {
        TextView tvCurrentDateBadge;
        TextView tabToday;
        TextView tabTimetable;
        TextView tabAnalytics;

        // Sub-Views
        View viewTodayClasses;
        View viewWeeklySchedule;
        View viewAnalytics;

        // Today's View Components
        TextView tvTodaySummaryInfo;
        RecyclerView rvTodayClasses;
        View layoutEmptyToday;
        TextView tvEmptyTodayTitle;
        TextView tvEmptyTodaySubtitle;

        // Schedule View Components
        TextView filterDayAll, filterDayMon, filterDayTue, filterDayWed, filterDayThu, filterDayFri;
        RecyclerView rvWeeklySchedule;
        View layoutEmptySchedule;

        // Analytics View Components
        TextView tvOverallPercentage;
        ProgressBar progressOverallAttendance;
        TextView tvTotalPresent;
        TextView tvTotalAbsent;
        TextView tvTotalHoliday;
        RecyclerView rvSubjectAnalytics;
        View layoutEmptyAnalytics;

        FloatingActionButton fabAddClass;

        AttendanceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCurrentDateBadge = itemView.findViewById(R.id.tv_current_date_badge);
            tabToday = itemView.findViewById(R.id.tab_today);
            tabTimetable = itemView.findViewById(R.id.tab_timetable);
            tabAnalytics = itemView.findViewById(R.id.tab_analytics);

            viewTodayClasses = itemView.findViewById(R.id.view_today_classes);
            viewWeeklySchedule = itemView.findViewById(R.id.view_weekly_schedule);
            viewAnalytics = itemView.findViewById(R.id.view_analytics);

            tvTodaySummaryInfo = itemView.findViewById(R.id.tv_today_summary_info);
            rvTodayClasses = itemView.findViewById(R.id.rv_today_classes);
            layoutEmptyToday = itemView.findViewById(R.id.layout_empty_today);
            tvEmptyTodayTitle = itemView.findViewById(R.id.tv_empty_today_title);
            tvEmptyTodaySubtitle = itemView.findViewById(R.id.tv_empty_today_subtitle);

            filterDayAll = itemView.findViewById(R.id.filter_day_all);
            filterDayMon = itemView.findViewById(R.id.filter_day_mon);
            filterDayTue = itemView.findViewById(R.id.filter_day_tue);
            filterDayWed = itemView.findViewById(R.id.filter_day_wed);
            filterDayThu = itemView.findViewById(R.id.filter_day_thu);
            filterDayFri = itemView.findViewById(R.id.filter_day_fri);
            rvWeeklySchedule = itemView.findViewById(R.id.rv_weekly_schedule);
            layoutEmptySchedule = itemView.findViewById(R.id.layout_empty_schedule);

            tvOverallPercentage = itemView.findViewById(R.id.tv_overall_percentage);
            progressOverallAttendance = itemView.findViewById(R.id.progress_overall_attendance);
            tvTotalPresent = itemView.findViewById(R.id.tv_total_present);
            tvTotalAbsent = itemView.findViewById(R.id.tv_total_absent);
            tvTotalHoliday = itemView.findViewById(R.id.tv_total_holiday);
            rvSubjectAnalytics = itemView.findViewById(R.id.rv_subject_analytics);
            layoutEmptyAnalytics = itemView.findViewById(R.id.layout_empty_analytics);

            fabAddClass = itemView.findViewById(R.id.fab_add_class);
        }

        void setup() {
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());
            tvCurrentDateBadge.setText(sdf.format(new Date()));

            // RecyclerView Setup
            rvTodayClasses.setLayoutManager(new LinearLayoutManager(MainActivity.this));
            rvTodayClasses.setAdapter(todayClassAdapter);

            rvWeeklySchedule.setLayoutManager(new LinearLayoutManager(MainActivity.this));
            rvWeeklySchedule.setAdapter(weeklyScheduleAdapter);

            rvSubjectAnalytics.setLayoutManager(new LinearLayoutManager(MainActivity.this));
            rvSubjectAnalytics.setAdapter(subjectAnalyticsAdapter);

            // Sub-Tab Switcher Listeners
            tabToday.setOnClickListener(v -> switchSubTab(SUB_TAB_TODAY));
            tabTimetable.setOnClickListener(v -> switchSubTab(SUB_TAB_TIMETABLE));
            tabAnalytics.setOnClickListener(v -> switchSubTab(SUB_TAB_ANALYTICS));

            // Schedule Day Filters (Strictly Monday-Friday)
            setupDayFilters();

            // FAB Action
            fabAddClass.setOnClickListener(v -> showAddClassBottomSheet(null));

            switchSubTab(currentAttendanceSubTab);
        }

        private void setupDayFilters() {
            TextView[] filterChips = {filterDayAll, filterDayMon, filterDayTue, filterDayWed, filterDayThu, filterDayFri};
            String[] dayNames = {"All", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};

            for (int i = 0; i < filterChips.length; i++) {
                final int index = i;
                filterChips[i].setOnClickListener(v -> {
                    currentScheduleFilterDay = dayNames[index];
                    for (int j = 0; j < filterChips.length; j++) {
                        if (j == index) {
                            filterChips[j].setBackgroundResource(R.drawable.bg_chip_selected);
                            filterChips[j].setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_accent));
                        } else {
                            filterChips[j].setBackgroundResource(R.drawable.bg_chip_unselected);
                            filterChips[j].setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_text_secondary));
                        }
                    }
                    refreshWeeklyScheduleView();
                });
            }
        }

        void switchSubTab(int subTab) {
            currentAttendanceSubTab = subTab;

            // Reset Sub-Tab header buttons
            tabToday.setBackgroundResource(R.drawable.bg_nav_tab_inactive);
            tabToday.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_text_secondary));
            tabTimetable.setBackgroundResource(R.drawable.bg_nav_tab_inactive);
            tabTimetable.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_text_secondary));
            tabAnalytics.setBackgroundResource(R.drawable.bg_nav_tab_inactive);
            tabAnalytics.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_text_secondary));

            viewTodayClasses.setVisibility(View.GONE);
            viewWeeklySchedule.setVisibility(View.GONE);
            viewAnalytics.setVisibility(View.GONE);

            if (subTab == SUB_TAB_TODAY) {
                tabToday.setBackgroundResource(R.drawable.bg_nav_tab_active);
                tabToday.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_on_accent));
                viewTodayClasses.setVisibility(View.VISIBLE);
                refreshTodayClassesView();
            } else if (subTab == SUB_TAB_TIMETABLE) {
                tabTimetable.setBackgroundResource(R.drawable.bg_nav_tab_active);
                tabTimetable.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_on_accent));
                viewWeeklySchedule.setVisibility(View.VISIBLE);
                refreshWeeklyScheduleView();
            } else {
                tabAnalytics.setBackgroundResource(R.drawable.bg_nav_tab_active);
                tabAnalytics.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_on_accent));
                viewAnalytics.setVisibility(View.VISIBLE);
                updateAnalyticsView(currentSubjectStats);
            }
        }

        void showTodayWeekendEmptyState() {
            layoutEmptyToday.setVisibility(View.VISIBLE);
            rvTodayClasses.setVisibility(View.GONE);
            tvEmptyTodayTitle.setText("No classes this weekend");
            tvEmptyTodaySubtitle.setText("Enjoy your rest and mindfulness. Weekends are strictly free of scheduled classes.");
            tvTodaySummaryInfo.setText("Weekend Relaxation • No scheduled lectures");
        }

        void updateTodayClassesUI(List<ClassWithTodayStatus> items, String todayDayName) {
            if (items == null || items.isEmpty()) {
                layoutEmptyToday.setVisibility(View.VISIBLE);
                rvTodayClasses.setVisibility(View.GONE);
                tvEmptyTodayTitle.setText("No classes today (" + todayDayName + ")");
                tvEmptyTodaySubtitle.setText("No recurring classes scheduled for " + todayDayName + ". Configure schedules in the Timetable tab.");
                tvTodaySummaryInfo.setText("0 classes scheduled for " + todayDayName);
            } else {
                layoutEmptyToday.setVisibility(View.GONE);
                rvTodayClasses.setVisibility(View.VISIBLE);
                tvTodaySummaryInfo.setText(items.size() + (items.size() == 1 ? " class" : " classes") + " scheduled for " + todayDayName);
            }
        }

        void updateScheduleUI(List<ClassSchedule> items) {
            if (items == null || items.isEmpty()) {
                layoutEmptySchedule.setVisibility(View.VISIBLE);
                rvWeeklySchedule.setVisibility(View.GONE);
            } else {
                layoutEmptySchedule.setVisibility(View.GONE);
                rvWeeklySchedule.setVisibility(View.VISIBLE);
            }
        }

        void updateAnalyticsView(List<SubjectStats> stats) {
            subjectAnalyticsAdapter.setStats(stats);

            if (stats == null || stats.isEmpty()) {
                layoutEmptyAnalytics.setVisibility(View.VISIBLE);
                rvSubjectAnalytics.setVisibility(View.GONE);
                tvOverallPercentage.setText("—");
                progressOverallAttendance.setProgress(0);
                tvTotalPresent.setText("0 Present");
                tvTotalAbsent.setText("0 Absent");
                tvTotalHoliday.setText("0 Holiday");
            } else {
                layoutEmptyAnalytics.setVisibility(View.GONE);
                rvSubjectAnalytics.setVisibility(View.VISIBLE);

                int totalPresent = 0;
                int totalAbsent = 0;
                int totalHoliday = 0;

                for (SubjectStats s : stats) {
                    totalPresent += s.getPresentCount();
                    totalAbsent += s.getAbsentCount();
                    totalHoliday += s.getHolidayCount();
                }

                int effectiveTotal = totalPresent + totalAbsent;
                double overallPercentage = effectiveTotal > 0 ? (totalPresent * 100.0) / effectiveTotal : 0.0;

                if (effectiveTotal == 0) {
                    tvOverallPercentage.setText("—");
                    progressOverallAttendance.setProgress(0);
                } else {
                    tvOverallPercentage.setText(String.format(Locale.getDefault(), "%.1f%%", overallPercentage));
                    progressOverallAttendance.setProgress((int) Math.round(overallPercentage));

                    int color = overallPercentage >= 75.0 ?
                            ContextCompat.getColor(MainActivity.this, R.color.status_present) :
                            (overallPercentage >= 60.0 ?
                                    ContextCompat.getColor(MainActivity.this, R.color.status_holiday) :
                                    ContextCompat.getColor(MainActivity.this, R.color.status_absent));
                    tvOverallPercentage.setTextColor(color);
                    progressOverallAttendance.setProgressTintList(ColorStateList.valueOf(color));
                }

                tvTotalPresent.setText(totalPresent + " Present");
                tvTotalAbsent.setText(totalAbsent + " Absent");
                tvTotalHoliday.setText(totalHoliday + " Holiday");
            }
        }
    }

    // =========================================================================
    // Attendance Interaction Callbacks
    // =========================================================================

    @Override
    public void onMarkAttendance(ClassSchedule schedule, String status) {
        long todayDate = getStartOfDayMillis(Calendar.getInstance());

        AttendanceRecord record = new AttendanceRecord(
                schedule.getId(),
                schedule.getSubjectName(),
                todayDate,
                status
        );

        AppDatabase.databaseWriteExecutor.execute(() -> {
            database.attendanceRecordDao().insertOrUpdate(record);
        });

        Toast.makeText(this, "Marked " + schedule.getSubjectName() + " as " + status, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEditSchedule(ClassSchedule schedule) {
        showAddClassBottomSheet(schedule);
    }

    @Override
    public void onDeleteSchedule(ClassSchedule schedule) {
        String preview = schedule.getSubjectName() + " (" + schedule.getDayOfWeek() + " • " + schedule.getStartTime() + " - " + schedule.getEndTime() + ")";
        showDeleteConfirmationPopup(
                "REMOVE SCHEDULE",
                "Are you sure you want to remove this class schedule from your timetable?",
                preview,
                () -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        NotificationHelper.cancelClassReminder(MainActivity.this, schedule.getId());
                        database.classScheduleDao().delete(schedule);
                    });
                    Toast.makeText(MainActivity.this, "Class schedule removed", Toast.LENGTH_SHORT).show();
                }
        );
    }

    @Override
    public void onSubjectClick(SubjectStats stats) {
        showSubjectHistoryDialog(stats);
    }

    /**
     * Dialog to Add or Edit a Class Schedule.
     * Crucial Rule: Day selector ONLY allows Monday, Tuesday, Wednesday, Thursday, Friday.
     * Saturday and Sunday are strictly blocked from scheduling.
     */
    private void showAddClassBottomSheet(ClassSchedule existingSchedule) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_ZEN);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_class, null);
        dialog.setContentView(dialogView);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_class_title);
        TextInputEditText etSubject = dialogView.findViewById(R.id.et_class_subject);
        TextInputEditText etRoom = dialogView.findViewById(R.id.et_class_room);

        TextView chipMon = dialogView.findViewById(R.id.chip_day_mon);
        TextView chipTue = dialogView.findViewById(R.id.chip_day_tue);
        TextView chipWed = dialogView.findViewById(R.id.chip_day_wed);
        TextView chipThu = dialogView.findViewById(R.id.chip_day_thu);
        TextView chipFri = dialogView.findViewById(R.id.chip_day_fri);

        LinearLayout btnStartTime = dialogView.findViewById(R.id.btn_select_start_time);
        TextView tvStartTime = dialogView.findViewById(R.id.tv_selected_start_time);
        LinearLayout btnEndTime = dialogView.findViewById(R.id.btn_select_end_time);
        TextView tvEndTime = dialogView.findViewById(R.id.tv_selected_end_time);

        TextView chipTheory = dialogView.findViewById(R.id.chip_type_theory);
        TextView chipLab = dialogView.findViewById(R.id.chip_type_lab);

        TextView chipCr1 = dialogView.findViewById(R.id.chip_cr_1);
        TextView chipCr2 = dialogView.findViewById(R.id.chip_cr_2);
        TextView chipCr3 = dialogView.findViewById(R.id.chip_cr_3);
        TextView chipCr4 = dialogView.findViewById(R.id.chip_cr_4);

        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel_class);
        MaterialButton btnSave = dialogView.findViewById(R.id.btn_save_class);

        // State Holders
        final String[] selectedDay = {existingSchedule != null ? existingSchedule.getDayOfWeek() : "Monday"};
        final String[] selectedType = {existingSchedule != null ? existingSchedule.getClassType() : "Theory"};
        final int[] selectedCredits = {existingSchedule != null ? existingSchedule.getCreditHours() : 3};
        final String[] selectedStartTimeStr = {existingSchedule != null ? existingSchedule.getStartTime() : "09:00 AM"};
        final String[] selectedEndTimeStr = {existingSchedule != null ? existingSchedule.getEndTime() : "10:30 AM"};

        if (existingSchedule != null) {
            tvTitle.setText("EDIT CLASS SCHEDULE");
            etSubject.setText(existingSchedule.getSubjectName());
            etRoom.setText(existingSchedule.getRoomNumber());
            tvStartTime.setText(selectedStartTimeStr[0]);
            tvEndTime.setText(selectedEndTimeStr[0]);
            btnSave.setText("Update Class");
        }

        // Day of Week Selection (Mon-Fri only)
        TextView[] dayChips = {chipMon, chipTue, chipWed, chipThu, chipFri};
        String[] dayNames = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};

        Runnable updateDayChips = () -> {
            for (int i = 0; i < dayChips.length; i++) {
                if (dayNames[i].equalsIgnoreCase(selectedDay[0])) {
                    dayChips[i].setBackgroundResource(R.drawable.bg_chip_selected);
                    dayChips[i].setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_accent));
                } else {
                    dayChips[i].setBackgroundResource(R.drawable.bg_chip_unselected);
                    dayChips[i].setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_text_secondary));
                }
            }
        };
        updateDayChips.run();

        for (int i = 0; i < dayChips.length; i++) {
            final String d = dayNames[i];
            dayChips[i].setOnClickListener(v -> {
                selectedDay[0] = d;
                updateDayChips.run();
            });
        }

        // Type Selection (Theory vs Lab)
        Runnable updateTypeChips = () -> {
            if ("Lab".equalsIgnoreCase(selectedType[0])) {
                chipLab.setBackgroundResource(R.drawable.bg_chip_selected);
                chipLab.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_accent));
                chipTheory.setBackgroundResource(R.drawable.bg_chip_unselected);
                chipTheory.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_text_secondary));
            } else {
                chipTheory.setBackgroundResource(R.drawable.bg_chip_selected);
                chipTheory.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_accent));
                chipLab.setBackgroundResource(R.drawable.bg_chip_unselected);
                chipLab.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_text_secondary));
            }
        };
        updateTypeChips.run();

        chipTheory.setOnClickListener(v -> {
            selectedType[0] = "Theory";
            updateTypeChips.run();
        });
        chipLab.setOnClickListener(v -> {
            selectedType[0] = "Lab";
            updateTypeChips.run();
        });

        // Credit Hours Selection
        TextView[] creditChips = {chipCr1, chipCr2, chipCr3, chipCr4};
        int[] creditValues = {1, 2, 3, 4};

        Runnable updateCreditChips = () -> {
            for (int i = 0; i < creditChips.length; i++) {
                if (creditValues[i] == selectedCredits[0]) {
                    creditChips[i].setBackgroundResource(R.drawable.bg_chip_selected);
                    creditChips[i].setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_accent));
                } else {
                    creditChips[i].setBackgroundResource(R.drawable.bg_chip_unselected);
                    creditChips[i].setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_text_secondary));
                }
            }
        };
        updateCreditChips.run();

        for (int i = 0; i < creditChips.length; i++) {
            final int val = creditValues[i];
            creditChips[i].setOnClickListener(v -> {
                selectedCredits[0] = val;
                updateCreditChips.run();
            });
        }

        // Start Time Picker (Material 3 Time Picker)
        btnStartTime.setOnClickListener(v -> {
            int[] current = NotificationHelper.parseHourAndMinute(selectedStartTimeStr[0]);
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(current[0])
                    .setMinute(current[1])
                    .setTitleText("Select Start Time")
                    .build();

            picker.addOnPositiveButtonClickListener(v1 -> {
                Calendar c = Calendar.getInstance();
                c.set(Calendar.HOUR_OF_DAY, picker.getHour());
                c.set(Calendar.MINUTE, picker.getMinute());
                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                selectedStartTimeStr[0] = sdf.format(c.getTime());
                tvStartTime.setText(selectedStartTimeStr[0]);
            });

            picker.show(getSupportFragmentManager(), "CLASS_START_TIME_PICKER");
        });

        // End Time Picker (Material 3 Time Picker)
        btnEndTime.setOnClickListener(v -> {
            int[] current = NotificationHelper.parseHourAndMinute(selectedEndTimeStr[0]);
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(current[0])
                    .setMinute(current[1])
                    .setTitleText("Select End Time")
                    .build();

            picker.addOnPositiveButtonClickListener(v1 -> {
                Calendar c = Calendar.getInstance();
                c.set(Calendar.HOUR_OF_DAY, picker.getHour());
                c.set(Calendar.MINUTE, picker.getMinute());
                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                selectedEndTimeStr[0] = sdf.format(c.getTime());
                tvEndTime.setText(selectedEndTimeStr[0]);
            });

            picker.show(getSupportFragmentManager(), "CLASS_END_TIME_PICKER");
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String subject = etSubject.getText() != null ? etSubject.getText().toString().trim() : "";
            String room = etRoom.getText() != null ? etRoom.getText().toString().trim() : "";

            if (TextUtils.isEmpty(subject)) {
                etSubject.setError("Please enter a subject name");
                return;
            }

            if (TextUtils.isEmpty(room)) {
                room = "TBA";
            }

            final ClassSchedule scheduleToSave = existingSchedule != null ? existingSchedule : new ClassSchedule();
            scheduleToSave.setSubjectName(subject);
            scheduleToSave.setDayOfWeek(selectedDay[0]);
            scheduleToSave.setStartTime(selectedStartTimeStr[0]);
            scheduleToSave.setEndTime(selectedEndTimeStr[0]);
            scheduleToSave.setCreditHours(selectedCredits[0]);
            scheduleToSave.setClassType(selectedType[0]);
            scheduleToSave.setRoomNumber(room);

            AppDatabase.databaseWriteExecutor.execute(() -> {
                if (existingSchedule == null) {
                    long insertedId = database.classScheduleDao().insert(scheduleToSave);
                    scheduleToSave.setId((int) insertedId);
                } else {
                    database.classScheduleDao().update(scheduleToSave);
                }

                // Schedule 5-minute advance notification alarm using AlarmManager
                NotificationHelper.scheduleClassReminder(MainActivity.this, scheduleToSave);
            });

            dialog.dismiss();
            Toast.makeText(MainActivity.this, "Class scheduled with 5-min reminder 🔔", Toast.LENGTH_SHORT).show();
        });

        dialog.show();

        etSubject.postDelayed(() -> {
            etSubject.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etSubject, InputMethodManager.SHOW_IMPLICIT);
        }, 150);
    }

    /**
     * Dialog showing date-by-date history log for a specific subject.
     */
    private void showSubjectHistoryDialog(SubjectStats stats) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_ZEN);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_attendance_history, null);
        dialog.setContentView(dialogView);

        TextView tvSubject = dialogView.findViewById(R.id.tv_dialog_history_subject);
        TextView tvSubtitle = dialogView.findViewById(R.id.tv_dialog_history_subtitle);
        TextView tvPercentage = dialogView.findViewById(R.id.tv_dialog_history_percentage);
        RecyclerView rvHistory = dialogView.findViewById(R.id.rv_dialog_history);
        TextView tvEmpty = dialogView.findViewById(R.id.tv_empty_history);
        MaterialButton btnClose = dialogView.findViewById(R.id.btn_close_history);

        tvSubject.setText(stats.getSubjectName());
        double percentage = stats.getAttendancePercentage();
        int totalEffective = stats.getEffectiveTotal();

        if (totalEffective == 0) {
            tvPercentage.setText("—");
            tvSubtitle.setText(stats.getHolidayCount() + " Holiday sessions recorded");
        } else {
            tvPercentage.setText(String.format(Locale.getDefault(), "%.1f%%", percentage));
            tvSubtitle.setText(stats.getPresentCount() + " Present • " + stats.getAbsentCount() + " Absent • " + stats.getHolidayCount() + " Holiday");
        }

        AttendanceHistoryAdapter historyAdapter = new AttendanceHistoryAdapter(this);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        rvHistory.setAdapter(historyAdapter);

        database.attendanceRecordDao().getHistoryForSubject(stats.getSubjectName()).observe(this, records -> {
            if (records == null || records.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                rvHistory.setVisibility(View.GONE);
            } else {
                tvEmpty.setVisibility(View.GONE);
                rvHistory.setVisibility(View.VISIBLE);
                historyAdapter.setHistory(records);
            }
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // =========================================================================
    // Task Bottom Sheet Dialog
    // =========================================================================

    private void showAddTaskBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_ZEN);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, null);
        dialog.setContentView(dialogView);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });

        TextInputEditText etTaskInput = dialogView.findViewById(R.id.et_task_input);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel_task);
        MaterialButton btnSubmit = dialogView.findViewById(R.id.btn_submit_task);

        TextView chipNoLimit = dialogView.findViewById(R.id.chip_no_limit);
        TextView chip30m = dialogView.findViewById(R.id.chip_30m);
        TextView chip1h = dialogView.findViewById(R.id.chip_1h);
        TextView chip3h = dialogView.findViewById(R.id.chip_3h);
        TextView chipTodayEod = dialogView.findViewById(R.id.chip_today_eod);
        TextView chipCustom = dialogView.findViewById(R.id.chip_custom_time);

        View layoutTimingSummary = dialogView.findViewById(R.id.layout_timing_summary);
        TextView tvTimingPreview = dialogView.findViewById(R.id.tv_timing_preview);

        final long[] selectedStartTime = {System.currentTimeMillis()};
        final long[] selectedEndTime = {0};
        final int[] selectedDurationMins = {0};

        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

        Runnable updateTimingCard = () -> {
            if (selectedEndTime[0] > 0) {
                layoutTimingSummary.setVisibility(View.VISIBLE);
                String startStr = timeFormat.format(new Date(selectedStartTime[0]));
                String endStr = timeFormat.format(new Date(selectedEndTime[0]));
                String durationStr = selectedDurationMins[0] >= 60 ?
                        (selectedDurationMins[0] / 60) + "h" + (selectedDurationMins[0] % 60 > 0 ? " " + (selectedDurationMins[0] % 60) + "m" : "")
                        : selectedDurationMins[0] + " mins";
                tvTimingPreview.setText("Start: " + startStr + " • Due: " + endStr + " (" + durationStr + ")");
            } else {
                layoutTimingSummary.setVisibility(View.GONE);
            }
        };

        TextView[] allChips = {chipNoLimit, chip30m, chip1h, chip3h, chipTodayEod, chipCustom};

        java.util.function.Consumer<TextView> selectChip = (selected) -> {
            for (TextView chip : allChips) {
                if (chip == selected) {
                    chip.setBackgroundResource(R.drawable.bg_chip_selected);
                    chip.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_accent));
                } else {
                    chip.setBackgroundResource(R.drawable.bg_chip_unselected);
                    chip.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_text_secondary));
                }
            }
        };

        chipNoLimit.setOnClickListener(v -> {
            selectChip.accept(chipNoLimit);
            selectedStartTime[0] = 0;
            selectedEndTime[0] = 0;
            selectedDurationMins[0] = 0;
            updateTimingCard.run();
        });

        chip30m.setOnClickListener(v -> {
            selectChip.accept(chip30m);
            selectedStartTime[0] = System.currentTimeMillis();
            selectedDurationMins[0] = 30;
            selectedEndTime[0] = selectedStartTime[0] + (30L * 60 * 1000);
            updateTimingCard.run();
        });

        chip1h.setOnClickListener(v -> {
            selectChip.accept(chip1h);
            selectedStartTime[0] = System.currentTimeMillis();
            selectedDurationMins[0] = 60;
            selectedEndTime[0] = selectedStartTime[0] + (60L * 60 * 1000);
            updateTimingCard.run();
        });

        // 3 Hours Preset
        chip3h.setOnClickListener(v -> {
            selectChip.accept(chip3h);
            selectedStartTime[0] = System.currentTimeMillis();
            selectedDurationMins[0] = 180;
            selectedEndTime[0] = selectedStartTime[0] + (180L * 60 * 1000);
            updateTimingCard.run();
        });

        chipTodayEod.setOnClickListener(v -> {
            selectChip.accept(chipTodayEod);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 18);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
            selectedStartTime[0] = System.currentTimeMillis();
            selectedEndTime[0] = cal.getTimeInMillis();
            selectedDurationMins[0] = (int) ((selectedEndTime[0] - selectedStartTime[0]) / (60 * 1000));
            updateTimingCard.run();
        });

        // Custom Time Picker (Material 3 Time Picker)
        chipCustom.setOnClickListener(v -> {
            Calendar currentCal = Calendar.getInstance();
            int defaultHour = (currentCal.get(Calendar.HOUR_OF_DAY) + 1) % 24;
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(defaultHour)
                    .setMinute(0)
                    .setTitleText("Select Due Time")
                    .build();

            picker.addOnPositiveButtonClickListener(v1 -> {
                selectChip.accept(chipCustom);
                Calendar pickedCal = Calendar.getInstance();
                pickedCal.set(Calendar.HOUR_OF_DAY, picker.getHour());
                pickedCal.set(Calendar.MINUTE, picker.getMinute());
                pickedCal.set(Calendar.SECOND, 0);

                if (pickedCal.getTimeInMillis() <= System.currentTimeMillis()) {
                    pickedCal.add(Calendar.DAY_OF_YEAR, 1);
                }

                selectedStartTime[0] = System.currentTimeMillis();
                selectedEndTime[0] = pickedCal.getTimeInMillis();
                selectedDurationMins[0] = (int) ((selectedEndTime[0] - selectedStartTime[0]) / (60 * 1000));
                updateTimingCard.run();
            });

            picker.show(getSupportFragmentManager(), "TASK_CUSTOM_TIME_PICKER");
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSubmit.setOnClickListener(v -> {
            String taskDescription = etTaskInput.getText() != null ?
                    etTaskInput.getText().toString().trim() : "";

            if (TextUtils.isEmpty(taskDescription)) {
                etTaskInput.setError("Please enter a task description");
                return;
            }

            Task newTask = new Task(
                    taskDescription,
                    false,
                    System.currentTimeMillis(),
                    selectedStartTime[0],
                    selectedEndTime[0],
                    selectedDurationMins[0]
            );

            AppDatabase.databaseWriteExecutor.execute(() -> {
                long insertedId = database.taskDao().insert(newTask);
                newTask.setId((int) insertedId);

                if (newTask.getEndTime() > System.currentTimeMillis()) {
                    NotificationHelper.scheduleTaskReminder(MainActivity.this, newTask);
                }
            });

            dialog.dismiss();
            Toast.makeText(MainActivity.this, "Task added" + (newTask.hasTimeWindow() ? " with reminder 🔔" : ""), Toast.LENGTH_SHORT).show();
        });

        dialog.show();

        etTaskInput.postDelayed(() -> {
            etTaskInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etTaskInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 150);
    }

    // =========================================================================
    // Note Interaction Callbacks
    // =========================================================================

    @Override
    public void onNoteClick(Note note) {
        Intent intent = new Intent(this, NoteEditorActivity.class);
        intent.putExtra(NoteEditorActivity.EXTRA_NOTE, note);
        startActivity(intent);
    }

    @Override
    public void onNoteDelete(Note note) {
        String preview = note.getTitle() != null && !note.getTitle().trim().isEmpty() ? note.getTitle() : "Untitled Note";
        showDeleteConfirmationPopup(
                "DELETE NOTE",
                "Are you sure you want to permanently delete this note? This action cannot be undone.",
                preview,
                () -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        database.noteDao().delete(note);
                    });
                    Toast.makeText(MainActivity.this, "Note deleted", Toast.LENGTH_SHORT).show();
                }
        );
    }

    // =========================================================================
    // Task Interaction Callbacks
    // =========================================================================

    @Override
    public void onTaskToggle(Task task, boolean isCompleted) {
        task.setCompleted(isCompleted);
        task.setTimestamp(System.currentTimeMillis());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            database.taskDao().update(task);
            if (isCompleted) {
                NotificationHelper.cancelTaskReminder(MainActivity.this, task.getId());
            } else if (task.getEndTime() > System.currentTimeMillis()) {
                NotificationHelper.scheduleTaskReminder(MainActivity.this, task);
            }
        });
    }

    @Override
    public void onTaskDelete(Task task) {
        String preview = task.getDescription() != null ? task.getDescription() : "Task";
        showDeleteConfirmationPopup(
                "DELETE TASK",
                "Are you sure you want to delete this task?",
                preview,
                () -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        NotificationHelper.cancelTaskReminder(MainActivity.this, task.getId());
                        database.taskDao().delete(task);
                    });
                    Toast.makeText(MainActivity.this, "Task deleted", Toast.LENGTH_SHORT).show();
                }
        );
    }

    /**
     * Shows a customized minimalist pop-up screen modal for deletion confirmation.
     */
    public void showDeleteConfirmationPopup(String title, String message, String itemPreview, Runnable onConfirm) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirm_delete);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle = dialog.findViewById(R.id.tv_delete_popup_title);
        TextView tvMsg = dialog.findViewById(R.id.tv_delete_popup_message);
        TextView tvPreview = dialog.findViewById(R.id.tv_delete_item_preview);
        MaterialButton btnCancel = dialog.findViewById(R.id.btn_popup_delete_cancel);
        MaterialButton btnConfirm = dialog.findViewById(R.id.btn_popup_delete_confirm);

        if (title != null) tvTitle.setText(title);
        if (message != null) tvMsg.setText(message);
        if (itemPreview != null && !itemPreview.trim().isEmpty()) {
            tvPreview.setVisibility(View.VISIBLE);
            tvPreview.setText(itemPreview);
        } else {
            tvPreview.setVisibility(View.GONE);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            if (onConfirm != null) {
                onConfirm.run();
            }
        });

        dialog.show();
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    public static long getStartOfDayMillis(Calendar cal) {
        Calendar c = (Calendar) cal.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static String getCurrentDayOfWeekName() {
        Calendar cal = Calendar.getInstance();
        switch (cal.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY: return "Monday";
            case Calendar.TUESDAY: return "Tuesday";
            case Calendar.WEDNESDAY: return "Wednesday";
            case Calendar.THURSDAY: return "Thursday";
            case Calendar.FRIDAY: return "Friday";
            case Calendar.SATURDAY: return "Saturday";
            case Calendar.SUNDAY: return "Sunday";
            default: return "Monday";
        }
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}

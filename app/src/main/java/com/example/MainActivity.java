package com.example;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.adapter.NoteAdapter;
import com.example.adapter.SubjectAnalyticsAdapter;
import com.example.adapter.TaskAdapter;
import com.example.adapter.WeeklyScheduleAdapter;
import com.example.database.AppDatabase;
import com.example.model.AttendanceHistory;
import com.example.model.ClassSchedule;
import com.example.model.ClassWithTodayStatus;
import com.example.model.Note;
import com.example.model.SubjectStats;
import com.example.model.Task;
import com.example.model.UserProfile;
import com.example.util.NotificationHelper;
import com.example.util.SecureProfileStore;
import com.example.util.ThemeHelper;
import com.example.util.UpdateManager;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MainActivity is the core screen of the ZEN minimalist dark-themed productivity application.
 *
 * Modules:
 * 1. Notes (Zen minimalist note taking)
 * 2. Tasks (Scheduled task management with precise alarms)
 * 3. Classes Dashboard & Attendance Tracker (Immediate focus, daily schedule, Mon-Fri config, analytics)
 */
public class MainActivity extends AppCompatActivity implements
        NoteAdapter.OnNoteClickListener,
        TaskAdapter.OnTaskActionListener,
        WeeklyScheduleAdapter.OnScheduleActionListener,
        SubjectAnalyticsAdapter.OnSubjectClickListener {

    private static final String PREFS_ZEN = "zen_prefs";

    public static final int TAB_NOTES = 0;
    public static final int TAB_TASKS = 1;
    public static final int TAB_ATTENDANCE = 2;
    private int currentTab = TAB_ATTENDANCE;

    // Attendance Sub-Tab States
    public static final int SUB_TAB_TODAY = 0;
    public static final int SUB_TAB_TIMETABLE = 1;
    public static final int SUB_TAB_ANALYTICS = 2;
    private int currentAttendanceSubTab = SUB_TAB_TODAY;

    // View References - Header
    private TextView tvHeaderTitle;
    private TextView tvHeaderSubtitle;
    private TextView tvItemCountBadge;
    private View dotLocalPulse;
    private ObjectAnimator pulseAnimator;
    private View btnHeaderProfile;
    private TextView tvHeaderProfileInitial;
    private ImageView ivHeaderProfilePhoto;
    private EditText etSearch;
    private ImageView btnClearSearch;
    private LinearLayout searchBarContainer;

    // View References - Navigation & Pager
    private ViewPager2 viewPager;
    private View bottomNavContainer;
    private View pillNavNotes;
    private View pillNavTasks;
    private View pillNavAttendance;
    private View btnNavNotes;
    private View btnNavTasks;
    private View btnNavAttendance;
    private ImageView ivNavNotesIcon;
    private ImageView ivNavTasksIcon;
    private ImageView ivNavAttendanceIcon;
    private TextView tvNavNotesText;
    private TextView tvNavTasksText;
    private TextView tvNavAttendanceText;
    private FloatingActionButton fabAdd;

    // Page Views & Adapters - Notes & Tasks
    private RecyclerView rvNotes;
    private RecyclerView rvTasks;
    private View layoutEmptyNotes;
    private View layoutEmptyTasks;
    private NoteAdapter noteAdapter;
    private TaskAdapter taskAdapter;

    // Page Views & Adapters - Attendance Tracker & Dashboard
    private AttendanceViewHolder attendanceViewHolder;
    private WeeklyScheduleAdapter weeklyScheduleAdapter;
    private SubjectAnalyticsAdapter subjectAnalyticsAdapter;

    private MainPagerAdapter pagerAdapter;

    // Cached Data
    private List<Note> currentNotes = new ArrayList<>();
    private List<Task> currentTasks = new ArrayList<>();
    private List<Task> allDashboardTasks = new ArrayList<>();
    private List<ClassSchedule> currentSchedules = new ArrayList<>();
    private List<AttendanceHistory> todayAttendanceRecords = new ArrayList<>();
    private List<SubjectStats> currentSubjectStats = new ArrayList<>();
    private String currentScheduleFilterDay = "All";
    private int focusedScheduleId = -1;
    private UserProfile currentUserProfile;
    private Bitmap currentAvatarBitmap;

    // Database Reference & LiveData Observers
    private AppDatabase database;
    private LiveData<List<Note>> activeNotesLiveData;
    private LiveData<List<Task>> activeTasksLiveData;
    private boolean attendanceObserversAttached = false;
    private final Observer<List<Note>> notesObserver = this::updateNotesList;
    private final Observer<List<Task>> tasksObserver = this::updateTasksList;

    // Notification Permission Launcher (Android 13+)
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    NotificationHelper.createNotificationChannel(this);
                }
            });

    // Profile Setup Launcher
    private final ActivityResultLauncher<Intent> profileSetupLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                refreshProfileHeader();
                if (attendanceViewHolder != null) {
                    attendanceViewHolder.updateGreeting();
                    attendanceViewHolder.updateQuickOverview(currentSubjectStats, allDashboardTasks);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Handle splash screen transition using AndroidX SplashScreen API
        SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);

        // Check if first-launch onboarding is required before inflating the main dashboard
        if (checkAndLaunchOnboarding()) {
            return;
        }

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

        int storedAccent = ThemeHelper.getAccentColor(this);
        applyThemeAccent(storedAccent);

        handleIntent(getIntent());

        observeDatabase("");
        observeAttendanceData();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentTab == TAB_ATTENDANCE && currentAttendanceSubTab != SUB_TAB_TODAY && attendanceViewHolder != null) {
                    attendanceViewHolder.switchSubTab(SUB_TAB_TODAY);
                } else if (currentTab != TAB_ATTENDANCE && viewPager != null) {
                    viewPager.setCurrentItem(TAB_ATTENDANCE, true);
                } else {
                    finish();
                }
            }
        });

        // Automated background check for in-app updates on app launch
        if (UpdateManager.isAutoUpdateEnabled(this)) {
            UpdateManager.checkForUpdates(this);
        }

        // Schedule nightly attendance auto-marker via WorkManager (runs around 11:55 PM daily)
        com.example.worker.DailyAttendanceWorker.scheduleDailyAutoMarker(this);
    }

    /**
     * Checks if the user is launching the app for the first time following the v9.3
     * database architecture migration. If so, redirects to the dedicated OnboardingActivity.
     *
     * @return true if onboarding was launched and MainActivity should terminate.
     */
    private boolean checkAndLaunchOnboarding() {
        SharedPreferences prefs = getSharedPreferences(PREFS_ZEN, Context.MODE_PRIVATE);
        SharedPreferences defaultPrefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        boolean isFirstLaunch = prefs.getBoolean("is_first_launch_v9_3_migration", true)
                && defaultPrefs.getBoolean("is_first_launch_v9_3_migration", true);

        if (isFirstLaunch) {
            Intent intent = new Intent(this, OnboardingActivity.class);
            startActivity(intent);
            finish();
            return true;
        }

        if (!SecureProfileStore.hasSeenProfileSetup(this)) {
            Intent intent = new Intent(this, ProfileSetupActivity.class);
            intent.putExtra(ProfileSetupActivity.EXTRA_IS_FIRST_LAUNCH, true);
            startActivity(intent);
            finish();
            return true;
        }
        return false;
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
            if ("notes".equalsIgnoreCase(tab)) {
                if (viewPager != null) viewPager.setCurrentItem(TAB_NOTES, true);
            } else if ("tasks".equalsIgnoreCase(tab)) {
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

    // Easter Egg Watermark: 7-tap detection within 3 seconds
    private int easterEggTapCount = 0;
    private long easterEggFirstTapTime = 0;
    private static final int REQUIRED_EASTER_EGG_TAPS = 7;
    private static final long EASTER_EGG_TIME_WINDOW_MS = 3000L;

    // Touch & Gesture Disambiguation: Prevents vertical list scrolling from triggering ViewPager2 page shifts
    private float touchDownX = 0f;
    private float touchDownY = 0f;
    private boolean isGestureLocked = false;
    private int scaledTouchSlop = 0;

    private final RecyclerView.OnScrollListener verticalScrollStateListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            if (viewPager == null) return;
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING) {
                viewPager.setUserInputEnabled(false);
            } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                boolean allowSwipe = (currentTab != TAB_ATTENDANCE) || (currentAttendanceSubTab == SUB_TAB_TODAY);
                viewPager.setUserInputEnabled(allowSwipe);
            }
        }
    };

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (scaledTouchSlop == 0) {
            scaledTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        }

        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            touchDownX = ev.getRawX();
            touchDownY = ev.getRawY();
            isGestureLocked = false;
        } else if (action == MotionEvent.ACTION_MOVE && !isGestureLocked) {
            float dx = Math.abs(ev.getRawX() - touchDownX);
            float dy = Math.abs(ev.getRawY() - touchDownY);

            // React quickly as soon as motion starts (fraction of standard touch slop)
            if (dx > scaledTouchSlop * 0.35f || dy > scaledTouchSlop * 0.35f) {
                isGestureLocked = true;
                // If the gesture has vertical intent (scrolling up/down through notes, tasks, or classes)
                if (dy >= dx * 0.55f) {
                    if (viewPager != null) {
                        viewPager.setUserInputEnabled(false);
                        viewPager.requestDisallowInterceptTouchEvent(true);
                    }
                } else if (dx > dy * 1.8f) {
                    // Deliberate horizontal swipe across screens (disabled when nav bar is hidden on sub-views)
                    boolean allowSwipe = (currentTab != TAB_ATTENDANCE) || (currentAttendanceSubTab == SUB_TAB_TODAY);
                    if (viewPager != null) {
                        viewPager.setUserInputEnabled(allowSwipe);
                    }
                }
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            isGestureLocked = false;
            boolean allowSwipe = (currentTab != TAB_ATTENDANCE) || (currentAttendanceSubTab == SUB_TAB_TODAY);
            // Only re-enable horizontal paging if no vertical list is actively flinging/scrolling
            if (viewPager != null && !isAnyListActivelyScrolling()) {
                viewPager.setUserInputEnabled(allowSwipe);
            }
        }

        return super.dispatchTouchEvent(ev);
    }

    private boolean isAnyListActivelyScrolling() {
        if (rvNotes != null && rvNotes.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) return true;
        if (rvTasks != null && rvTasks.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) return true;
        if (attendanceViewHolder != null && attendanceViewHolder.isScrolling()) return true;
        return false;
    }

    private void bindViews() {
        tvHeaderTitle = findViewById(R.id.tv_header_title);
        tvHeaderSubtitle = findViewById(R.id.tv_header_subtitle);
        tvItemCountBadge = findViewById(R.id.tv_item_count_badge);
        dotLocalPulse = findViewById(R.id.dot_local_pulse);
        btnHeaderProfile = findViewById(R.id.btn_header_profile);
        tvHeaderProfileInitial = findViewById(R.id.tv_header_profile_initial);
        ivHeaderProfilePhoto = findViewById(R.id.iv_header_profile_photo);
        if (ivHeaderProfilePhoto != null) {
            ivHeaderProfilePhoto.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            ivHeaderProfilePhoto.setClipToOutline(true);
        }
        searchBarContainer = findViewById(R.id.search_bar_container);
        etSearch = findViewById(R.id.et_search);
        btnClearSearch = findViewById(R.id.btn_clear_search);

        viewPager = findViewById(R.id.view_pager);
        bottomNavContainer = findViewById(R.id.bottom_nav_container);
        pillNavNotes = findViewById(R.id.pill_nav_notes);
        pillNavTasks = findViewById(R.id.pill_nav_tasks);
        pillNavAttendance = findViewById(R.id.pill_nav_attendance);
        btnNavNotes = findViewById(R.id.btn_nav_notes);
        btnNavTasks = findViewById(R.id.btn_nav_tasks);
        btnNavAttendance = findViewById(R.id.btn_nav_attendance);
        ivNavNotesIcon = findViewById(R.id.iv_nav_notes_icon);
        ivNavTasksIcon = findViewById(R.id.iv_nav_tasks_icon);
        ivNavAttendanceIcon = findViewById(R.id.iv_nav_attendance_icon);
        tvNavNotesText = findViewById(R.id.tv_nav_notes_text);
        tvNavTasksText = findViewById(R.id.tv_nav_tasks_text);
        tvNavAttendanceText = findViewById(R.id.tv_nav_attendance_text);
        fabAdd = findViewById(R.id.fab_add);

        // Attach Easter Egg 7-tap listener on Header Title & LOCAL Pill
        if (tvHeaderTitle != null) {
            tvHeaderTitle.setOnClickListener(v -> handleEasterEggTap());
        }
        if (tvHeaderSubtitle != null) {
            tvHeaderSubtitle.setOnClickListener(v -> handleEasterEggTap());
        }

        View btnSettings = findViewById(R.id.btn_header_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, BackupActivity.class);
                startActivity(intent);
            });
        }

        if (btnHeaderProfile != null) {
            btnHeaderProfile.setOnClickListener(v -> openProfileSetup());
        }

        if (dotLocalPulse != null) {
            pulseAnimator = ObjectAnimator.ofFloat(dotLocalPulse, View.ALPHA, 1.0f, 0.35f);
            pulseAnimator.setDuration(900);
            pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
            pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnimator.start();
        }

        refreshProfileHeader();
    }

    private void refreshProfileHeader() {
        currentUserProfile = SecureProfileStore.loadProfile(this);
        currentAvatarBitmap = SecureProfileStore.loadAvatarBitmap(this);

        if (ivHeaderProfilePhoto != null && tvHeaderProfileInitial != null) {
            if (currentAvatarBitmap != null) {
                ivHeaderProfilePhoto.setImageBitmap(currentAvatarBitmap);
                ivHeaderProfilePhoto.setVisibility(View.VISIBLE);
                tvHeaderProfileInitial.setVisibility(View.GONE);
            } else {
                ivHeaderProfilePhoto.setImageDrawable(null);
                ivHeaderProfilePhoto.setVisibility(View.GONE);
                tvHeaderProfileInitial.setVisibility(View.VISIBLE);
                tvHeaderProfileInitial.setText(currentUserProfile.getInitial());
            }
        }
    }

    private void openProfileSetup() {
        Intent intent = new Intent(this, ProfileSetupActivity.class);
        intent.putExtra(ProfileSetupActivity.EXTRA_IS_FIRST_LAUNCH, false);
        profileSetupLauncher.launch(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int storedAccent = ThemeHelper.getAccentColor(this);
        applyThemeAccent(storedAccent);
        refreshProfileHeader();
        if (attendanceViewHolder != null) {
            attendanceViewHolder.updateGreeting();
            refreshTodayClassesView();
            attendanceViewHolder.updateQuickOverview(currentSubjectStats, allDashboardTasks);
            attendanceViewHolder.updateAnalyticsView(currentSubjectStats);
        }
    }

    @Override
    protected void onDestroy() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        super.onDestroy();
    }

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

    private void setupAdapters() {
        noteAdapter = new NoteAdapter(this);
        taskAdapter = new TaskAdapter(this);
        weeklyScheduleAdapter = new WeeklyScheduleAdapter(this, this);
        subjectAnalyticsAdapter = new SubjectAnalyticsAdapter(this, this);
    }

    private void setupViewPager() {
        pagerAdapter = new MainPagerAdapter();
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(2);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                updatePillsOnScroll(position, positionOffset);
            }

            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                onTabChanged(position);
            }
        });

        viewPager.setCurrentItem(TAB_ATTENDANCE, false);
        onTabChanged(TAB_ATTENDANCE);
    }

    private void setupNavigation() {
        int accentColor = ThemeHelper.getAccentColor(this);
        if (pillNavNotes != null) pillNavNotes.setBackground(ThemeHelper.createActiveTabDrawable(accentColor));
        if (pillNavTasks != null) pillNavTasks.setBackground(ThemeHelper.createActiveTabDrawable(accentColor));
        if (pillNavAttendance != null) pillNavAttendance.setBackground(ThemeHelper.createActiveTabDrawable(accentColor));

        btnNavNotes.setOnClickListener(v -> selectTabDirectly(TAB_NOTES));
        btnNavTasks.setOnClickListener(v -> selectTabDirectly(TAB_TASKS));
        btnNavAttendance.setOnClickListener(v -> selectTabDirectly(TAB_ATTENDANCE));

        updatePillStates(currentTab, false);
    }

    private void selectTabDirectly(int tab) {
        if (viewPager != null && currentTab != tab) {
            viewPager.setCurrentItem(tab, false);
        }
    }

    private void updatePillsOnScroll(int position, float positionOffset) {
        if (viewPager == null || viewPager.getScrollState() == ViewPager2.SCROLL_STATE_IDLE) {
            return;
        }

        float alpha0 = 0f, alpha1 = 0f, alpha2 = 0f;
        if (position == 0) {
            alpha0 = 1.0f - positionOffset;
            alpha1 = positionOffset;
            alpha2 = 0f;
        } else if (position == 1) {
            alpha0 = 0f;
            alpha1 = 1.0f - positionOffset;
            alpha2 = positionOffset;
        } else if (position >= 2) {
            alpha2 = 1.0f;
        }

        applyPillProgress(pillNavNotes, ivNavNotesIcon, tvNavNotesText, alpha0);
        applyPillProgress(pillNavTasks, ivNavTasksIcon, tvNavTasksText, alpha1);
        applyPillProgress(pillNavAttendance, ivNavAttendanceIcon, tvNavAttendanceText, alpha2);
    }

    private void applyPillProgress(View pill, ImageView icon, TextView text, float progress) {
        if (pill == null) return;
        pill.setAlpha(progress);
        float scale = 0.88f + 0.12f * progress;
        pill.setScaleX(scale);
        pill.setScaleY(scale);

        boolean isActive = progress >= 0.5f;
        int accentColor = ThemeHelper.getAccentColor(this);
        int onAccentColor = ThemeHelper.getOnAccentColor(accentColor);
        int inactiveColor = ContextCompat.getColor(this, R.color.zen_text_secondary);
        updateTabItemVisuals(icon, text, isActive, onAccentColor, inactiveColor);
    }

    private void updatePillStates(int activeTab, boolean animate) {
        animateTabPill(pillNavNotes, ivNavNotesIcon, tvNavNotesText, activeTab == TAB_NOTES, animate);
        animateTabPill(pillNavTasks, ivNavTasksIcon, tvNavTasksText, activeTab == TAB_TASKS, animate);
        animateTabPill(pillNavAttendance, ivNavAttendanceIcon, tvNavAttendanceText, activeTab == TAB_ATTENDANCE, animate);
    }

    private void animateTabPill(View pill, ImageView icon, TextView text, boolean isActive, boolean animate) {
        if (pill == null) return;
        float targetAlpha = isActive ? 1.0f : 0.0f;
        float targetScale = isActive ? 1.0f : 0.88f;

        if (animate) {
            pill.animate()
                    .alpha(targetAlpha)
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        } else {
            pill.animate().cancel();
            pill.setAlpha(targetAlpha);
            pill.setScaleX(targetScale);
            pill.setScaleY(targetScale);
        }

        int accentColor = ThemeHelper.getAccentColor(this);
        int onAccentColor = ThemeHelper.getOnAccentColor(accentColor);
        int inactiveColor = ContextCompat.getColor(this, R.color.zen_text_secondary);
        updateTabItemVisuals(icon, text, isActive, onAccentColor, inactiveColor);
    }

    private void onTabChanged(int tab) {
        currentTab = tab;
        String query = etSearch.getText().toString().trim();

        int accentColor = ThemeHelper.getAccentColor(this);
        updatePillStates(tab, true);

        tvHeaderTitle.setText("ZEN");

        if (tab == TAB_NOTES) {
            tvItemCountBadge.setVisibility(View.VISIBLE);
            searchBarContainer.setVisibility(View.VISIBLE);
            etSearch.setHint(R.string.search_notes_hint);
            fabAdd.setVisibility(View.VISIBLE);
            fabAdd.setContentDescription("Add Note");
            updateNotesBadge();
        } else if (tab == TAB_TASKS) {
            tvItemCountBadge.setVisibility(View.VISIBLE);
            searchBarContainer.setVisibility(View.VISIBLE);
            etSearch.setHint(R.string.search_tasks_hint);
            fabAdd.setVisibility(View.VISIBLE);
            fabAdd.setContentDescription("Add Task");
            updateTasksBadge();
        } else {
            tvItemCountBadge.setVisibility(View.GONE);
            searchBarContainer.setVisibility(View.GONE);
            fabAdd.setVisibility(View.GONE); // Classes page uses specialized FAB / actions
            if (attendanceViewHolder != null) {
                attendanceViewHolder.updateGreeting();
            }
        }

        boolean showBottomNav = (tab != TAB_ATTENDANCE) || (currentAttendanceSubTab == SUB_TAB_TODAY);
        if (bottomNavContainer != null) {
            bottomNavContainer.setVisibility(showBottomNav ? View.VISIBLE : View.GONE);
        }
        if (viewPager != null) {
            viewPager.setUserInputEnabled(showBottomNav);
        }

        ThemeHelper.applyAccentToFab(fabAdd, accentColor);
        ThemeHelper.applyAccentToBadge(tvItemCountBadge, accentColor);

        observeDatabase(query);
    }

    private void updateTabItemVisuals(ImageView icon, TextView text, boolean isActive, int activeColor, int inactiveColor) {
        if (icon != null) {
            icon.setColorFilter(isActive ? activeColor : inactiveColor);
        }
        if (text != null) {
            text.setTextColor(isActive ? activeColor : inactiveColor);
            text.setTypeface(null, isActive ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    /**
     * Dynamically applies the chosen accent color across the app UI elements.
     */
    public void applyThemeAccent(int accentColor) {
        ThemeHelper.applyAccentToFab(fabAdd, accentColor);
        ThemeHelper.applyAccentToBadge(tvItemCountBadge, accentColor);

        if (pillNavNotes != null) {
            pillNavNotes.setBackground(ThemeHelper.createActiveTabDrawable(accentColor));
        }
        if (pillNavTasks != null) {
            pillNavTasks.setBackground(ThemeHelper.createActiveTabDrawable(accentColor));
        }
        if (pillNavAttendance != null) {
            pillNavAttendance.setBackground(ThemeHelper.createActiveTabDrawable(accentColor));
        }

        updatePillStates(currentTab, false);

        if (attendanceViewHolder != null) {
            attendanceViewHolder.refreshThemeAccent(accentColor);
        }
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
            if (activeNotesLiveData != null) {
                activeNotesLiveData.removeObserver(notesObserver);
            }
            activeNotesLiveData = TextUtils.isEmpty(query)
                    ? database.noteDao().getAllNotes()
                    : database.noteDao().searchNotes(query);
            activeNotesLiveData.observe(this, notesObserver);
        } else if (currentTab == TAB_TASKS) {
            if (activeTasksLiveData != null) {
                activeTasksLiveData.removeObserver(tasksObserver);
            }
            activeTasksLiveData = TextUtils.isEmpty(query)
                    ? database.taskDao().getAllTasks()
                    : database.taskDao().searchTasks(query);
            activeTasksLiveData.observe(this, tasksObserver);
        } else {
            observeAttendanceData();
        }
    }

    private void observeAttendanceData() {
        if (attendanceObserversAttached) {
            refreshTodayClassesView();
            refreshWeeklyScheduleView();
            if (attendanceViewHolder != null) {
                attendanceViewHolder.updateAnalyticsView(currentSubjectStats);
                attendanceViewHolder.updateQuickOverview(currentSubjectStats, allDashboardTasks);
            }
            return;
        }
        attendanceObserversAttached = true;

        long todayStartOfDay = getStartOfDayMillis(Calendar.getInstance());

        // Observe Today's Attendance Records
        database.attendanceHistoryDao().getRecordsForDate(todayStartOfDay).observe(this, records -> {
            this.todayAttendanceRecords = records != null ? records : new ArrayList<>();
            refreshTodayClassesView();
        });

        // Observe All Class Schedules
        database.classScheduleDao().getAllSchedules().observe(this, schedules -> {
            this.currentSchedules = schedules != null ? new ArrayList<>(schedules) : new ArrayList<>();
            Collections.sort(this.currentSchedules, ClassSchedule.TIMETABLE_COMPARATOR);
            refreshTodayClassesView();
            refreshWeeklyScheduleView();
            if (attendanceViewHolder != null) {
                attendanceViewHolder.updateAnalyticsView(currentSubjectStats);
            }
        });

        // Observe Subject Analytics with exact SQL percentage math
        database.attendanceHistoryDao().getAllSubjectStats().observe(this, stats -> {
            this.currentSubjectStats = stats != null ? stats : new ArrayList<>();
            if (attendanceViewHolder != null) {
                attendanceViewHolder.updateAnalyticsView(currentSubjectStats);
                attendanceViewHolder.updateQuickOverview(currentSubjectStats, allDashboardTasks);
            }
        });

        // Observe All Tasks continuously for Dashboard Quick Overview priority task
        database.taskDao().getAllTasks().observe(this, tasks -> {
            this.allDashboardTasks = tasks != null ? tasks : new ArrayList<>();
            if (attendanceViewHolder != null) {
                attendanceViewHolder.updateQuickOverview(currentSubjectStats, allDashboardTasks);
            }
        });
    }

    private void refreshTodayClassesView() {
        if (attendanceViewHolder == null) return;

        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        boolean isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY);
        String todayDayName = getCurrentDayOfWeekName();

        // Count distinct enrolled courses across weekly timetable
        Set<String> distinctCourses = new HashSet<>();
        for (ClassSchedule cs : currentSchedules) {
            if (cs.getSubjectName() != null && !cs.getSubjectName().trim().isEmpty()) {
                distinctCourses.add(cs.getSubjectName().trim().toLowerCase(Locale.ROOT));
            }
        }
        int enrolledCount = distinctCourses.size();

        if (isWeekend) {
            attendanceViewHolder.updateDashboardTodayUI(new ArrayList<>(), todayDayName, true, enrolledCount);
            return;
        }

        List<ClassSchedule> todaySchedules = new ArrayList<>();
        for (ClassSchedule cs : currentSchedules) {
            if (todayDayName.equalsIgnoreCase(cs.getDayOfWeek())) {
                todaySchedules.add(cs);
            }
        }
        Collections.sort(todaySchedules, ClassSchedule.CHRONOLOGICAL_COMPARATOR);

        Map<String, AttendanceHistory> recordMap = new HashMap<>();
        for (AttendanceHistory ar : todayAttendanceRecords) {
            if (ar.getSubjectName() != null) {
                recordMap.put(ar.getSubjectName().trim().toLowerCase(Locale.ROOT), ar);
            }
        }

        List<ClassWithTodayStatus> compositeList = new ArrayList<>();
        for (ClassSchedule cs : todaySchedules) {
            String key = cs.getSubjectName() != null ? cs.getSubjectName().trim().toLowerCase(Locale.ROOT) : "";
            compositeList.add(new ClassWithTodayStatus(cs, recordMap.get(key)));
        }

        attendanceViewHolder.updateDashboardTodayUI(compositeList, todayDayName, false, enrolledCount);
    }

    private void refreshWeeklyScheduleView() {
        if (attendanceViewHolder == null) return;

        List<ClassSchedule> filteredList = new ArrayList<>();
        if ("All".equalsIgnoreCase(currentScheduleFilterDay)) {
            filteredList.addAll(currentSchedules);
            Collections.sort(filteredList, ClassSchedule.TIMETABLE_COMPARATOR);
        } else {
            for (ClassSchedule cs : currentSchedules) {
                if (currentScheduleFilterDay.equalsIgnoreCase(cs.getDayOfWeek())) {
                    filteredList.add(cs);
                }
            }
            Collections.sort(filteredList, ClassSchedule.CHRONOLOGICAL_COMPARATOR);
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
                rvNotes.clearOnScrollListeners();
                rvNotes.addOnScrollListener(verticalScrollStateListener);
                updateNotesList(currentNotes);
            } else if (holder instanceof TasksViewHolder) {
                TasksViewHolder tHolder = (TasksViewHolder) holder;
                rvTasks = tHolder.rvTasks;
                layoutEmptyTasks = tHolder.layoutEmptyTasks;

                rvTasks.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                rvTasks.setAdapter(taskAdapter);
                rvTasks.clearOnScrollListeners();
                rvTasks.addOnScrollListener(verticalScrollStateListener);
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
    // Attendance Tracker Dashboard ViewHolder & Sub-Tab Architecture
    // =========================================================================

    public class AttendanceViewHolder extends RecyclerView.ViewHolder {
        // Sub-Views
        NestedScrollView viewTodayClasses;
        View viewWeeklySchedule;
        View viewAnalytics;

        // Dashboard Header & Greeting
        TextView tvDashboardDate;
        TextView tvDashboardGreeting;
        TextView tvDashboardAcademicMeta;
        View btnDashboardBolt;

        // Quick Glance Chips
        View chipEnrolledCourses;
        TextView tvChipEnrolledCourses;
        View chipScheduledToday;
        TextView tvChipScheduledToday;
        TextView tvChipNextTime;

        // Hero Immediate Focus Card & Empty State
        View cardImmediateFocus;
        TextView tvFocusSubject;
        TextView tvFocusType;
        TextView tvFocusTime;
        TextView tvFocusRoom;
        TextView tvFocusBadge;
        View btnFocusPresent;
        ImageView ivFocusPresent;
        TextView tvFocusPresent;
        View btnFocusAbsent;
        ImageView ivFocusAbsent;
        TextView tvFocusAbsent;
        View btnFocusHoliday;
        ImageView ivFocusHoliday;
        TextView tvFocusHoliday;
        TextView tvFocusStatusCaption;

        View cardEmptyFocus;
        TextView tvEmptyFocusTitle;
        TextView tvEmptyFocusDesc;
        View btnEmptyOpenTimetable;

        // Today's Schedule Section
        View layoutScheduleSection;
        TextView tvScheduleSectionTitle;
        TextView tvScheduleCountPill;
        View btnOpenTimetable;
        LinearLayout containerTodaySchedule;

        // Quick Overview Section
        View btnViewTasks;
        View cardOverallStanding;
        View boxStandingIcon;
        ImageView ivStandingIcon;
        TextView tvOverallPercent;
        TextView tvOverallStandingLabel;
        TextView tvOverallStandingDesc;

        View cardPriorityTask;
        View btnPriorityTaskCheck;
        View boxPriorityTaskCheckbox;
        ImageView ivPriorityTaskCheck;
        TextView tvPriorityTaskTitle;
        TextView tvPriorityTaskDue;

        // Weekly Schedule Sub-View
        ImageButton btnBackFromSchedule;
        View btnScheduleToAnalytics;
        TextView filterDayAll, filterDayMon, filterDayTue, filterDayWed, filterDayThu, filterDayFri;
        RecyclerView rvWeeklySchedule;
        View layoutEmptySchedule;

        // Analytics Sub-View
        TextView tvAnalyticsSemesterPill;
        TextView tvAnalyticsEnrolledCount;
        View btnAnalyticsToSchedule;
        TextView filterAnalyticsAll;
        TextView filterAnalyticsTheory;
        TextView filterAnalyticsLab;
        TextView tvAnalyticsMinReqBadge;
        RecyclerView rvSubjectAnalytics;
        View layoutEmptyAnalytics;
        TextView tvAnalyticsEmptyTitle;
        TextView tvAnalyticsEmptyDesc;
        String currentAnalyticsFilter = "ALL";

        FloatingActionButton fabAddClass;

        AttendanceViewHolder(@NonNull View itemView) {
            super(itemView);
            viewTodayClasses = itemView.findViewById(R.id.view_today_classes);
            viewWeeklySchedule = itemView.findViewById(R.id.view_weekly_schedule);
            viewAnalytics = itemView.findViewById(R.id.view_analytics);

            tvDashboardDate = itemView.findViewById(R.id.tv_dashboard_date);
            tvDashboardGreeting = itemView.findViewById(R.id.tv_dashboard_greeting);
            tvDashboardAcademicMeta = itemView.findViewById(R.id.tv_dashboard_academic_meta);
            btnDashboardBolt = itemView.findViewById(R.id.btn_dashboard_bolt);

            chipEnrolledCourses = itemView.findViewById(R.id.chip_enrolled_courses);
            tvChipEnrolledCourses = itemView.findViewById(R.id.tv_chip_enrolled_courses);
            chipScheduledToday = itemView.findViewById(R.id.chip_scheduled_today);
            tvChipScheduledToday = itemView.findViewById(R.id.tv_chip_scheduled_today);
            tvChipNextTime = itemView.findViewById(R.id.tv_chip_next_time);

            cardImmediateFocus = itemView.findViewById(R.id.card_immediate_focus);
            tvFocusSubject = itemView.findViewById(R.id.tv_focus_subject);
            tvFocusType = itemView.findViewById(R.id.tv_focus_type);
            tvFocusTime = itemView.findViewById(R.id.tv_focus_time);
            tvFocusRoom = itemView.findViewById(R.id.tv_focus_room);
            tvFocusBadge = itemView.findViewById(R.id.tv_focus_badge);
            btnFocusPresent = itemView.findViewById(R.id.btn_focus_present);
            ivFocusPresent = itemView.findViewById(R.id.iv_focus_present);
            tvFocusPresent = itemView.findViewById(R.id.tv_focus_present);
            btnFocusAbsent = itemView.findViewById(R.id.btn_focus_absent);
            ivFocusAbsent = itemView.findViewById(R.id.iv_focus_absent);
            tvFocusAbsent = itemView.findViewById(R.id.tv_focus_absent);
            btnFocusHoliday = itemView.findViewById(R.id.btn_focus_holiday);
            ivFocusHoliday = itemView.findViewById(R.id.iv_focus_holiday);
            tvFocusHoliday = itemView.findViewById(R.id.tv_focus_holiday);
            tvFocusStatusCaption = itemView.findViewById(R.id.tv_focus_status_caption);

            cardEmptyFocus = itemView.findViewById(R.id.card_empty_focus);
            tvEmptyFocusTitle = itemView.findViewById(R.id.tv_empty_focus_title);
            tvEmptyFocusDesc = itemView.findViewById(R.id.tv_empty_focus_desc);
            btnEmptyOpenTimetable = itemView.findViewById(R.id.btn_empty_open_timetable);

            layoutScheduleSection = itemView.findViewById(R.id.layout_schedule_section);
            tvScheduleSectionTitle = itemView.findViewById(R.id.tv_schedule_section_title);
            tvScheduleCountPill = itemView.findViewById(R.id.tv_schedule_count_pill);
            btnOpenTimetable = itemView.findViewById(R.id.btn_open_timetable);
            containerTodaySchedule = itemView.findViewById(R.id.container_today_schedule);

            btnViewTasks = itemView.findViewById(R.id.btn_view_tasks);
            cardOverallStanding = itemView.findViewById(R.id.card_overall_standing);
            boxStandingIcon = itemView.findViewById(R.id.box_standing_icon);
            ivStandingIcon = itemView.findViewById(R.id.iv_standing_icon);
            tvOverallPercent = itemView.findViewById(R.id.tv_overall_percent);
            tvOverallStandingLabel = itemView.findViewById(R.id.tv_overall_standing_label);
            tvOverallStandingDesc = itemView.findViewById(R.id.tv_overall_standing_desc);

            cardPriorityTask = itemView.findViewById(R.id.card_priority_task);
            btnPriorityTaskCheck = itemView.findViewById(R.id.btn_priority_task_check);
            boxPriorityTaskCheckbox = itemView.findViewById(R.id.box_priority_task_checkbox);
            ivPriorityTaskCheck = itemView.findViewById(R.id.iv_priority_task_check);
            tvPriorityTaskTitle = itemView.findViewById(R.id.tv_priority_task_title);
            tvPriorityTaskDue = itemView.findViewById(R.id.tv_priority_task_due);

            btnBackFromSchedule = itemView.findViewById(R.id.btn_back_from_schedule);
            btnScheduleToAnalytics = itemView.findViewById(R.id.btn_schedule_to_analytics);
            filterDayAll = itemView.findViewById(R.id.filter_day_all);
            filterDayMon = itemView.findViewById(R.id.filter_day_mon);
            filterDayTue = itemView.findViewById(R.id.filter_day_tue);
            filterDayWed = itemView.findViewById(R.id.filter_day_wed);
            filterDayThu = itemView.findViewById(R.id.filter_day_thu);
            filterDayFri = itemView.findViewById(R.id.filter_day_fri);
            rvWeeklySchedule = itemView.findViewById(R.id.rv_weekly_schedule);
            layoutEmptySchedule = itemView.findViewById(R.id.layout_empty_schedule);

            tvAnalyticsSemesterPill = itemView.findViewById(R.id.tv_analytics_semester_pill);
            tvAnalyticsEnrolledCount = itemView.findViewById(R.id.tv_analytics_enrolled_count);
            btnAnalyticsToSchedule = itemView.findViewById(R.id.btn_analytics_to_schedule);
            filterAnalyticsAll = itemView.findViewById(R.id.filter_analytics_all);
            filterAnalyticsTheory = itemView.findViewById(R.id.filter_analytics_theory);
            filterAnalyticsLab = itemView.findViewById(R.id.filter_analytics_lab);
            tvAnalyticsMinReqBadge = itemView.findViewById(R.id.tv_analytics_min_req_badge);
            rvSubjectAnalytics = itemView.findViewById(R.id.rv_subject_analytics);
            layoutEmptyAnalytics = itemView.findViewById(R.id.layout_empty_analytics);
            tvAnalyticsEmptyTitle = itemView.findViewById(R.id.tv_analytics_empty_title);
            tvAnalyticsEmptyDesc = itemView.findViewById(R.id.tv_analytics_empty_desc);

            fabAddClass = itemView.findViewById(R.id.fab_add_class);
        }

        void setup() {
            updateGreeting();

            // RecyclerView Setup for Timetable & Analytics sub-views
            rvWeeklySchedule.setLayoutManager(new LinearLayoutManager(MainActivity.this));
            rvWeeklySchedule.setAdapter(weeklyScheduleAdapter);
            rvWeeklySchedule.clearOnScrollListeners();
            rvWeeklySchedule.addOnScrollListener(verticalScrollStateListener);

            rvSubjectAnalytics.setLayoutManager(new LinearLayoutManager(MainActivity.this));
            rvSubjectAnalytics.setAdapter(subjectAnalyticsAdapter);
            rvSubjectAnalytics.clearOnScrollListeners();
            rvSubjectAnalytics.addOnScrollListener(verticalScrollStateListener);

            // Dashboard Quick Navigation Listeners
            btnDashboardBolt.setOnClickListener(v -> switchSubTab(SUB_TAB_TIMETABLE));
            chipEnrolledCourses.setOnClickListener(v -> switchSubTab(SUB_TAB_TIMETABLE));
            btnOpenTimetable.setOnClickListener(v -> switchSubTab(SUB_TAB_TIMETABLE));
            btnEmptyOpenTimetable.setOnClickListener(v -> switchSubTab(SUB_TAB_TIMETABLE));
            chipScheduledToday.setOnClickListener(v -> {
                if (layoutScheduleSection != null && layoutScheduleSection.getVisibility() == View.VISIBLE && viewTodayClasses != null) {
                    viewTodayClasses.smoothScrollTo(0, layoutScheduleSection.getTop());
                } else {
                    switchSubTab(SUB_TAB_TIMETABLE);
                }
            });

            btnViewTasks.setOnClickListener(v -> selectTabDirectly(TAB_TASKS));
            cardOverallStanding.setOnClickListener(v -> switchSubTab(SUB_TAB_ANALYTICS));

            // Sub-View Navigation Listeners
            btnBackFromSchedule.setOnClickListener(v -> switchSubTab(SUB_TAB_TODAY));
            btnScheduleToAnalytics.setOnClickListener(v -> switchSubTab(SUB_TAB_ANALYTICS));
            if (btnAnalyticsToSchedule != null) {
                btnAnalyticsToSchedule.setOnClickListener(v -> switchSubTab(SUB_TAB_TIMETABLE));
            }
            setupAnalyticsFilters();

            // Schedule Day Filters (Strictly Monday-Friday)
            setupDayFilters();
            refreshDayFiltersTheme(ThemeHelper.getAccentColor(MainActivity.this));

            // FAB Action & Theme Styling
            if (fabAddClass != null) {
                ThemeHelper.applyAccentToFab(fabAddClass, ThemeHelper.getAccentColor(MainActivity.this));
                fabAddClass.setOnClickListener(v -> showAddClassBottomSheet(null));
            }

            switchSubTab(currentAttendanceSubTab);
            updateQuickOverview(currentSubjectStats, allDashboardTasks);
        }

        void updateGreeting() {
            if (currentUserProfile == null) {
                currentUserProfile = SecureProfileStore.loadProfile(MainActivity.this);
            }
            if (tvDashboardDate != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());
                tvDashboardDate.setText(sdf.format(new Date()).toUpperCase(Locale.getDefault()));
            }
            if (tvDashboardGreeting != null) {
                Calendar cal = Calendar.getInstance();
                int hour = cal.get(Calendar.HOUR_OF_DAY);
                String baseGreeting;
                if (hour >= 5 && hour < 12) {
                    baseGreeting = "Good Morning";
                } else if (hour >= 12 && hour < 17) {
                    baseGreeting = "Good Afternoon";
                } else {
                    baseGreeting = "Good Evening";
                }
                String userName = currentUserProfile != null ? currentUserProfile.getFullName() : "";
                tvDashboardGreeting.setText(userName.isEmpty() ? baseGreeting : baseGreeting + ", " + userName);
            }
            if (tvDashboardAcademicMeta != null) {
                String summary = currentUserProfile != null ? currentUserProfile.getAcademicSummaryLine() : "";
                if (!summary.isEmpty()) {
                    tvDashboardAcademicMeta.setText(summary);
                    tvDashboardAcademicMeta.setVisibility(View.VISIBLE);
                } else {
                    tvDashboardAcademicMeta.setVisibility(View.GONE);
                }
            }
        }

        void refreshThemeAccent(int accentColor) {
            if (fabAddClass != null) {
                ThemeHelper.applyAccentToFab(fabAddClass, accentColor);
            }
            refreshDayFiltersTheme(accentColor);
            switchSubTab(currentAttendanceSubTab);
        }

        boolean isScrolling() {
            if (rvWeeklySchedule != null && rvWeeklySchedule.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) return true;
            if (rvSubjectAnalytics != null && rvSubjectAnalytics.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) return true;
            return false;
        }

        private void setupDayFilters() {
            TextView[] filterChips = {filterDayAll, filterDayMon, filterDayTue, filterDayWed, filterDayThu, filterDayFri};
            String[] dayNames = {"All", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};

            for (int i = 0; i < filterChips.length; i++) {
                final int index = i;
                filterChips[i].setOnClickListener(v -> {
                    currentScheduleFilterDay = dayNames[index];
                    int accentColor = ThemeHelper.getAccentColor(MainActivity.this);
                    for (int j = 0; j < filterChips.length; j++) {
                        ThemeHelper.applyChipSelected(filterChips[j], j == index, accentColor);
                    }
                    refreshWeeklyScheduleView();
                });
            }
        }

        private void refreshDayFiltersTheme(int accentColor) {
            TextView[] filterChips = {filterDayAll, filterDayMon, filterDayTue, filterDayWed, filterDayThu, filterDayFri};
            String[] dayNames = {"All", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
            for (int i = 0; i < filterChips.length; i++) {
                boolean isSelected = dayNames[i].equalsIgnoreCase(currentScheduleFilterDay);
                ThemeHelper.applyChipSelected(filterChips[i], isSelected, accentColor);
            }
        }

        void switchSubTab(int subTab) {
            currentAttendanceSubTab = subTab;

            viewTodayClasses.setVisibility(View.GONE);
            viewWeeklySchedule.setVisibility(View.GONE);
            viewAnalytics.setVisibility(View.GONE);

            boolean showBottomNav = (currentTab != TAB_ATTENDANCE) || (subTab == SUB_TAB_TODAY);
            if (bottomNavContainer != null) {
                bottomNavContainer.setVisibility(showBottomNav ? View.VISIBLE : View.GONE);
            }
            if (viewPager != null) {
                viewPager.setUserInputEnabled(showBottomNav);
            }

            if (subTab == SUB_TAB_TODAY) {
                viewTodayClasses.setVisibility(View.VISIBLE);
                if (fabAddClass != null) fabAddClass.setVisibility(View.GONE);
                updateGreeting();
                refreshTodayClassesView();
                updateQuickOverview(currentSubjectStats, allDashboardTasks);
            } else if (subTab == SUB_TAB_TIMETABLE) {
                viewWeeklySchedule.setVisibility(View.VISIBLE);
                if (fabAddClass != null) fabAddClass.setVisibility(View.VISIBLE);
                refreshWeeklyScheduleView();
            } else {
                viewAnalytics.setVisibility(View.VISIBLE);
                if (fabAddClass != null) fabAddClass.setVisibility(View.GONE);
                updateAnalyticsView(currentSubjectStats);
            }
        }

        void updateDashboardTodayUI(List<ClassWithTodayStatus> items, String todayDayName, boolean isWeekend, int enrolledCourseCount) {
            updateGreeting();

            // Quick Glance Chip 1: Enrolled Courses
            tvChipEnrolledCourses.setText(enrolledCourseCount + (enrolledCourseCount == 1 ? " Enrolled Course" : " Enrolled Courses"));

            // Quick Glance Chip 2: Scheduled Today
            int todayCount = items != null ? items.size() : 0;
            tvChipScheduledToday.setText(todayCount + " Scheduled Today");

            Calendar nowCal = Calendar.getInstance();
            int nowMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE);

            if (isWeekend || items == null || items.isEmpty()) {
                tvChipNextTime.setText(isWeekend ? "Weekend" : "No Classes");
                cardImmediateFocus.setVisibility(View.GONE);
                cardEmptyFocus.setVisibility(View.VISIBLE);
                layoutScheduleSection.setVisibility(View.GONE);

                if (isWeekend) {
                    tvEmptyFocusTitle.setText("Weekend Mode");
                    tvEmptyFocusDesc.setText("No classes on Saturday or Sunday. Review your weekly timetable or catch up on tasks.");
                } else {
                    tvEmptyFocusTitle.setText("No classes scheduled for " + todayDayName);
                    tvEmptyFocusDesc.setText("Configure your Monday–Friday classes in the weekly timetable or tap + to add one.");
                }
                return;
            }

            cardImmediateFocus.setVisibility(View.VISIBLE);
            cardEmptyFocus.setVisibility(View.GONE);
            layoutScheduleSection.setVisibility(View.VISIBLE);

            // Find next upcoming class for Quick Glance Chip 3
            ClassSchedule nextUpcoming = null;
            for (ClassWithTodayStatus item : items) {
                ClassSchedule cs = item.getSchedule();
                int startMin = ClassSchedule.parseTimeToMinutes(cs.getStartTime());
                int endMin = ClassSchedule.parseTimeToMinutes(cs.getEndTime());
                if (endMin > nowMinutes || startMin >= nowMinutes) {
                    nextUpcoming = cs;
                    break;
                }
            }
            if (nextUpcoming != null) {
                tvChipNextTime.setText("Next: " + nextUpcoming.getStartTime());
            } else {
                tvChipNextTime.setText("Done Today");
            }

            // Determine Focused Class for Immediate Focus Hero Card
            ClassWithTodayStatus focusedItem = null;
            if (focusedScheduleId != -1) {
                for (ClassWithTodayStatus item : items) {
                    if (item.getSchedule().getId() == focusedScheduleId) {
                        focusedItem = item;
                        break;
                    }
                }
            }
            if (focusedItem == null) {
                for (ClassWithTodayStatus item : items) {
                    int endMin = ClassSchedule.parseTimeToMinutes(item.getSchedule().getEndTime());
                    if (endMin > nowMinutes) {
                        focusedItem = item;
                        break;
                    }
                }
            }
            if (focusedItem == null) {
                focusedItem = items.get(items.size() - 1);
            }

            bindImmediateFocusCard(focusedItem, nowMinutes);

            // Populate Today's Schedule Section
            tvScheduleSectionTitle.setText(todayDayName + " Schedule");
            tvScheduleCountPill.setText(todayCount + (todayCount == 1 ? " class" : " classes"));
            containerTodaySchedule.removeAllViews();

            LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
            for (ClassWithTodayStatus item : items) {
                // Exclude focused item when there are multiple classes to match clean hierarchy
                if (items.size() > 1 && item == focusedItem) {
                    continue;
                }
                View row = inflater.inflate(R.layout.item_dashboard_schedule, containerTodaySchedule, false);
                bindDashboardScheduleRow(row, item, nowMinutes);
                containerTodaySchedule.addView(row);
            }
        }

        private void bindImmediateFocusCard(ClassWithTodayStatus item, int nowMinutes) {
            ClassSchedule cs = item.getSchedule();
            tvFocusSubject.setText(cs.getSubjectName());

            String type = cs.getClassType();
            tvFocusType.setText(type.toUpperCase(Locale.ROOT));
            if ("Lab".equalsIgnoreCase(type)) {
                tvFocusType.setBackgroundResource(R.drawable.bg_type_lab);
                tvFocusType.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.type_lab_text));
            } else {
                tvFocusType.setBackgroundResource(R.drawable.bg_type_theory);
                tvFocusType.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.type_theory_text));
            }

            String endStr = cs.getEndTime() != null ? cs.getEndTime().trim() : "";
            String timeText = endStr.isEmpty() ? cs.getStartTime() : (cs.getStartTime() + " - " + endStr);
            tvFocusTime.setText(timeText);

            String roomLabel = formatRoomLabel(cs.getRoomNumber());
            tvFocusRoom.setText(roomLabel + " (" + cs.getCreditHours() + " Cr)");

            int startMin = ClassSchedule.parseTimeToMinutes(cs.getStartTime());
            int endMin = ClassSchedule.parseTimeToMinutes(cs.getEndTime());
            if (nowMinutes >= startMin && nowMinutes < endMin) {
                tvFocusBadge.setText("NOW");
            } else if (nowMinutes < startMin) {
                tvFocusBadge.setText("NEXT UP");
            } else {
                tvFocusBadge.setText("TODAY");
            }

            // Reset toggle buttons
            int secondaryColor = ContextCompat.getColor(MainActivity.this, R.color.zen_text_secondary);
            btnFocusPresent.setBackground(null);
            ivFocusPresent.setColorFilter(secondaryColor);
            tvFocusPresent.setTextColor(secondaryColor);

            btnFocusAbsent.setBackground(null);
            ivFocusAbsent.setColorFilter(secondaryColor);
            tvFocusAbsent.setTextColor(secondaryColor);

            btnFocusHoliday.setBackground(null);
            ivFocusHoliday.setColorFilter(secondaryColor);
            tvFocusHoliday.setTextColor(secondaryColor);

            String status = item.getCurrentStatus();
            if (AttendanceHistory.STATUS_PRESENT.equalsIgnoreCase(status)) {
                int onAccent = ContextCompat.getColor(MainActivity.this, R.color.zen_on_accent);
                btnFocusPresent.setBackgroundResource(R.drawable.bg_toggle_present_active);
                ivFocusPresent.setColorFilter(onAccent);
                tvFocusPresent.setTextColor(onAccent);
                setFocusStatusCaption("Marked as Present", "Present", ContextCompat.getColor(MainActivity.this, R.color.status_present));
            } else if (AttendanceHistory.STATUS_ABSENT.equalsIgnoreCase(status)) {
                int onErr = ContextCompat.getColor(MainActivity.this, R.color.zen_on_error_container);
                btnFocusAbsent.setBackgroundResource(R.drawable.bg_toggle_absent_active);
                ivFocusAbsent.setColorFilter(onErr);
                tvFocusAbsent.setTextColor(onErr);
                setFocusStatusCaption("Marked as Absent", "Absent", ContextCompat.getColor(MainActivity.this, R.color.status_absent));
            } else if (AttendanceHistory.STATUS_HOLIDAY.equalsIgnoreCase(status)) {
                int holidayTint = ContextCompat.getColor(MainActivity.this, R.color.status_holiday);
                int primaryText = ContextCompat.getColor(MainActivity.this, R.color.zen_text_primary);
                btnFocusHoliday.setBackgroundResource(R.drawable.bg_toggle_holiday_active);
                ivFocusHoliday.setColorFilter(holidayTint);
                tvFocusHoliday.setTextColor(primaryText);
                setFocusStatusCaption("Marked as Holiday (Session excluded)", "Holiday", holidayTint);
            } else {
                tvFocusStatusCaption.setText("Tap an option above to mark attendance");
                tvFocusStatusCaption.setTextColor(secondaryColor);
            }

            btnFocusPresent.setOnClickListener(v -> onMarkAttendance(cs, AttendanceHistory.STATUS_PRESENT));
            btnFocusAbsent.setOnClickListener(v -> onMarkAttendance(cs, AttendanceHistory.STATUS_ABSENT));
            btnFocusHoliday.setOnClickListener(v -> onMarkAttendance(cs, AttendanceHistory.STATUS_HOLIDAY));
        }

        private void setFocusStatusCaption(String fullText, String highlightWord, int highlightColor) {
            tvFocusStatusCaption.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_text_secondary));
            int start = fullText.indexOf(highlightWord);
            if (start >= 0) {
                SpannableString ss = new SpannableString(fullText);
                ss.setSpan(new ForegroundColorSpan(highlightColor), start, start + highlightWord.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ss.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), start, start + highlightWord.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                tvFocusStatusCaption.setText(ss);
            } else {
                tvFocusStatusCaption.setText(fullText);
            }
        }

        private void bindDashboardScheduleRow(View row, ClassWithTodayStatus item, int nowMinutes) {
            ClassSchedule cs = item.getSchedule();
            TextView tvSubject = row.findViewById(R.id.tv_dash_sched_subject);
            TextView tvType = row.findViewById(R.id.tv_dash_sched_type);
            TextView tvMeta = row.findViewById(R.id.tv_dash_sched_meta);
            TextView tvPill = row.findViewById(R.id.tv_dash_sched_status_pill);

            tvSubject.setText(cs.getSubjectName());
            String type = cs.getClassType();
            tvType.setText(type.toUpperCase(Locale.ROOT));
            if ("Lab".equalsIgnoreCase(type)) {
                tvType.setBackgroundResource(R.drawable.bg_type_lab);
                tvType.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.type_lab_text));
            } else {
                tvType.setBackgroundResource(R.drawable.bg_type_theory);
                tvType.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.type_theory_text));
            }

            String endStr = cs.getEndTime() != null ? cs.getEndTime().trim() : "";
            String timeStr = endStr.isEmpty() ? cs.getStartTime() : (cs.getStartTime() + " - " + endStr);
            String roomLabel = formatRoomLabel(cs.getRoomNumber());
            tvMeta.setText(timeStr + " • " + roomLabel + " (" + cs.getCreditHours() + " Cr)");

            String status = item.getCurrentStatus();
            if (AttendanceHistory.STATUS_PRESENT.equalsIgnoreCase(status)) {
                tvPill.setText("Present");
                tvPill.setBackgroundResource(R.drawable.bg_pill_accent_soft);
                tvPill.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.status_present));
            } else if (AttendanceHistory.STATUS_ABSENT.equalsIgnoreCase(status)) {
                tvPill.setText("Absent");
                tvPill.setBackgroundResource(R.drawable.bg_toggle_absent_active);
                tvPill.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_on_error_container));
            } else if (AttendanceHistory.STATUS_HOLIDAY.equalsIgnoreCase(status)) {
                tvPill.setText("Holiday");
                tvPill.setBackgroundResource(R.drawable.bg_pill_highest);
                tvPill.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.status_holiday));
            } else {
                tvPill.setBackgroundResource(R.drawable.bg_pill_highest);
                int startMin = ClassSchedule.parseTimeToMinutes(cs.getStartTime());
                int endMin = ClassSchedule.parseTimeToMinutes(cs.getEndTime());
                if (nowMinutes >= startMin && nowMinutes < endMin) {
                    tvPill.setText("Now");
                    tvPill.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_accent));
                } else if (startMin > nowMinutes) {
                    int diffMins = startMin - nowMinutes;
                    if (diffMins >= 60) {
                        int hrs = Math.max(1, Math.round(diffMins / 60f));
                        tvPill.setText("In " + hrs + "h");
                    } else {
                        tvPill.setText("In " + diffMins + "m");
                    }
                    tvPill.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_text_secondary));
                } else {
                    tvPill.setText("Ended");
                    tvPill.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_text_tertiary));
                }
            }

            row.setOnClickListener(v -> {
                focusedScheduleId = cs.getId();
                refreshTodayClassesView();
                if (viewTodayClasses != null) {
                    viewTodayClasses.smoothScrollTo(0, 0);
                }
            });
        }

        private String formatRoomLabel(String rawRoom) {
            if (rawRoom == null || rawRoom.trim().isEmpty()) {
                return "TBA";
            }
            String trimmed = rawRoom.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.startsWith("room") || lower.startsWith("romm") || lower.startsWith("lab") || lower.startsWith("hall") || lower.equals("tba")) {
                return trimmed;
            }
            return "Room # " + trimmed;
        }

        void updateQuickOverview(List<SubjectStats> stats, List<Task> tasks) {
            // 1. Overall Attendance Standing Card
            int targetPct = currentUserProfile != null ? currentUserProfile.getEffectiveTargetPercent() : 75;
            int totalPresent = 0;
            int totalAbsent = 0;
            int belowThresholdCount = 0;
            if (stats != null) {
                for (SubjectStats s : stats) {
                    totalPresent += s.getPresentCount();
                    totalAbsent += s.getAbsentCount();
                    if (s.getEffectiveTotal() > 0 && s.getAttendancePercentage() < (double) targetPct) {
                        belowThresholdCount++;
                    }
                }
            }

            int totalEffective = totalPresent + totalAbsent;
            if (totalEffective > 0) {
                double overallPct = (totalPresent * 100.0) / totalEffective;
                tvOverallPercent.setText(String.format(Locale.getDefault(), "%.1f%% Overall", overallPct));
                if (overallPct >= (double) targetPct && belowThresholdCount == 0) {
                    tvOverallStandingLabel.setText("Good Standing");
                    tvOverallStandingLabel.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_accent));
                    tvOverallStandingDesc.setText("All attendance thresholds (" + targetPct + "%) met");
                    ivStandingIcon.setColorFilter(ContextCompat.getColor(MainActivity.this, R.color.zen_accent));
                } else if (overallPct >= (double) targetPct) {
                    tvOverallStandingLabel.setText("Watch List");
                    tvOverallStandingLabel.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.status_warning));
                    tvOverallStandingDesc.setText(belowThresholdCount + (belowThresholdCount == 1 ? " subject" : " subjects") + " below " + targetPct + "%");
                    ivStandingIcon.setColorFilter(ContextCompat.getColor(MainActivity.this, R.color.status_warning));
                } else {
                    tvOverallStandingLabel.setText("Needs Focus");
                    tvOverallStandingLabel.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.status_absent));
                    tvOverallStandingDesc.setText(belowThresholdCount + (belowThresholdCount == 1 ? " subject" : " subjects") + " below " + targetPct + "%");
                    ivStandingIcon.setColorFilter(ContextCompat.getColor(MainActivity.this, R.color.status_absent));
                }
            } else {
                tvOverallPercent.setText("No Attendance Yet");
                tvOverallStandingLabel.setText("Ready");
                tvOverallStandingLabel.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_accent));
                tvOverallStandingDesc.setText("Mark classes above to calculate standing");
                ivStandingIcon.setColorFilter(ContextCompat.getColor(MainActivity.this, R.color.zen_accent));
            }

            // 2. Priority Task Card
            Task priorityTask = pickPriorityTask(tasks);
            if (priorityTask != null) {
                boolean done = priorityTask.isCompleted();
                tvPriorityTaskTitle.setText(priorityTask.getDescription());
                if (done) {
                    tvPriorityTaskTitle.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_text_completed));
                    tvPriorityTaskTitle.setPaintFlags(tvPriorityTaskTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    boxPriorityTaskCheckbox.setBackgroundResource(R.drawable.bg_checkbox_checked);
                    ivPriorityTaskCheck.setVisibility(View.VISIBLE);
                } else {
                    tvPriorityTaskTitle.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_text_primary));
                    tvPriorityTaskTitle.setPaintFlags(tvPriorityTaskTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                    boxPriorityTaskCheckbox.setBackgroundResource(R.drawable.bg_checkbox_unchecked);
                    ivPriorityTaskCheck.setVisibility(View.INVISIBLE);
                }
                tvPriorityTaskDue.setText(formatTaskDueSubtitle(priorityTask));

                btnPriorityTaskCheck.setOnClickListener(v -> onTaskToggle(priorityTask, !priorityTask.isCompleted()));
                cardPriorityTask.setOnClickListener(v -> selectTabDirectly(TAB_TASKS));
            } else {
                tvPriorityTaskTitle.setText("No pending tasks");
                tvPriorityTaskTitle.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_text_secondary));
                tvPriorityTaskTitle.setPaintFlags(tvPriorityTaskTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                boxPriorityTaskCheckbox.setBackgroundResource(R.drawable.bg_checkbox_unchecked);
                ivPriorityTaskCheck.setVisibility(View.INVISIBLE);
                tvPriorityTaskDue.setText("Tap to add or manage tasks");

                btnPriorityTaskCheck.setOnClickListener(v -> showAddTaskBottomSheet());
                cardPriorityTask.setOnClickListener(v -> selectTabDirectly(TAB_TASKS));
            }
        }

        private Task pickPriorityTask(List<Task> tasks) {
            if (tasks == null || tasks.isEmpty()) return null;
            Task earliestTimedPending = null;
            Task firstPending = null;
            for (Task t : tasks) {
                if (!t.isCompleted()) {
                    if (firstPending == null) {
                        firstPending = t;
                    }
                    if (t.getEndTime() > 0) {
                        if (earliestTimedPending == null || t.getEndTime() < earliestTimedPending.getEndTime()) {
                            earliestTimedPending = t;
                        }
                    }
                }
            }
            if (earliestTimedPending != null) return earliestTimedPending;
            if (firstPending != null) return firstPending;
            return tasks.get(0);
        }

        private String formatTaskDueSubtitle(Task task) {
            if (task.getEndTime() <= 0) {
                return task.isCompleted() ? "Completed" : "No deadline • Tap to view tasks";
            }
            long dueMillis = task.getEndTime();
            long todayStart = getStartOfDayMillis(Calendar.getInstance());
            Calendar tomorrowCal = Calendar.getInstance();
            tomorrowCal.add(Calendar.DAY_OF_YEAR, 1);
            long tomorrowStart = getStartOfDayMillis(tomorrowCal);
            Calendar dayAfterCal = (Calendar) tomorrowCal.clone();
            dayAfterCal.add(Calendar.DAY_OF_YEAR, 1);
            long dayAfterStart = getStartOfDayMillis(dayAfterCal);

            SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());
            if (dueMillis >= todayStart && dueMillis < tomorrowStart) {
                return "Due Today • " + timeFmt.format(new Date(dueMillis));
            } else if (dueMillis >= tomorrowStart && dueMillis < dayAfterStart) {
                return "Due Tomorrow • " + timeFmt.format(new Date(dueMillis));
            } else {
                SimpleDateFormat dateFmt = new SimpleDateFormat("MMM d • h:mm a", Locale.getDefault());
                return "Due " + dateFmt.format(new Date(dueMillis));
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

        private void setupAnalyticsFilters() {
            if (filterAnalyticsAll != null) {
                filterAnalyticsAll.setOnClickListener(v -> {
                    currentAnalyticsFilter = "ALL";
                    refreshAnalyticsFilterPills();
                    updateAnalyticsView(currentSubjectStats);
                });
            }
            if (filterAnalyticsTheory != null) {
                filterAnalyticsTheory.setOnClickListener(v -> {
                    currentAnalyticsFilter = "THEORY";
                    refreshAnalyticsFilterPills();
                    updateAnalyticsView(currentSubjectStats);
                });
            }
            if (filterAnalyticsLab != null) {
                filterAnalyticsLab.setOnClickListener(v -> {
                    currentAnalyticsFilter = "LAB";
                    refreshAnalyticsFilterPills();
                    updateAnalyticsView(currentSubjectStats);
                });
            }
            refreshAnalyticsFilterPills();
        }

        private void refreshAnalyticsFilterPills() {
            applyAnalyticsFilterStyle(filterAnalyticsAll, "ALL".equals(currentAnalyticsFilter));
            applyAnalyticsFilterStyle(filterAnalyticsTheory, "THEORY".equals(currentAnalyticsFilter));
            applyAnalyticsFilterStyle(filterAnalyticsLab, "LAB".equals(currentAnalyticsFilter));
        }

        private void applyAnalyticsFilterStyle(TextView pill, boolean selected) {
            if (pill == null) return;
            if (selected) {
                pill.setBackgroundResource(R.drawable.bg_pill_lime_solid);
                pill.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_on_accent));
            } else {
                pill.setBackgroundResource(R.drawable.bg_pill_surface_outline);
                pill.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.zen_text_secondary));
            }
        }

        void updateAnalyticsView(List<SubjectStats> stats) {
            if (currentUserProfile == null) {
                currentUserProfile = SecureProfileStore.loadProfile(MainActivity.this);
            }

            // Semester & Min. Req threshold
            int minTarget = currentUserProfile != null ? currentUserProfile.getEffectiveTargetPercent() : 75;
            subjectAnalyticsAdapter.setMinTargetPercent(minTarget);
            if (tvAnalyticsMinReqBadge != null) {
                tvAnalyticsMinReqBadge.setText(String.format(Locale.getDefault(), "%d%%", minTarget));
            }
            if (tvAnalyticsSemesterPill != null) {
                String sem = currentUserProfile != null ? currentUserProfile.getCurrentSemester() : "";
                tvAnalyticsSemesterPill.setText(!sem.isEmpty() ? sem.toUpperCase(Locale.getDefault()) : "CURRENT TERM");
            }

            // Enrich stats with schedule metadata (classType & creditHours)
            Map<String, ClassSchedule> scheduleLookup = new HashMap<>();
            Set<String> distinctCourses = new HashSet<>();
            for (ClassSchedule cs : currentSchedules) {
                if (cs.getSubjectName() != null && !cs.getSubjectName().trim().isEmpty()) {
                    String key = cs.getSubjectName().trim().toLowerCase(Locale.ROOT);
                    distinctCourses.add(key);
                    if (!scheduleLookup.containsKey(key)) {
                        scheduleLookup.put(key, cs);
                    }
                }
            }

            List<SubjectStats> allStats = stats != null ? new ArrayList<>(stats) : new ArrayList<>();
            for (SubjectStats s : allStats) {
                String key = s.getSubjectName().trim().toLowerCase(Locale.ROOT);
                distinctCourses.add(key);
                ClassSchedule match = scheduleLookup.get(key);
                if (match != null) {
                    s.setClassType(match.getClassType());
                    s.setCreditHours(match.getCreditHours());
                }
            }

            int enrolledCount = distinctCourses.size();
            if (tvAnalyticsEnrolledCount != null) {
                tvAnalyticsEnrolledCount.setText(String.format(Locale.getDefault(),
                        "%d %s Enrolled", enrolledCount, enrolledCount == 1 ? "Course" : "Courses"));
            }
            if (filterAnalyticsAll != null) {
                filterAnalyticsAll.setText(String.format(Locale.getDefault(), "All (%d)", allStats.size()));
            }

            List<SubjectStats> filtered = new ArrayList<>();
            for (SubjectStats s : allStats) {
                if ("THEORY".equals(currentAnalyticsFilter)) {
                    if (!"Lab".equalsIgnoreCase(s.getClassType())) {
                        filtered.add(s);
                    }
                } else if ("LAB".equals(currentAnalyticsFilter)) {
                    if ("Lab".equalsIgnoreCase(s.getClassType())) {
                        filtered.add(s);
                    }
                } else {
                    filtered.add(s);
                }
            }

            subjectAnalyticsAdapter.setStats(filtered);

            if (filtered.isEmpty()) {
                layoutEmptyAnalytics.setVisibility(View.VISIBLE);
                rvSubjectAnalytics.setVisibility(View.GONE);
                if (tvAnalyticsEmptyTitle != null && tvAnalyticsEmptyDesc != null) {
                    if ("THEORY".equals(currentAnalyticsFilter) && !allStats.isEmpty()) {
                        tvAnalyticsEmptyTitle.setText("No Theory courses recorded");
                        tvAnalyticsEmptyDesc.setText("Mark attendance for a Theory class or switch to All.");
                    } else if ("LAB".equals(currentAnalyticsFilter) && !allStats.isEmpty()) {
                        tvAnalyticsEmptyTitle.setText("No Laboratory courses recorded");
                        tvAnalyticsEmptyDesc.setText("Mark attendance for a Lab class or switch to All.");
                    } else {
                        tvAnalyticsEmptyTitle.setText("No analytics available");
                        tvAnalyticsEmptyDesc.setText("Configure your class schedule and mark attendance to see detailed subject statistics.");
                    }
                }
            } else {
                layoutEmptyAnalytics.setVisibility(View.GONE);
                rvSubjectAnalytics.setVisibility(View.VISIBLE);
            }
        }
    }

    // =========================================================================
    // Attendance Interaction Callbacks
    // =========================================================================

    public void onMarkAttendance(String subjectName, int creditHours, String classType, String status) {
        ThemeHelper.performHaptic(attendanceViewHolder != null ? attendanceViewHolder.itemView : viewPager);
        long todayDate = getStartOfDayMillis(Calendar.getInstance());

        AppDatabase.databaseWriteExecutor.execute(() -> {
            AttendanceHistory existing = database.attendanceHistoryDao()
                    .getRecordBySubjectAndDateSync(subjectName, todayDate);
            if (existing != null) {
                existing.setStatus(status);
                existing.setCreditHours(creditHours);
                existing.setClassType(classType);
                database.attendanceHistoryDao().update(existing);
            } else {
                AttendanceHistory record = new AttendanceHistory(
                        subjectName,
                        creditHours,
                        classType,
                        todayDate,
                        status
                );
                database.attendanceHistoryDao().insert(record);
            }
            database.activityLogDao().insert(new com.example.model.ActivityLog(
                    System.currentTimeMillis(),
                    "Marked " + subjectName + " as " + status
            ));
        });

        Toast.makeText(this, "Marked " + subjectName + " as " + status, Toast.LENGTH_SHORT).show();
    }

    public void onMarkAttendance(ClassSchedule schedule, String status) {
        if (schedule != null) {
            onMarkAttendance(schedule.getSubjectName(), schedule.getCreditHours(), schedule.getClassType(), status);
        }
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
        Intent intent = new Intent(MainActivity.this, DetailedHistoryActivity.class);
        if (stats != null) {
            intent.putExtra(DetailedHistoryActivity.EXTRA_SUBJECT_NAME, stats.getSubjectName());
        }
        startActivity(intent);
    }

    /**
     * Dialog to Add or Edit a Class Schedule.
     * Crucial Rule: Day selector ONLY allows Monday, Tuesday, Wednesday, Thursday, Friday.
     * Saturday and Sunday are strictly blocked from scheduling.
     */
    public void showAddClassBottomSheet(ClassSchedule existingSchedule) {
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

        ImageButton btnClose = dialogView.findViewById(R.id.btn_close_class);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

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

        int accentColor = ThemeHelper.getAccentColor(MainActivity.this);
        ThemeHelper.applyAccentToPrimaryButton(btnSave, accentColor);

        com.google.android.material.textfield.TextInputLayout tilSubject = dialogView.findViewById(R.id.til_class_subject);
        com.google.android.material.textfield.TextInputLayout tilRoom = dialogView.findViewById(R.id.til_class_room);
        TextView tvReminderNote = dialogView.findViewById(R.id.tv_class_reminder_note);
        ThemeHelper.applyAccentToTextInputLayout(tilSubject, accentColor);
        ThemeHelper.applyAccentToTextInputLayout(tilRoom, accentColor);
        if (tvReminderNote != null) {
            tvReminderNote.setTextColor(accentColor);
        }

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
                boolean isSelected = dayNames[i].equalsIgnoreCase(selectedDay[0]);
                ThemeHelper.applyChipSelected(dayChips[i], isSelected, accentColor);
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

        // Credit Hours Selection Runnable (Defined early so chipLab can trigger smart default)
        TextView[] creditChips = {chipCr1, chipCr2, chipCr3, chipCr4};
        int[] creditValues = {1, 2, 3, 4};

        Runnable updateCreditChips = () -> {
            for (int i = 0; i < creditChips.length; i++) {
                boolean isSelected = (creditValues[i] == selectedCredits[0]);
                ThemeHelper.applyChipSelected(creditChips[i], isSelected, accentColor);
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

        // Type Selection (Theory vs Lab)
        Runnable updateTypeChips = () -> {
            boolean isLab = "Lab".equalsIgnoreCase(selectedType[0]);
            ThemeHelper.applyChipSelected(chipLab, isLab, accentColor);
            ThemeHelper.applyChipSelected(chipTheory, !isLab, accentColor);
        };
        updateTypeChips.run();

        chipTheory.setOnClickListener(v -> {
            selectedType[0] = "Theory";
            updateTypeChips.run();
        });

        // Issue 1: Smart Lab Default - Sets selectedCredits to 1 and refreshes credit chips UI
        chipLab.setOnClickListener(v -> {
            selectedType[0] = "Lab";
            updateTypeChips.run();

            // Auto-default to 1 credit hour when selecting Lab (can still be manually adjusted afterward)
            selectedCredits[0] = 1;
            updateCreditChips.run();
        });

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

            // Issue 1: Time Validation - Block saving if End Time <= Start Time
            if (!isValidClassTimeWindow(selectedStartTimeStr[0], selectedEndTimeStr[0])) {
                Toast.makeText(
                        MainActivity.this,
                        "End time must be after start time. Please select a valid class time window.",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            // Issue 2: Lab Grouping Bug (Auto-Suffix)
            // If class type is Lab and subject name does not already end with "Lab", append " Lab"
            if ("Lab".equalsIgnoreCase(selectedType[0])) {
                if (!subject.toLowerCase(Locale.ROOT).endsWith("lab")) {
                    subject = subject + " Lab";
                }
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

    // =========================================================================
    // Task Bottom Sheet Dialog
    // =========================================================================

    public void showAddTaskBottomSheet() {
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

        int accentColor = ThemeHelper.getAccentColor(MainActivity.this);
        ThemeHelper.applyAccentToPrimaryButton(btnSubmit, accentColor);

        com.google.android.material.textfield.TextInputLayout tilTaskInput = dialogView.findViewById(R.id.til_task_input);
        ThemeHelper.applyAccentToTextInputLayout(tilTaskInput, accentColor);

        TextView chipNoLimit = dialogView.findViewById(R.id.chip_no_limit);
        TextView chip30m = dialogView.findViewById(R.id.chip_30m);
        TextView chip1h = dialogView.findViewById(R.id.chip_1h);
        TextView chip3h = dialogView.findViewById(R.id.chip_3h);
        TextView chipTodayEod = dialogView.findViewById(R.id.chip_today_eod);
        TextView chipCustom = dialogView.findViewById(R.id.chip_custom_time);

        View layoutTimingSummary = dialogView.findViewById(R.id.layout_timing_summary);
        TextView tvTimingPreview = dialogView.findViewById(R.id.tv_timing_preview);
        tvTimingPreview.setTextColor(accentColor);

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
                ThemeHelper.applyChipSelected(chip, chip == selected, accentColor);
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
        ThemeHelper.performHaptic(viewPager);
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

    /**
     * Validates whether the class end time occurs strictly after the start time.
     *
     * @param startTimeStr Start time formatted string (e.g. "09:00 AM")
     * @param endTimeStr   End time formatted string (e.g. "10:30 AM")
     * @return true if end time is strictly greater than start time within a 24-hour cycle
     */
    private boolean isValidClassTimeWindow(String startTimeStr, String endTimeStr) {
        int[] startHM = NotificationHelper.parseHourAndMinute(startTimeStr);
        int[] endHM = NotificationHelper.parseHourAndMinute(endTimeStr);
        int startMinutes = startHM[0] * 60 + startHM[1];
        int endMinutes = endHM[0] * 60 + endHM[1];
        return endMinutes > startMinutes;
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}

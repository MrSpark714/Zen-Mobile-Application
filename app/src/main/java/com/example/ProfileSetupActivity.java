package com.example;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.database.AppDatabase;
import com.example.model.UserProfile;
import com.example.util.DeleteConfirmationDialog;
import com.example.util.ScheduleSyncPreferences;
import com.example.util.SecureProfileStore;
import com.example.util.ThemeHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Dedicated Academic Profile & Photo Setup screen.
 *
 * - Shown automatically on first launch (with Skip support) and reachable anytime
 *   from the MainActivity top-right profile avatar.
 * - Uses Android's zero-permission Photo Picker (PickVisualMedia).
 * - Uses plain text inputs (no dropdowns) for semester, degree, and courses.
 * - Stores everything strictly locally via Android Keystore AES-256-GCM encryption
 *   inside private internal storage (SecureProfileStore).
 */
public class ProfileSetupActivity extends AppCompatActivity {

    public static final String EXTRA_IS_FIRST_LAUNCH = "extra_is_first_launch";

    private boolean isFirstLaunch = false;

    // Header & Hero Views
    private ImageButton btnBack;
    private TextView tvScreenTitle;
    private TextView tvScreenSubtitle;
    private TextView btnTopSkip;
    private ImageButton btnClearProfile;

    private View btnAvatarPickArea;
    private TextView tvAvatarInitial;
    private ImageView ivAvatarPhoto;
    private TextView tvPreviewName;
    private TextView tvPreviewMeta;
    private View btnUploadPhoto;
    private TextView tvUploadPhotoLabel;
    private TextView btnRemovePhoto;

    // Form Inputs (Plain Text Fields — No Dropdowns)
    private TextInputLayout tilName;
    private TextInputLayout tilUniversity;
    private TextInputLayout tilSemester;
    private TextInputLayout tilDegree;
    private TextInputLayout tilCurrentCourse;
    private TextInputLayout tilDepartment;
    private TextInputLayout tilStudentId;
    private TextInputLayout tilTargetPercent;
    private TextInputLayout tilCampusSection;

    private TextInputEditText etName;
    private TextInputEditText etUniversity;
    private TextInputEditText etSemester;
    private TextInputEditText etDegree;
    private TextInputEditText etCurrentCourse;
    private TextInputEditText etDepartment;
    private TextInputEditText etStudentId;
    private TextInputEditText etTargetPercent;
    private TextInputEditText etCampusSection;

    // Semester Date Pickers
    private View btnStartDate;
    private TextView tvStartDate;
    private View btnEndDate;
    private TextView tvEndDate;

    // Bottom Actions
    private MaterialButton btnSave;
    private MaterialButton btnBottomSkip;

    // Mutable State
    private long semesterStartMillis = 0L;
    private long semesterEndMillis = 0L;
    private Bitmap activeAvatarBitmap = null;
    private boolean avatarChanged = false;
    private boolean avatarRemoved = false;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::onPhotoPicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide personal profile from Recents thumbnails on Android 13+ without blacking out virtual displays
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false);
        }

        setContentView(R.layout.activity_profile_setup);

        isFirstLaunch = getIntent() != null && getIntent().getBooleanExtra(EXTRA_IS_FIRST_LAUNCH, false);

        bindViews();
        applyThemeStyling();
        setupAvatarOutline();
        loadExistingProfile();
        setupListeners();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btn_profile_back);
        tvScreenTitle = findViewById(R.id.tv_profile_screen_title);
        tvScreenSubtitle = findViewById(R.id.tv_profile_screen_subtitle);
        btnTopSkip = findViewById(R.id.btn_profile_top_skip);
        btnClearProfile = findViewById(R.id.btn_profile_clear);

        btnAvatarPickArea = findViewById(R.id.btn_avatar_pick_area);
        tvAvatarInitial = findViewById(R.id.tv_profile_avatar_initial);
        ivAvatarPhoto = findViewById(R.id.iv_profile_avatar_photo);
        tvPreviewName = findViewById(R.id.tv_preview_name);
        tvPreviewMeta = findViewById(R.id.tv_preview_meta);
        btnUploadPhoto = findViewById(R.id.btn_upload_photo);
        tvUploadPhotoLabel = findViewById(R.id.tv_upload_photo_label);
        btnRemovePhoto = findViewById(R.id.btn_remove_photo);

        tilName = findViewById(R.id.til_profile_name);
        tilUniversity = findViewById(R.id.til_profile_university);
        tilSemester = findViewById(R.id.til_profile_semester);
        tilDegree = findViewById(R.id.til_profile_degree);
        tilCurrentCourse = findViewById(R.id.til_profile_current_course);
        tilDepartment = findViewById(R.id.til_profile_department);
        tilStudentId = findViewById(R.id.til_profile_student_id);
        tilTargetPercent = findViewById(R.id.til_profile_target_percent);
        tilCampusSection = findViewById(R.id.til_profile_campus_section);

        etName = findViewById(R.id.et_profile_name);
        etUniversity = findViewById(R.id.et_profile_university);
        etSemester = findViewById(R.id.et_profile_semester);
        etDegree = findViewById(R.id.et_profile_degree);
        etCurrentCourse = findViewById(R.id.et_profile_current_course);
        etDepartment = findViewById(R.id.et_profile_department);
        etStudentId = findViewById(R.id.et_profile_student_id);
        etTargetPercent = findViewById(R.id.et_profile_target_percent);
        etCampusSection = findViewById(R.id.et_profile_campus_section);

        btnStartDate = findViewById(R.id.btn_semester_start_date);
        tvStartDate = findViewById(R.id.tv_semester_start_date);
        btnEndDate = findViewById(R.id.btn_semester_end_date);
        tvEndDate = findViewById(R.id.tv_semester_end_date);

        btnSave = findViewById(R.id.btn_profile_save);
        btnBottomSkip = findViewById(R.id.btn_profile_skip_bottom);

        if (isFirstLaunch) {
            btnBack.setVisibility(View.GONE);
            tvScreenTitle.setText("Set Up Your Profile");
            tvScreenSubtitle.setText("Optional • Stored privately on this device");
            btnTopSkip.setVisibility(View.VISIBLE);
            btnBottomSkip.setVisibility(View.VISIBLE);
            btnClearProfile.setVisibility(View.GONE);
            btnSave.setText("Save & Continue");
        } else {
            btnBack.setVisibility(View.VISIBLE);
            tvScreenTitle.setText("Academic Profile");
            tvScreenSubtitle.setText("Encrypted locally on this device");
            btnTopSkip.setVisibility(View.GONE);
            btnBottomSkip.setVisibility(View.GONE);
            btnSave.setText("Save Profile");
        }
    }

    private void applyThemeStyling() {
        int accent = ThemeHelper.getAccentColor(this);
        ThemeHelper.applyAccentToPrimaryButton(btnSave, accent);
        ThemeHelper.applyAccentToTextInputLayout(tilName, accent);
        ThemeHelper.applyAccentToTextInputLayout(tilUniversity, accent);
        ThemeHelper.applyAccentToTextInputLayout(tilSemester, accent);
        ThemeHelper.applyAccentToTextInputLayout(tilDegree, accent);
        ThemeHelper.applyAccentToTextInputLayout(tilCurrentCourse, accent);
        ThemeHelper.applyAccentToTextInputLayout(tilDepartment, accent);
        ThemeHelper.applyAccentToTextInputLayout(tilStudentId, accent);
        ThemeHelper.applyAccentToTextInputLayout(tilTargetPercent, accent);
        ThemeHelper.applyAccentToTextInputLayout(tilCampusSection, accent);
    }

    private void setupAvatarOutline() {
        ivAvatarPhoto.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        ivAvatarPhoto.setClipToOutline(true);
    }

    private void loadExistingProfile() {
        UserProfile existing = SecureProfileStore.loadProfile(this);
        etName.setText(existing.getFullName());
        etUniversity.setText(existing.getUniversityName());
        etSemester.setText(existing.getCurrentSemester());
        etDegree.setText(existing.getMainDegreeCourse());
        etCurrentCourse.setText(existing.getCurrentCourseFocus());
        etDepartment.setText(existing.getDepartment());
        etStudentId.setText(existing.getStudentId());
        etCampusSection.setText(existing.getCampusSection());

        if (existing.getTargetAttendancePercent() > 0) {
            etTargetPercent.setText(String.valueOf(existing.getTargetAttendancePercent()));
        } else {
            etTargetPercent.setText("");
        }

        semesterStartMillis = existing.getSemesterStartDateMillis();
        semesterEndMillis = existing.getSemesterEndDateMillis();
        refreshDateLabels();

        activeAvatarBitmap = SecureProfileStore.loadAvatarBitmap(this);
        avatarChanged = false;
        avatarRemoved = false;

        if (!isFirstLaunch && (existing.hasAnyInfo() || activeAvatarBitmap != null)) {
            btnClearProfile.setVisibility(View.VISIBLE);
        } else {
            btnClearProfile.setVisibility(View.GONE);
        }

        refreshLivePreview();
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnTopSkip.setOnClickListener(v -> handleSkip());
        btnBottomSkip.setOnClickListener(v -> handleSkip());
        btnSave.setOnClickListener(v -> handleSave());
        btnClearProfile.setOnClickListener(v -> confirmClearProfile());

        View.OnClickListener pickPhotoClick = v -> launchPhotoPicker();
        btnAvatarPickArea.setOnClickListener(pickPhotoClick);
        btnUploadPhoto.setOnClickListener(pickPhotoClick);
        btnRemovePhoto.setOnClickListener(v -> {
            activeAvatarBitmap = null;
            avatarChanged = true;
            avatarRemoved = true;
            refreshLivePreview();
        });

        btnStartDate.setOnClickListener(v -> openDatePicker(true));
        btnEndDate.setOnClickListener(v -> openDatePicker(false));

        // Long-press allows clearing a previously selected semester date cleanly
        btnStartDate.setOnLongClickListener(v -> {
            if (semesterStartMillis > 0) {
                semesterStartMillis = 0L;
                refreshDateLabels();
                Toast.makeText(this, "Start date cleared", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        btnEndDate.setOnLongClickListener(v -> {
            if (semesterEndMillis > 0) {
                semesterEndMillis = 0L;
                refreshDateLabels();
                Toast.makeText(this, "End date cleared", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        TextWatcher previewWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshLivePreview();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        etName.addTextChangedListener(previewWatcher);
        etUniversity.addTextChangedListener(previewWatcher);
        etSemester.addTextChangedListener(previewWatcher);
        etDegree.addTextChangedListener(previewWatcher);
        etCurrentCourse.addTextChangedListener(previewWatcher);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isFirstLaunch) {
                    handleSkip();
                } else {
                    finish();
                }
            }
        });
    }

    private void launchPhotoPicker() {
        try {
            PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build();
            pickMediaLauncher.launch(request);
        } catch (Exception e) {
            Toast.makeText(this, "Photo picker unavailable on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void onPhotoPicked(Uri uri) {
        if (uri == null) return;

        AppDatabase.databaseWriteExecutor.execute(() -> {
            Bitmap processed = SecureProfileStore.decodeAndOrientAvatar(ProfileSetupActivity.this, uri);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (processed != null) {
                    activeAvatarBitmap = processed;
                    avatarChanged = true;
                    avatarRemoved = false;
                    refreshLivePreview();
                } else {
                    Toast.makeText(ProfileSetupActivity.this, "Could not load selected image", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void openDatePicker(boolean isStart) {
        long initialSelection = isStart ? semesterStartMillis : semesterEndMillis;
        if (initialSelection <= 0L) {
            initialSelection = MaterialDatePicker.todayInUtcMilliseconds();
        }

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(isStart ? "Semester Starting Date" : "Semester Ending Date")
                .setSelection(initialSelection)
                .setTheme(R.style.ThemeOverlay_ZEN_DatePicker)
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null) return;
            if (isStart) {
                semesterStartMillis = selection;
            } else {
                semesterEndMillis = selection;
            }
            refreshDateLabels();
        });

        picker.show(getSupportFragmentManager(), isStart ? "SEMESTER_START_PICKER" : "SEMESTER_END_PICKER");
    }

    private void refreshDateLabels() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        int primary = ContextCompat.getColor(this, R.color.zen_text_primary);
        int secondary = ContextCompat.getColor(this, R.color.zen_text_secondary);

        if (semesterStartMillis > 0) {
            tvStartDate.setText(sdf.format(new Date(semesterStartMillis)));
            tvStartDate.setTextColor(primary);
        } else {
            tvStartDate.setText("Start Date");
            tvStartDate.setTextColor(secondary);
        }

        if (semesterEndMillis > 0) {
            tvEndDate.setText(sdf.format(new Date(semesterEndMillis)));
            tvEndDate.setTextColor(primary);
        } else {
            tvEndDate.setText("End Date");
            tvEndDate.setTextColor(secondary);
        }
    }

    private void refreshLivePreview() {
        String name = readText(etName);
        String semester = readText(etSemester);
        String degree = readText(etDegree);
        String course = readText(etCurrentCourse);
        String university = readText(etUniversity);

        String initial = name.isEmpty() ? "" : name.substring(0, 1).toUpperCase(Locale.getDefault());
        tvAvatarInitial.setText(initial);

        if (activeAvatarBitmap != null) {
            ivAvatarPhoto.setImageBitmap(activeAvatarBitmap);
            ivAvatarPhoto.setVisibility(View.VISIBLE);
            tvAvatarInitial.setVisibility(View.GONE);
            tvUploadPhotoLabel.setText("Change Photo");
            btnRemovePhoto.setVisibility(View.VISIBLE);
        } else {
            ivAvatarPhoto.setImageDrawable(null);
            ivAvatarPhoto.setVisibility(View.GONE);
            tvAvatarInitial.setVisibility(View.VISIBLE);
            tvUploadPhotoLabel.setText("Upload Photo");
            btnRemovePhoto.setVisibility(View.GONE);
        }

        tvPreviewName.setText(name.isEmpty() ? "Your Name" : name);

        UserProfile previewModel = new UserProfile();
        previewModel.setFullName(name);
        previewModel.setCurrentSemester(semester);
        previewModel.setMainDegreeCourse(degree);
        previewModel.setCurrentCourseFocus(course);
        previewModel.setUniversityName(university);
        String summary = previewModel.getAcademicSummaryLine();
        if (!summary.isEmpty()) {
            tvPreviewMeta.setText(summary);
        } else {
            tvPreviewMeta.setText("Uses first letter if no photo is uploaded");
        }
    }

    private void handleSave() {
        if (semesterStartMillis > 0 && semesterEndMillis > 0 && semesterEndMillis < semesterStartMillis) {
            Toast.makeText(this, "Semester ending date must be after starting date", Toast.LENGTH_SHORT).show();
            return;
        }

        int targetPct = 0;
        String targetStr = readText(etTargetPercent);
        if (!targetStr.isEmpty()) {
            try {
                targetPct = Integer.parseInt(targetStr);
                if (targetPct < 1 || targetPct > 100) {
                    tilTargetPercent.setError("Enter 1–100");
                    return;
                }
                tilTargetPercent.setError(null);
            } catch (NumberFormatException e) {
                tilTargetPercent.setError("Invalid number");
                return;
            }
        } else {
            tilTargetPercent.setError(null);
        }

        UserProfile profile = new UserProfile();
        profile.setFullName(readText(etName));
        profile.setUniversityName(readText(etUniversity));
        profile.setCurrentSemester(readText(etSemester));
        profile.setSemesterStartDateMillis(semesterStartMillis);
        profile.setSemesterEndDateMillis(semesterEndMillis);
        profile.setMainDegreeCourse(readText(etDegree));
        profile.setCurrentCourseFocus(readText(etCurrentCourse));
        profile.setDepartment(readText(etDepartment));
        profile.setStudentId(readText(etStudentId));
        profile.setCampusSection(readText(etCampusSection));
        profile.setTargetAttendancePercent(targetPct);

        boolean profileSaved = SecureProfileStore.saveProfile(this, profile);
        boolean avatarOk = true;
        if (avatarChanged) {
            if (avatarRemoved || activeAvatarBitmap == null) {
                SecureProfileStore.deleteAvatar(this);
            } else {
                avatarOk = SecureProfileStore.saveAvatarBitmap(this, activeAvatarBitmap);
            }
        }

        if (!profileSaved || !avatarOk) {
            Toast.makeText(this, "Could not encrypt profile securely", Toast.LENGTH_SHORT).show();
            return;
        }

        SecureProfileStore.markProfileSetupSeen(this);
        ScheduleSyncPreferences.resetCooldownForProfileChange(this);
        Toast.makeText(this, "Profile encrypted & saved locally", Toast.LENGTH_SHORT).show();

        if (isFirstLaunch) {
            Intent mainIntent = new Intent(this, MainActivity.class);
            startActivity(mainIntent);
        } else {
            setResult(RESULT_OK);
        }
        finish();
    }

    private void handleSkip() {
        SecureProfileStore.markProfileSetupSeen(this);
        if (isFirstLaunch) {
            Intent mainIntent = new Intent(this, MainActivity.class);
            startActivity(mainIntent);
        }
        finish();
    }

    private void confirmClearProfile() {
        UserProfile snapshot = new UserProfile();
        snapshot.setFullName(readText(etName));
        snapshot.setUniversityName(readText(etUniversity));
        snapshot.setCurrentSemester(readText(etSemester));
        snapshot.setMainDegreeCourse(readText(etDegree));
        snapshot.setCurrentCourseFocus(readText(etCurrentCourse));
        String targetStr = readText(etTargetPercent);
        if (!targetStr.isEmpty()) {
            try {
                snapshot.setTargetAttendancePercent(Integer.parseInt(targetStr));
            } catch (NumberFormatException ignored) {
            }
        }

        DeleteConfirmationDialog.showForClearProfile(
                this,
                snapshot,
                activeAvatarBitmap != null,
                () -> {
                    SecureProfileStore.clearProfile(this);
                    activeAvatarBitmap = null;
                    avatarChanged = false;
                    avatarRemoved = false;
                    semesterStartMillis = 0L;
                    semesterEndMillis = 0L;
                    etName.setText("");
                    etUniversity.setText("");
                    etSemester.setText("");
                    etDegree.setText("");
                    etCurrentCourse.setText("");
                    etDepartment.setText("");
                    etStudentId.setText("");
                    etTargetPercent.setText("");
                    etCampusSection.setText("");
                    refreshDateLabels();
                    refreshLivePreview();
                    btnClearProfile.setVisibility(View.GONE);
                    setResult(RESULT_OK);
                    Toast.makeText(this, "Local profile cleared", Toast.LENGTH_SHORT).show();
                }
        );
    }

    private static String readText(TextInputEditText editText) {
        return editText != null && editText.getText() != null
                ? editText.getText().toString().trim()
                : "";
    }
}

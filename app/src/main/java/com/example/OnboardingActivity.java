package com.example;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.example.adapter.OnboardingAdapter;
import com.example.util.ThemeHelper;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * OnboardingActivity delivers a minimalist, distraction-free 3-step introductory flow
 * for first-time ZEN users, seamlessly integrating data migration into the final setup step.
 */
public class OnboardingActivity extends AppCompatActivity implements OnboardingAdapter.OnboardingActionListener {

    private ViewPager2 viewPager;
    private MaterialButton btnSkip;
    private MaterialButton btnNext;
    private View[] indicators;

    private int activeAccentColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Dark system bars matching ZEN deep surface aesthetic (#121212)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.zen_background));
            window.setNavigationBarColor(ContextCompat.getColor(this, R.color.zen_background));
        }

        setContentView(R.layout.activity_onboarding);

        activeAccentColor = ThemeHelper.getAccentColor(this);

        initViews();
        setupSlides();
        setupNavigation();
    }

    private void initViews() {
        viewPager = findViewById(R.id.view_pager_onboarding);
        btnSkip = findViewById(R.id.btn_skip);
        btnNext = findViewById(R.id.btn_next);

        indicators = new View[]{
                findViewById(R.id.indicator_0),
                findViewById(R.id.indicator_1),
                findViewById(R.id.indicator_2)
        };

        // Apply theme accent to Next button
        ThemeHelper.applyAccentToPrimaryButton(btnNext, activeAccentColor);
    }

    private void setupSlides() {
        List<OnboardingAdapter.SlideItem> slides = new ArrayList<>();

        // Slide 1 — Focus & Workspace
        slides.add(new OnboardingAdapter.SlideItem(
                R.drawable.ic_onboarding_workspace,
                R.string.onboarding_slide1_title,
                R.string.onboarding_slide1_desc,
                false
        ));

        // Slide 2 — The Attendance Engine
        slides.add(new OnboardingAdapter.SlideItem(
                R.drawable.ic_onboarding_attendance,
                R.string.onboarding_slide2_title,
                R.string.onboarding_slide2_desc,
                false
        ));

        // Slide 3 — Data Ownership & Setup
        slides.add(new OnboardingAdapter.SlideItem(
                R.drawable.ic_onboarding_offline,
                R.string.onboarding_slide3_title,
                R.string.onboarding_slide3_desc,
                true
        ));

        OnboardingAdapter adapter = new OnboardingAdapter(slides, this);
        viewPager.setAdapter(adapter);

        // Smooth Page Scroll Listener
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                animateIndicators(position);
                updateBottomNavigation(position);
            }
        });
    }

    private void setupNavigation() {
        // Skip advances directly to Slide 3 (Index 2)
        btnSkip.setOnClickListener(v -> viewPager.setCurrentItem(2, true));

        // Next button advances to the subsequent slide
        btnNext.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current < 2) {
                viewPager.setCurrentItem(current + 1, true);
            }
        });
    }

    /**
     * Animates indicator pills to highlight the current active position.
     * The active pill expands into an oblong shape, while inactive dots shrink to a circular dot.
     */
    private void animateIndicators(int activePosition) {
        float density = getResources().getDisplayMetrics().density;
        int activeWidthPx = (int) (28 * density);
        int inactiveWidthPx = (int) (8 * density);

        for (int i = 0; i < indicators.length; i++) {
            final View indicator = indicators[i];
            if (indicator == null) continue;

            boolean isActive = (i == activePosition);
            int targetWidth = isActive ? activeWidthPx : inactiveWidthPx;

            // Value animator for smooth pill expansion/contraction
            int currentWidth = indicator.getWidth() > 0 ? indicator.getWidth() : (isActive ? activeWidthPx : inactiveWidthPx);
            if (currentWidth != targetWidth) {
                ValueAnimator animator = ValueAnimator.ofInt(currentWidth, targetWidth);
                animator.setDuration(220);
                animator.setInterpolator(new DecelerateInterpolator());
                animator.addUpdateListener(animation -> {
                    ViewGroup.LayoutParams params = indicator.getLayoutParams();
                    if (params != null) {
                        params.width = (int) animation.getAnimatedValue();
                        indicator.setLayoutParams(params);
                    }
                });
                animator.start();
            }

            // Update background pill drawable
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(100f * density);
            if (isActive) {
                drawable.setColor(activeAccentColor);
            } else {
                drawable.setColor(ContextCompat.getColor(this, R.color.zen_indicator_inactive));
            }
            indicator.setBackground(drawable);
        }
    }

    /**
     * Adjusts bottom navigation bar visibility based on active slide index.
     * On slides 1 and 2: "Skip" and "Next" are visible.
     * On slide 3: "Skip" and "Next" fade away since dedicated action buttons are embedded on the slide.
     */
    private void updateBottomNavigation(int position) {
        boolean isFinalSlide = (position == 2);

        if (isFinalSlide) {
            btnSkip.animate().alpha(0f).setDuration(180).withEndAction(() -> btnSkip.setVisibility(View.GONE)).start();
            btnNext.animate().alpha(0f).setDuration(180).withEndAction(() -> btnNext.setVisibility(View.GONE)).start();
        } else {
            if (btnSkip.getVisibility() != View.VISIBLE) {
                btnSkip.setVisibility(View.VISIBLE);
                btnSkip.setAlpha(0f);
                btnSkip.animate().alpha(1f).setDuration(180).start();
            }
            if (btnNext.getVisibility() != View.VISIBLE) {
                btnNext.setVisibility(View.VISIBLE);
                btnNext.setAlpha(0f);
                btnNext.animate().alpha(1f).setDuration(180).start();
            }
        }
    }

    /**
     * Completes onboarding by marking preferences and launching MainActivity.
     */
    @Override
    public void onStartFreshClicked() {
        markOnboardingCompleted();

        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * Completes onboarding by marking preferences and launching BackupActivity to import legacy file.
     */
    @Override
    public void onImportBackupClicked() {
        markOnboardingCompleted();

        Intent intent = new Intent(this, BackupActivity.class);
        intent.putExtra("ACTION_MODE", "IMPORT");
        startActivity(intent);
        finish();
    }

    private void markOnboardingCompleted() {
        // App private preferences
        SharedPreferences prefs = getSharedPreferences("zen_prefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(MainActivity.KEY_ONBOARDING_COMPLETED, true)
                .putBoolean("is_first_launch_v9_3_migration", false)
                .apply();

        // Default preferences
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(MainActivity.KEY_ONBOARDING_COMPLETED, true)
                .putBoolean("is_first_launch_v9_3_migration", false)
                .apply();
    }
}

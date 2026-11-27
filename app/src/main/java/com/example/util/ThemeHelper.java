package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.example.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputLayout;

/**
 * ThemeHelper manages user-customizable appearance modes (Zen Light / Zen Dark)
 * in SharedPreferences and provides dynamic tinting utilities for Material Design 3 components.
 */
public class ThemeHelper {

    private static final String PREF_NAME = "zen_theme_preferences";
    private static final String KEY_SELECTED_ACCENT = "selected_accent_color";
    private static final String KEY_DARK_MODE_ENABLED = "dark_mode_enabled";

    // Default single accent colors (Zen Light Deep Olive / Zen Dark Neon Lime)
    public static final String COLOR_MINT_DEFAULT = "#4D7C0F";
    public static final String COLOR_DARK_ACCENT = "#BEF264";
    public static final String COLOR_DEFAULT = COLOR_MINT_DEFAULT;

    public static final String[] PALETTE = {
            COLOR_DEFAULT
    };

    public static boolean isDarkModeEnabled(Context context) {
        if (context == null) return false;
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_DARK_MODE_ENABLED, false);
    }

    public static void setDarkModeEnabled(Context context, boolean darkMode) {
        if (context == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DARK_MODE_ENABLED, darkMode)
                .commit();
        applySavedThemeMode(context);
    }

    public static void applySavedThemeMode(Context context) {
        boolean darkMode = isDarkModeEnabled(context);
        int targetMode = darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode);
        }
    }

    /**
     * Retrieves the active theme accent color as an integer.
     */
    public static int getAccentColor(Context context) {
        if (context != null) {
            return ContextCompat.getColor(context, R.color.zen_accent);
        }
        return Color.parseColor(COLOR_DEFAULT);
    }

    /**
     * Retrieves the active accent color string (HEX).
     */
    public static String getAccentColorHex(Context context) {
        return isDarkModeEnabled(context) ? COLOR_DARK_ACCENT : COLOR_DEFAULT;
    }

    /**
     * No-op: the app uses theme-paired default accent colors.
     */
    public static void setAccentColor(Context context, String hexColor) {
        // App uses paired light/dark accent colors
    }

    /**
     * Dynamically tints the Floating Action Button (FAB) background and icon using Zen Light FAB highlight tokens.
     */
    public static void applyAccentToFab(FloatingActionButton fab, int accentColor) {
        if (fab == null) return;
        int fabBg = ContextCompat.getColor(fab.getContext(), R.color.zen_fab_bg);
        int onFab = ContextCompat.getColor(fab.getContext(), R.color.zen_on_fab);
        fab.setBackgroundTintList(ColorStateList.valueOf(fabBg));
        fab.setImageTintList(ColorStateList.valueOf(onFab));
    }

    /**
     * Dynamically builds a rounded pill background for active navigation tabs
     * (#ECFCCB in Zen Light, #BEF264 in Zen Dark).
     */
    public static GradientDrawable createActiveTabDrawable(int accentColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(1000f); // Auto-clamps to half the height, giving a perfect capsule curve
        int pillBg = (accentColor == Color.parseColor(COLOR_DARK_ACCENT))
                ? Color.parseColor(COLOR_DARK_ACCENT)
                : getAccentContainerColor(accentColor);
        drawable.setColor(pillBg);
        return drawable;
    }

    public static GradientDrawable createActiveTabDrawable(Context context, int accentColor) {
        if (context == null) {
            return createActiveTabDrawable(accentColor);
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(1000f);
        drawable.setColor(ContextCompat.getColor(context, R.color.zen_nav_pill_bg));
        return drawable;
    }

    /**
     * Applies dynamic accent theme to active/inactive navigation tabs.
     */
    public static void applyTabTheme(View tabView, ImageView tabIcon, TextView tabText, boolean isActive, int accentColor) {
        if (tabView == null) return;
        if (isActive) {
            tabView.setBackground(createActiveTabDrawable(tabView.getContext(), accentColor));
            int activeFg = ContextCompat.getColor(tabView.getContext(), R.color.zen_nav_pill_fg);
            if (tabIcon != null) {
                tabIcon.setColorFilter(activeFg);
            }
            if (tabText != null) {
                tabText.setTextColor(activeFg);
            }
        } else {
            tabView.setBackgroundResource(R.drawable.bg_nav_tab_inactive);
            int inactiveColor = ContextCompat.getColor(tabView.getContext(), R.color.zen_nav_inactive);
            if (tabIcon != null) {
                tabIcon.setColorFilter(inactiveColor);
            }
            if (tabText != null) {
                tabText.setTextColor(inactiveColor);
            }
        }
    }

    /**
     * Dynamically tints a badge or counter with a soft lime container background and crisp accent text.
     */
    public static void applyAccentToBadge(TextView badgeView, int accentColor) {
        if (badgeView == null) return;
        badgeView.setTextColor(accentColor);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(20f);
        bg.setColor(getAccentContainerColor(accentColor));
        badgeView.setBackground(bg);
    }

    /**
     * Calculates contrasting text/icon color (dark #131F00 for bright accents, white for deep accents).
     */
    public static int getOnAccentColor(int color) {
        // Luminance calculation
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return (luminance > 0.5) ? Color.parseColor("#131F00") : Color.WHITE;
    }

    /**
     * Generates the soft container color (#ECFCCB for light olive, #233012 for dark lime)
     * for chips, badges, and nav pills.
     */
    public static int getAccentContainerColor(int accentColor) {
        if (accentColor == Color.parseColor(COLOR_MINT_DEFAULT)) {
            return Color.parseColor("#ECFCCB");
        }
        if (accentColor == Color.parseColor(COLOR_DARK_ACCENT)) {
            return Color.parseColor("#233012");
        }
        return Color.argb(35, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor));
    }

    /**
     * Dynamically tints a filled MaterialButton with the accent color.
     */
    public static void applyAccentToPrimaryButton(com.google.android.material.button.MaterialButton button, int accentColor) {
        if (button == null) return;
        button.setBackgroundTintList(ColorStateList.valueOf(accentColor));
        int onAccent = getOnAccentColor(accentColor);
        button.setTextColor(onAccent);
        button.setIconTint(ColorStateList.valueOf(onAccent));
    }

    /**
     * Dynamically tints a RadioButton with the accent color for checked state.
     */
    public static void applyAccentToRadioButton(android.widget.RadioButton radioButton, int accentColor) {
        if (radioButton == null) return;
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        int[] colors = new int[]{
                accentColor,
                Color.parseColor("#71717A")
        };
        radioButton.setButtonTintList(new ColorStateList(states, colors));
    }

    /**
     * Dynamically styles a MaterialSwitch toggle button for both Zen Light and Zen Dark themes.
     */
    public static void applyAccentToSwitch(MaterialSwitch materialSwitch, int accentColor) {
        if (materialSwitch == null) return;
        Context ctx = materialSwitch.getContext();

        int[][] thumbStates = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        int[] thumbColors = new int[]{
                ContextCompat.getColor(ctx, R.color.zen_on_accent),
                ContextCompat.getColor(ctx, R.color.zen_text_tertiary)
        };
        materialSwitch.setThumbTintList(new ColorStateList(thumbStates, thumbColors));

        int[][] trackStates = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        int[] trackColors = new int[]{
                accentColor,
                ContextCompat.getColor(ctx, R.color.zen_border)
        };
        materialSwitch.setTrackTintList(new ColorStateList(trackStates, trackColors));
        materialSwitch.setThumbIconTintList(null);
    }

    /**
     * Dynamically builds a rounded chip background for selected options
     * matching the current accent color (14% alpha container + 1.5dp accent border).
     */
    public static GradientDrawable createSelectedChipDrawable(int accentColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(24f); // 12dp equivalent
        drawable.setColor(getAccentContainerColor(accentColor));
        drawable.setStroke(3, accentColor);
        return drawable;
    }

    /**
     * Applies dynamic theme accent styling to selection chips (e.g. Schedule new Class, Notes, Tasks).
     */
    public static void applyChipSelected(TextView chip, boolean isSelected, int accentColor) {
        if (chip == null) return;
        if (isSelected) {
            chip.setBackground(createSelectedChipDrawable(accentColor));
            chip.setTextColor(accentColor);
        } else {
            chip.setBackgroundResource(R.drawable.bg_chip_unselected);
            chip.setTextColor(ContextCompat.getColor(chip.getContext(), R.color.zen_text_secondary));
        }
    }

    /**
     * Applies dynamic accent colors to TextInputLayout focus box stroke and hint.
     */
    public static void applyAccentToTextInputLayout(TextInputLayout layout, int accentColor) {
        if (layout == null) return;
        layout.setBoxStrokeColor(accentColor);
        layout.setHintTextColor(ColorStateList.valueOf(accentColor));
    }

    /**
     * Generates a dynamic ColorStateList for checkboxes (e.g. Task items) with the active accent color.
     */
    public static ColorStateList createCheckboxTintList(int accentColor) {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        int[] colors = new int[]{
                accentColor,
                Color.parseColor("#71717A")
        };
        return new ColorStateList(states, colors);
    }

    public static boolean isHapticEnabled(Context context) {
        if (context == null) return true;
        SharedPreferences prefs = context.getSharedPreferences("zen_prefs", Context.MODE_PRIVATE);
        return prefs.getBoolean("haptic_feedback_enabled", true);
    }

    public static void setHapticEnabled(Context context, boolean enabled) {
        if (context == null) return;
        context.getSharedPreferences("zen_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("haptic_feedback_enabled", enabled)
                .apply();
    }

    public static void performHaptic(View view) {
        if (view == null) return;
        if (isHapticEnabled(view.getContext())) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    public static long getLastBackupMillis(Context context) {
        if (context == null) return 0L;
        return context.getSharedPreferences("zen_prefs", Context.MODE_PRIVATE)
                .getLong("last_backup_timestamp_ms", 0L);
    }

    public static void recordLastBackupNow(Context context) {
        if (context == null) return;
        context.getSharedPreferences("zen_prefs", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_backup_timestamp_ms", System.currentTimeMillis())
                .apply();
    }
}

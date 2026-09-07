package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.example.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputLayout;

/**
 * ThemeHelper manages user-customizable accent colors in SharedPreferences
 * and provides dynamic tinting utilities for Material Design 3 components.
 */
public class ThemeHelper {

    private static final String PREF_NAME = "zen_theme_preferences";
    private static final String KEY_SELECTED_ACCENT = "selected_accent_color";

    // Supported palette
    public static final String COLOR_MINT_DEFAULT = "#a7ff67";
    public static final String COLOR_NEON_TEAL = "#00f6ac";
    public static final String COLOR_CYAN_BLUE = "#07B7DC";
    public static final String COLOR_SAGE_GREEN = "#BDD0B8";
    public static final String COLOR_ELECTRIC_LIME = "#D0FF00";

    public static final String[] PALETTE = {
            COLOR_MINT_DEFAULT,
            COLOR_NEON_TEAL,
            COLOR_CYAN_BLUE,
            COLOR_SAGE_GREEN,
            COLOR_ELECTRIC_LIME
    };

    /**
     * Retrieves the currently active accent color from SharedPreferences as an integer.
     */
    public static int getAccentColor(Context context) {
        String hex = getAccentColorHex(context);
        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return Color.parseColor(COLOR_MINT_DEFAULT);
        }
    }

    /**
     * Retrieves the currently active accent color string (HEX) from SharedPreferences.
     */
    public static String getAccentColorHex(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SELECTED_ACCENT, COLOR_MINT_DEFAULT);
    }

    /**
     * Persists the selected accent hex string in SharedPreferences.
     */
    public static void setAccentColor(Context context, String hexColor) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SELECTED_ACCENT, hexColor).apply();
    }

    /**
     * Dynamically tints the Floating Action Button (FAB) background and icon.
     */
    public static void applyAccentToFab(FloatingActionButton fab, int accentColor) {
        if (fab == null) return;
        fab.setBackgroundTintList(ColorStateList.valueOf(accentColor));
        int onAccentColor = getOnAccentColor(accentColor);
        fab.setImageTintList(ColorStateList.valueOf(onAccentColor));
    }

    /**
     * Dynamically builds a rounded pill background for active navigation tabs.
     */
    public static GradientDrawable createActiveTabDrawable(int accentColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(28f); // High corner radius for smooth pill shape
        drawable.setColor(accentColor);
        return drawable;
    }

    /**
     * Applies dynamic accent theme to active/inactive navigation tabs.
     */
    public static void applyTabTheme(View tabView, ImageView tabIcon, TextView tabText, boolean isActive, int accentColor) {
        if (tabView == null) return;
        if (isActive) {
            tabView.setBackground(createActiveTabDrawable(accentColor));
            int onAccent = getOnAccentColor(accentColor);
            if (tabIcon != null) {
                tabIcon.setColorFilter(onAccent);
            }
            if (tabText != null) {
                tabText.setTextColor(onAccent);
            }
        } else {
            tabView.setBackgroundResource(R.drawable.bg_nav_tab_inactive);
            int secondaryColor = ContextCompat.getColor(tabView.getContext(), R.color.zen_text_secondary);
            if (tabIcon != null) {
                tabIcon.setColorFilter(secondaryColor);
            }
            if (tabText != null) {
                tabText.setTextColor(secondaryColor);
            }
        }
    }

    /**
     * Dynamically tints a badge or counter with a 15% opacity container background and solid text.
     */
    public static void applyAccentToBadge(TextView badgeView, int accentColor) {
        if (badgeView == null) return;
        badgeView.setTextColor(accentColor);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(20f);
        // Container background with low alpha
        int alpha = 35; // ~14% opacity
        int containerColor = Color.argb(alpha, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor));
        bg.setColor(containerColor);
        badgeView.setBackground(bg);
    }

    /**
     * Calculates contrasting text/icon color (dark #121212 for bright accents, white for very dark accents).
     */
    public static int getOnAccentColor(int color) {
        // Luminance calculation
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return (luminance > 0.5) ? Color.parseColor("#121212") : Color.WHITE;
    }

    /**
     * Generates a 14% opacity container color for secondary chips/cards from the accent color.
     */
    public static int getAccentContainerColor(int accentColor) {
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
     * Dynamically styles a MaterialSwitch toggle button:
     * - When ON (Checked): The Dot (thumb) is solid Black (#121212) to strongly contrast and differ
     *   from the bright accent track (Mint, Teal, Cyan, Sage, Lime). The Track is tinted with the active accent color.
     * - When OFF (Unchecked): The Dot is muted gray (#8E8E93) and the Track is dark surface (#2A2A2A).
     */
    public static void applyAccentToSwitch(MaterialSwitch materialSwitch, int accentColor) {
        if (materialSwitch == null) return;

        // Thumb ("Dot"): Black when ON, Muted Gray when OFF
        int[][] thumbStates = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        int[] thumbColors = new int[]{
                Color.parseColor("#121212"), // Black dot when ON
                Color.parseColor("#8E8E93")  // Muted gray dot when OFF
        };
        materialSwitch.setThumbTintList(new ColorStateList(thumbStates, thumbColors));

        // Track (Capsule background): Accent color when ON, Dark surface when OFF
        int[][] trackStates = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        int[] trackColors = new int[]{
                accentColor,                 // Dynamic Accent track when ON
                Color.parseColor("#2A2A2A")  // Dark surface track when OFF
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
}

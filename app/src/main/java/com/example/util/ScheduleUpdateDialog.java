package com.example.util;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.R;
import com.example.model.ScheduleSyncModels.ScheduleUpdatePreview;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;

/**
 * Displays the verified section schedule update modal with SHA-256 verification status,
 * version transition pill, diff counts, preview lines, and Skip / Update actions.
 */
public final class ScheduleUpdateDialog {

    public interface Listener {
        void onConfirmUpdate(ScheduleUpdatePreview preview);
        void onSkipVersion(ScheduleUpdatePreview preview);
    }

    private ScheduleUpdateDialog() {
    }

    public static Dialog show(
            @NonNull Activity activity,
            @NonNull ScheduleUpdatePreview preview,
            @NonNull Listener listener) {

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_schedule_update);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.72f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                    window.getAttributes().setBlurBehindRadius(20);
                } catch (Exception ignored) {
                }
            }
        }

        TextView tvVerified = dialog.findViewById(R.id.tv_sched_update_verified);
        TextView tvSectionLabel = dialog.findViewById(R.id.tv_sched_update_section_label);
        TextView tvVersionPill = dialog.findViewById(R.id.tv_sched_update_version_pill);
        TextView tvChangelog = dialog.findViewById(R.id.tv_sched_update_changelog);
        TextView tvAddedCount = dialog.findViewById(R.id.tv_sched_added_count);
        TextView tvUpdatedCount = dialog.findViewById(R.id.tv_sched_updated_count);
        TextView tvRemovedCount = dialog.findViewById(R.id.tv_sched_removed_count);
        LinearLayout diffContainer = dialog.findViewById(R.id.container_sched_diff_lines);
        ImageButton btnLater = dialog.findViewById(R.id.btn_sched_update_later);
        MaterialButton btnSkip = dialog.findViewById(R.id.btn_sched_skip);
        MaterialButton btnApply = dialog.findViewById(R.id.btn_sched_apply);

        String shortSha = preview.plaintextSha256.length() >= 8
                ? preview.plaintextSha256.substring(0, 8).toUpperCase(Locale.US)
                : "OK";
        tvVerified.setText("Verified • SHA-256 " + shortSha);

        tvSectionLabel.setText(preview.indexEntry != null ? preview.indexEntry.getDisplayLabel() : "Section Schedule");

        if (preview.fromVersion != null && !preview.fromVersion.isEmpty()
                && !preview.fromVersion.equalsIgnoreCase("Manual / None")) {
            tvVersionPill.setText(preview.fromVersion + " → " + preview.toVersion);
        } else {
            tvVersionPill.setText(preview.toVersion);
        }

        String changelog = preview.verifiedPackage != null ? preview.verifiedPackage.changelog : "";
        if (changelog != null && !changelog.trim().isEmpty()) {
            tvChangelog.setText(changelog.trim());
            tvChangelog.setVisibility(View.VISIBLE);
        } else {
            tvChangelog.setVisibility(View.GONE);
        }

        int added = preview.slotsToInsert.size();
        int updated = preview.slotsToUpdate.size();
        int removed = preview.slotsToDelete.size();

        tvAddedCount.setText(String.valueOf(added));
        tvAddedCount.setTextColor(added > 0 ? Color.WHITE : Color.parseColor("#D4D4D8"));
        tvUpdatedCount.setText(String.valueOf(updated));
        tvUpdatedCount.setTextColor(updated > 0 ? Color.WHITE : Color.parseColor("#D4D4D8"));
        tvRemovedCount.setText(String.valueOf(removed));
        tvRemovedCount.setTextColor(removed > 0 ? Color.WHITE : Color.parseColor("#D4D4D8"));

        diffContainer.removeAllViews();
        List<String> lines = preview.diffSummaryLines;
        if (lines != null && !lines.isEmpty()) {
            diffContainer.setVisibility(View.VISIBLE);
            int maxShown = Math.min(lines.size(), 4);
            for (int i = 0; i < maxShown; i++) {
                String line = lines.get(i);
                TextView tvLine = new TextView(activity);
                tvLine.setText(line);
                tvLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                tvLine.setMaxLines(1);
                tvLine.setIncludeFontPadding(false);
                if (line.startsWith("+")) {
                    tvLine.setTextColor(Color.parseColor("#34D399"));
                } else if (line.startsWith("~")) {
                    tvLine.setTextColor(Color.parseColor("#FBBF24"));
                } else {
                    tvLine.setTextColor(Color.parseColor("#FB7185"));
                }
                if (i > 0) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    lp.topMargin = Math.round(TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 4, activity.getResources().getDisplayMetrics()));
                    tvLine.setLayoutParams(lp);
                }
                diffContainer.addView(tvLine);
            }
            if (lines.size() > maxShown) {
                TextView tvMore = new TextView(activity);
                tvMore.setText(String.format(Locale.getDefault(), "+%d more schedule changes", lines.size() - maxShown));
                tvMore.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
                tvMore.setTextColor(Color.parseColor("#A1A1AA"));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                lp.topMargin = Math.round(TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 4, activity.getResources().getDisplayMetrics()));
                tvMore.setLayoutParams(lp);
                diffContainer.addView(tvMore);
            }
        } else {
            diffContainer.setVisibility(View.GONE);
        }

        ThemeHelper.applyAccentToPrimaryButton(btnApply, ThemeHelper.getAccentColor(activity));

        btnLater.setOnClickListener(v -> dialog.dismiss());
        btnSkip.setOnClickListener(v -> {
            ThemeHelper.performHaptic(v);
            dialog.dismiss();
            listener.onSkipVersion(preview);
        });
        btnApply.setOnClickListener(v -> {
            ThemeHelper.performHaptic(v);
            dialog.dismiss();
            listener.onConfirmUpdate(preview);
        });

        dialog.show();
        return dialog;
    }
}

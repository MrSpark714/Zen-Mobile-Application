package com.example.util;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.Html;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.R;
import com.example.model.ClassSchedule;
import com.example.model.Note;
import com.example.model.Task;
import com.example.model.UserProfile;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Unified destructive confirmation modal matching the ZEN Material 3 delete confirmation design.
 * Used across Notes, Tasks, Class Schedules, Note Editor, and Clear Local Profile.
 */
public final class DeleteConfirmationDialog {

    private DeleteConfirmationDialog() {
    }

    public static void show(
            @NonNull Context context,
            @NonNull String title,
            @NonNull String message,
            @Nullable String previewTitle,
            @Nullable String previewBadge,
            @DrawableRes int badgeBackgroundRes,
            int badgeTextColor,
            @Nullable String metaLeft,
            @Nullable String metaRight,
            @Nullable String confirmButtonText,
            @Nullable Runnable onConfirm
    ) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirm_delete);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.48f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                    window.getAttributes().setBlurBehindRadius(20);
                } catch (Exception ignored) {
                }
            }
        }

        TextView tvTitle = dialog.findViewById(R.id.tv_delete_popup_title);
        TextView tvMessage = dialog.findViewById(R.id.tv_delete_popup_message);
        LinearLayout previewCard = dialog.findViewById(R.id.layout_delete_preview_card);
        TextView tvPreviewTitle = dialog.findViewById(R.id.tv_delete_item_preview);
        TextView tvPreviewBadge = dialog.findViewById(R.id.tv_delete_preview_badge);
        LinearLayout metaContainer = dialog.findViewById(R.id.layout_delete_preview_meta);
        TextView tvMetaLeft = dialog.findViewById(R.id.tv_delete_preview_meta_left);
        TextView tvMetaRight = dialog.findViewById(R.id.tv_delete_preview_meta_right);
        MaterialButton btnCancel = dialog.findViewById(R.id.btn_popup_delete_cancel);
        MaterialButton btnConfirm = dialog.findViewById(R.id.btn_popup_delete_confirm);

        tvTitle.setText(title);
        tvMessage.setText(message);

        if (!TextUtils.isEmpty(previewTitle)) {
            previewCard.setVisibility(View.VISIBLE);
            tvPreviewTitle.setText(previewTitle.trim());

            if (!TextUtils.isEmpty(previewBadge)) {
                tvPreviewBadge.setVisibility(View.VISIBLE);
                tvPreviewBadge.setText(previewBadge);
                if (badgeBackgroundRes != 0) {
                    tvPreviewBadge.setBackgroundResource(badgeBackgroundRes);
                }
                if (badgeTextColor != 0) {
                    tvPreviewBadge.setTextColor(badgeTextColor);
                }
            } else {
                tvPreviewBadge.setVisibility(View.GONE);
            }

            boolean hasLeft = !TextUtils.isEmpty(metaLeft);
            boolean hasRight = !TextUtils.isEmpty(metaRight);
            if (hasLeft || hasRight) {
                metaContainer.setVisibility(View.VISIBLE);
                tvMetaLeft.setText(hasLeft ? metaLeft : "");
                tvMetaLeft.setVisibility(hasLeft ? View.VISIBLE : View.INVISIBLE);
                tvMetaRight.setText(hasRight ? metaRight : "");
                tvMetaRight.setVisibility(hasRight ? View.VISIBLE : View.GONE);
            } else {
                metaContainer.setVisibility(View.GONE);
            }
        } else {
            previewCard.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(confirmButtonText)) {
            btnConfirm.setText(confirmButtonText);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            ThemeHelper.performHaptic(v);
            dialog.dismiss();
            if (onConfirm != null) {
                onConfirm.run();
            }
        });

        dialog.show();
    }

    public static void showSimple(
            @NonNull Context context,
            @NonNull String title,
            @NonNull String message,
            @Nullable String itemPreview,
            @Nullable Runnable onConfirm
    ) {
        show(
                context,
                title,
                message,
                itemPreview,
                null,
                R.drawable.bg_pill_preview_lime,
                ContextCompat.getColor(context, R.color.zen_accent),
                null,
                null,
                context.getString(R.string.delete),
                onConfirm
        );
    }

    public static void showForNote(
            @NonNull Context context,
            @Nullable Note note,
            @Nullable String fallbackTitle,
            @Nullable String fallbackBody,
            boolean fallbackImportant,
            long fallbackTimestamp,
            @Nullable Runnable onConfirm
    ) {
        String titleText = note != null && !TextUtils.isEmpty(note.getTitle())
                ? note.getTitle().trim()
                : (!TextUtils.isEmpty(fallbackTitle) ? fallbackTitle.trim() : "Untitled Note");

        boolean important = note != null ? note.isImportant() : fallbackImportant;
        String rawBody = note != null && note.getContent() != null ? note.getContent() : (fallbackBody != null ? fallbackBody : "");
        long ts = note != null && note.getTimestamp() > 0 ? note.getTimestamp() : fallbackTimestamp;

        String badge = null;
        int badgeBg = R.drawable.bg_pill_preview_lime;
        int badgeColor = ContextCompat.getColor(context, R.color.zen_accent);
        if (important) {
            badge = "★ Important";
        } else if (note != null && !TextUtils.isEmpty(note.getTargetDate())) {
            badge = note.getTargetDate();
            badgeBg = R.drawable.bg_pill_surface_outline;
            badgeColor = ContextCompat.getColor(context, R.color.zen_text_secondary);
        }

        String metaLeft = summarizeNoteBody(rawBody);
        String metaRight = ts > 0 ? new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date(ts)) : "";

        show(
                context,
                "Delete Note?",
                "This note will be permanently removed from your local ZEN vault. This action cannot be undone.",
                titleText,
                badge,
                badgeBg,
                badgeColor,
                metaLeft,
                metaRight,
                context.getString(R.string.delete),
                onConfirm
        );
    }

    public static void showForTask(
            @NonNull Context context,
            @NonNull Task task,
            @Nullable Runnable onConfirm
    ) {
        String desc = !TextUtils.isEmpty(task.getDescription()) ? task.getDescription().trim() : "Untitled Task";
        String badge = task.isCompleted() ? "Completed" : "Pending";
        int badgeBg = task.isCompleted() ? R.drawable.bg_pill_surface_outline : R.drawable.bg_pill_preview_lime;
        int badgeColor = task.isCompleted()
                ? ContextCompat.getColor(context, R.color.zen_text_secondary)
                : ContextCompat.getColor(context, R.color.zen_accent);

        String metaLeft;
        if (task.hasTimeWindow()) {
            SimpleDateFormat timeFmt = new SimpleDateFormat("MMM d • h:mm a", Locale.getDefault());
            metaLeft = "Due " + timeFmt.format(new Date(task.getEndTime()));
        } else {
            metaLeft = task.isCompleted() ? "Marked completed" : "No due time set";
        }

        String metaRight = "";
        if (task.getDurationMinutes() > 0) {
            int mins = task.getDurationMinutes();
            metaRight = mins >= 60
                    ? (mins / 60) + "h" + (mins % 60 > 0 ? " " + (mins % 60) + "m" : "")
                    : mins + "m";
        } else if (task.getTimestamp() > 0) {
            metaRight = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date(task.getTimestamp()));
        }

        show(
                context,
                "Delete Task?",
                "This task will be permanently removed from your local ZEN vault. This action cannot be undone.",
                desc,
                badge,
                badgeBg,
                badgeColor,
                metaLeft,
                metaRight,
                context.getString(R.string.delete),
                onConfirm
        );
    }

    public static void showForSchedule(
            @NonNull Context context,
            @NonNull ClassSchedule schedule,
            @Nullable Runnable onConfirm
    ) {
        String subject = !TextUtils.isEmpty(schedule.getSubjectName()) ? schedule.getSubjectName().trim() : "Class Schedule";
        boolean isLab = "Lab".equalsIgnoreCase(schedule.getClassType());
        String badge = isLab ? "Lab" : "Theory";
        int badgeBg = isLab ? R.drawable.bg_type_lab : R.drawable.bg_type_theory;
        int badgeColor = ContextCompat.getColor(context, isLab ? R.color.type_lab_text : R.color.type_theory_text);

        String day = !TextUtils.isEmpty(schedule.getDayOfWeek()) ? schedule.getDayOfWeek() : "";
        String timeRange = (!TextUtils.isEmpty(schedule.getStartTime()) ? schedule.getStartTime() : "")
                + (!TextUtils.isEmpty(schedule.getEndTime()) ? " - " + schedule.getEndTime() : "");
        String metaLeft = (!day.isEmpty() && !timeRange.isEmpty())
                ? day + " • " + timeRange
                : (!day.isEmpty() ? day : timeRange);

        String rawRoom = schedule.getRoomNumber();
        String roomLabel = "";
        if (!TextUtils.isEmpty(rawRoom)) {
            String trimmed = rawRoom.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            roomLabel = (lower.startsWith("room") || lower.startsWith("romm") || lower.startsWith("lab") || lower.startsWith("hall") || lower.equals("tba"))
                    ? trimmed
                    : "Room # " + trimmed;
        }
        String credits = schedule.getCreditHours() > 0 ? schedule.getCreditHours() + " Cr" : "";
        String metaRight = (!roomLabel.isEmpty() && !credits.isEmpty())
                ? roomLabel + " • " + credits
                : (!roomLabel.isEmpty() ? roomLabel : credits);

        show(
                context,
                "Remove Class?",
                "This class will be permanently removed from your local weekly timetable. This action cannot be undone.",
                subject,
                badge,
                badgeBg,
                badgeColor,
                metaLeft,
                metaRight,
                context.getString(R.string.delete),
                onConfirm
        );
    }

    public static void showForClearProfile(
            @NonNull Context context,
            @NonNull UserProfile profile,
            boolean hasPhoto,
            @Nullable Runnable onConfirm
    ) {
        String previewName = !profile.getFullName().isEmpty()
                ? profile.getFullName()
                : "Local Academic Profile";
        String summary = profile.getAcademicSummaryLine();
        String metaLeft = !summary.isEmpty()
                ? summary
                : (hasPhoto ? "Personal photo & local vault" : "Stored privately on device");
        String metaRight = "Target " + profile.getEffectiveTargetPercent() + "%";

        show(
                context,
                "Clear Local Profile",
                "Your personal photo and academic profile details will be permanently removed from encrypted local storage. This action cannot be undone.",
                previewName,
                "Encrypted",
                R.drawable.bg_pill_preview_lime,
                ContextCompat.getColor(context, R.color.zen_accent),
                metaLeft,
                metaRight,
                context.getString(R.string.delete),
                onConfirm
        );
    }

    private static String summarizeNoteBody(String rawBody) {
        if (TextUtils.isEmpty(rawBody)) {
            return "Empty note";
        }
        String plain;
        if (rawBody.contains("<") && rawBody.contains(">")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                plain = Html.fromHtml(rawBody, Html.FROM_HTML_MODE_COMPACT).toString();
            } else {
                plain = Html.fromHtml(rawBody).toString();
            }
        } else {
            plain = rawBody;
        }
        String trimmed = plain.trim();
        if (trimmed.isEmpty()) {
            return "Empty note";
        }
        String[] lines = trimmed.split("\\r?\\n");
        int nonEmptyLines = 0;
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                nonEmptyLines++;
            }
        }
        if (nonEmptyLines > 1) {
            return nonEmptyLines + " items in note";
        }
        return trimmed.replace('\n', ' ');
    }
}

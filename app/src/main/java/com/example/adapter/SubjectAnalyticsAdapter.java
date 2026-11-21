package com.example.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.SubjectStats;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView Adapter for Subject Attendance Analytics Dashboard.
 *
 * Math Rule: Attendance % = (Total Present / (Total Present + Total Absent)) * 100.
 * Holiday sessions are tracked and displayed in the breakdown, but excluded from percentage math.
 */
public class SubjectAnalyticsAdapter extends RecyclerView.Adapter<SubjectAnalyticsAdapter.AnalyticsViewHolder> {

    public interface OnSubjectClickListener {
        void onSubjectClick(SubjectStats stats);
    }

    private final Context context;
    private final List<SubjectStats> statsList = new ArrayList<>();
    private final OnSubjectClickListener listener;
    private int minTargetPercent = 75;

    public SubjectAnalyticsAdapter(Context context, OnSubjectClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setMinTargetPercent(int minTargetPercent) {
        this.minTargetPercent = (minTargetPercent >= 1 && minTargetPercent <= 100) ? minTargetPercent : 75;
        notifyDataSetChanged();
    }

    public void setStats(List<SubjectStats> items) {
        this.statsList.clear();
        if (items != null) {
            this.statsList.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AnalyticsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_subject_analytics, parent, false);
        return new AnalyticsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AnalyticsViewHolder holder, int position) {
        SubjectStats stats = statsList.get(position);

        holder.tvSubjectName.setText(stats.getSubjectName());

        // Type pill ("Theory" / "Lab")
        boolean isLab = "Lab".equalsIgnoreCase(stats.getClassType());
        holder.tvTypeBadge.setText(isLab ? "Lab" : "Theory");
        holder.tvTypeBadge.setBackgroundResource(isLab ? R.drawable.bg_type_lab : R.drawable.bg_type_theory);
        holder.tvTypeBadge.setTextColor(ContextCompat.getColor(context,
                isLab ? R.color.type_lab_text : R.color.type_theory_text));

        // Credit hours metadata
        int credits = stats.getCreditHours();
        holder.tvCredits.setText(String.format(Locale.getDefault(),
                "• %d %s", credits, credits == 1 ? "Credit" : "Credits"));

        double percentage = stats.getAttendancePercentage();
        int totalEffective = stats.getEffectiveTotal(); // Present + Absent

        holder.tvTotalCount.setText(String.format(Locale.getDefault(), "%d total", totalEffective));

        int accentLime = ContextCompat.getColor(context, R.color.zen_accent);
        int cyanColor = ContextCompat.getColor(context, R.color.zen_secondary);
        int dangerColor = ContextCompat.getColor(context, R.color.zen_danger);
        int mutedColor = ContextCompat.getColor(context, R.color.zen_text_tertiary);

        if (totalEffective == 0) {
            holder.tvPercentage.setText("—");
            holder.tvPercentage.setTextColor(mutedColor);
            holder.progressBar.setProgress(0);
            holder.progressBar.setProgressTintList(ColorStateList.valueOf(mutedColor));
            holder.layoutThresholdBadge.setVisibility(View.GONE);
        } else {
            if (Math.abs(percentage - 100.0) < 0.05) {
                holder.tvPercentage.setText("100%");
            } else {
                holder.tvPercentage.setText(String.format(Locale.getDefault(), "%.1f%%", percentage));
            }
            holder.progressBar.setProgress((int) Math.min(100, Math.max(0, Math.round(percentage))));

            double target = (double) minTargetPercent;
            if (percentage < target - 0.05) {
                holder.tvPercentage.setTextColor(dangerColor);
                holder.progressBar.setProgressTintList(ColorStateList.valueOf(dangerColor));

                holder.layoutThresholdBadge.setVisibility(View.VISIBLE);
                holder.layoutThresholdBadge.setBackgroundResource(R.drawable.bg_pill_danger_soft);
                holder.viewThresholdDot.setBackgroundResource(R.drawable.bg_dot_danger);
                holder.tvThresholdLabel.setText(String.format(Locale.getDefault(), "Below %d%% Threshold", minTargetPercent));
                holder.tvThresholdLabel.setTextColor(ContextCompat.getColor(context, R.color.zen_on_error_container));
            } else if (Math.abs(percentage - target) <= 0.05) {
                holder.tvPercentage.setTextColor(cyanColor);
                holder.progressBar.setProgressTintList(ColorStateList.valueOf(cyanColor));

                holder.layoutThresholdBadge.setVisibility(View.VISIBLE);
                holder.layoutThresholdBadge.setBackgroundResource(R.drawable.bg_pill_highest);
                holder.viewThresholdDot.setBackgroundResource(R.drawable.bg_dot_cyan);
                holder.tvThresholdLabel.setText(String.format(Locale.getDefault(), "At %d%% Minimum Threshold", minTargetPercent));
                holder.tvThresholdLabel.setTextColor(ContextCompat.getColor(context, R.color.zen_text_primary));
            } else {
                holder.tvPercentage.setTextColor(accentLime);
                holder.progressBar.setProgressTintList(ColorStateList.valueOf(accentLime));
                holder.layoutThresholdBadge.setVisibility(View.GONE);
            }
        }

        // Breakdown row with semantic coral highlight on Absent when at/below threshold
        String presentStr = stats.getPresentCount() + " Present";
        String sep = "  •  ";
        String absentStr = stats.getAbsentCount() + " Absent";
        String holidayStr = stats.getHolidayCount() + " Holiday";

        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(presentStr).append(sep);
        int absentStart = sb.length();
        sb.append(absentStr);
        int absentEnd = sb.length();
        sb.append(sep).append(holidayStr);

        if (totalEffective > 0 && percentage <= minTargetPercent + 0.05 && stats.getAbsentCount() > 0) {
            sb.setSpan(new ForegroundColorSpan(dangerColor), absentStart, absentEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        holder.tvBreakdown.setText(sb);

        holder.cardView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSubjectClick(stats);
            }
        });
    }

    @Override
    public int getItemCount() {
        return statsList.size();
    }

    static class AnalyticsViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        TextView tvTypeBadge;
        TextView tvCredits;
        TextView tvSubjectName;
        TextView tvPercentage;
        TextView tvTotalCount;
        ProgressBar progressBar;
        View layoutThresholdBadge;
        View viewThresholdDot;
        TextView tvThresholdLabel;
        TextView tvBreakdown;

        public AnalyticsViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_subject_analytics);
            tvTypeBadge = itemView.findViewById(R.id.tv_analytics_type_badge);
            tvCredits = itemView.findViewById(R.id.tv_analytics_credits);
            tvSubjectName = itemView.findViewById(R.id.tv_analytics_subject_name);
            tvPercentage = itemView.findViewById(R.id.tv_analytics_percentage);
            tvTotalCount = itemView.findViewById(R.id.tv_analytics_total_count);
            progressBar = itemView.findViewById(R.id.progress_attendance);
            layoutThresholdBadge = itemView.findViewById(R.id.layout_threshold_badge);
            viewThresholdDot = itemView.findViewById(R.id.view_threshold_dot);
            tvThresholdLabel = itemView.findViewById(R.id.tv_threshold_label);
            tvBreakdown = itemView.findViewById(R.id.tv_analytics_breakdown);
        }
    }
}

package com.example.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
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
 * RecyclerView Adapter for Subject Attendance Analytics & Summary Dashboard.
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

    public SubjectAnalyticsAdapter(Context context, OnSubjectClickListener listener) {
        this.context = context;
        this.listener = listener;
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

        double percentage = stats.getAttendancePercentage();
        int totalEffective = stats.getEffectiveTotal(); // Present + Absent

        if (totalEffective == 0) {
            holder.tvPercentage.setText("—");
            holder.tvPercentage.setTextColor(ContextCompat.getColor(context, R.color.zen_text_secondary));
            holder.tvPercentage.setBackgroundResource(R.drawable.bg_chip_unselected);
            holder.progressBar.setProgress(0);
            holder.progressBar.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.zen_text_tertiary)));
        } else {
            holder.tvPercentage.setText(String.format(Locale.getDefault(), "%.1f%%", percentage));
            holder.progressBar.setProgress((int) Math.round(percentage));

            // Dynamic theme tinting: >= 75% mint, >= 60% amber, < 60% red
            int color;
            if (percentage >= 75.0) {
                color = ContextCompat.getColor(context, R.color.status_present);
            } else if (percentage >= 60.0) {
                color = ContextCompat.getColor(context, R.color.status_holiday);
            } else {
                color = ContextCompat.getColor(context, R.color.status_absent);
            }

            holder.tvPercentage.setTextColor(color);
            holder.tvPercentage.setBackgroundResource(R.drawable.bg_badge);
            holder.progressBar.setProgressTintList(ColorStateList.valueOf(color));
        }

        // Breakdown text
        String breakdown = stats.getPresentCount() + " Present • " +
                stats.getAbsentCount() + " Absent • " +
                stats.getHolidayCount() + " Holiday";
        holder.tvBreakdown.setText(breakdown);

        // Click to view history
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
        TextView tvSubjectName;
        TextView tvPercentage;
        ProgressBar progressBar;
        TextView tvBreakdown;

        public AnalyticsViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_subject_analytics);
            tvSubjectName = itemView.findViewById(R.id.tv_analytics_subject_name);
            tvPercentage = itemView.findViewById(R.id.tv_analytics_percentage);
            progressBar = itemView.findViewById(R.id.progress_attendance);
            tvBreakdown = itemView.findViewById(R.id.tv_analytics_breakdown);
        }
    }
}

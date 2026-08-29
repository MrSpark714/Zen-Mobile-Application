package com.example.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.ClassSchedule;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView Adapter for Weekly Class Schedules in the ZEN Attendance Tracker.
 *
 * Supports swipe-to-delete/edit or button actions.
 */
public class WeeklyScheduleAdapter extends RecyclerView.Adapter<WeeklyScheduleAdapter.ScheduleViewHolder> {

    public interface OnScheduleActionListener {
        void onEditSchedule(ClassSchedule schedule);
        void onDeleteSchedule(ClassSchedule schedule);
    }

    private final Context context;
    private final List<ClassSchedule> scheduleList = new ArrayList<>();
    private final OnScheduleActionListener listener;

    public WeeklyScheduleAdapter(Context context, OnScheduleActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setSchedules(List<ClassSchedule> items) {
        this.scheduleList.clear();
        if (items != null) {
            this.scheduleList.addAll(items);
        }
        notifyDataSetChanged();
    }

    public ClassSchedule getItemAt(int position) {
        if (position >= 0 && position < scheduleList.size()) {
            return scheduleList.get(position);
        }
        return null;
    }

    @NonNull
    @Override
    public ScheduleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_schedule_class, parent, false);
        return new ScheduleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScheduleViewHolder holder, int position) {
        ClassSchedule schedule = scheduleList.get(position);

        holder.tvSubjectName.setText(schedule.getSubjectName());

        // Day of Week Short Name
        String day = schedule.getDayOfWeek();
        if (day != null && day.length() >= 3) {
            holder.tvDayBadge.setText(day.substring(0, 3).toUpperCase());
        } else {
            holder.tvDayBadge.setText(day != null ? day.toUpperCase() : "MON");
        }

        // Class Type Badge
        String classType = schedule.getClassType();
        holder.tvTypeBadge.setText(classType.toUpperCase());
        if ("Lab".equalsIgnoreCase(classType)) {
            holder.tvTypeBadge.setBackgroundResource(R.drawable.bg_type_lab);
            holder.tvTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.type_lab_text));
        } else {
            holder.tvTypeBadge.setBackgroundResource(R.drawable.bg_type_theory);
            holder.tvTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.type_theory_text));
        }

        // Timing
        String timeStr = schedule.getStartTime();
        if (schedule.getEndTime() != null && !schedule.getEndTime().trim().isEmpty()) {
            timeStr += " - " + schedule.getEndTime();
        }
        holder.tvTime.setText(timeStr);

        // Room
        String room = schedule.getRoomNumber();
        if (room == null || room.trim().isEmpty()) {
            room = "TBA";
        }
        holder.tvRoom.setText(room);

        // Credits
        holder.tvCredits.setText(schedule.getCreditHours() + " Cr");

        // Action Listeners
        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditSchedule(schedule);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteSchedule(schedule);
            }
        });
    }

    @Override
    public int getItemCount() {
        return scheduleList.size();
    }

    static class ScheduleViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        TextView tvDayBadge;
        TextView tvTypeBadge;
        TextView tvSubjectName;
        TextView tvTime;
        TextView tvRoom;
        TextView tvCredits;
        ImageButton btnEdit;
        ImageButton btnDelete;

        public ScheduleViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_schedule_item);
            tvDayBadge = itemView.findViewById(R.id.tv_schedule_day_badge);
            tvTypeBadge = itemView.findViewById(R.id.tv_schedule_type_badge);
            tvSubjectName = itemView.findViewById(R.id.tv_schedule_subject_name);
            tvTime = itemView.findViewById(R.id.tv_schedule_time);
            tvRoom = itemView.findViewById(R.id.tv_schedule_room);
            tvCredits = itemView.findViewById(R.id.tv_schedule_credits);
            btnEdit = itemView.findViewById(R.id.btn_edit_schedule);
            btnDelete = itemView.findViewById(R.id.btn_delete_schedule);
        }
    }
}

package com.example.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.AttendanceHistory;
import com.example.model.ClassSchedule;
import com.example.model.ClassWithTodayStatus;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView Adapter for Today's Classes in the ZEN Attendance Tracker.
 *
 * Provides immediate feedback for marking "Present", "Absent", and "Holiday".
 */
public class TodayClassAdapter extends RecyclerView.Adapter<TodayClassAdapter.TodayClassViewHolder> {

    public interface OnAttendanceActionListener {
        void onMarkAttendance(ClassSchedule schedule, String status);
        void onMarkAttendance(String subjectName, int creditHours, String classType, String status);
    }

    private final Context context;
    private final List<ClassWithTodayStatus> classList = new ArrayList<>();
    private final OnAttendanceActionListener listener;

    public TodayClassAdapter(Context context, OnAttendanceActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setClasses(List<ClassWithTodayStatus> items) {
        this.classList.clear();
        if (items != null) {
            this.classList.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TodayClassViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_today_class, parent, false);
        return new TodayClassViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TodayClassViewHolder holder, int position) {
        ClassWithTodayStatus item = classList.get(position);
        ClassSchedule schedule = item.getSchedule();
        String currentStatus = item.getCurrentStatus();

        holder.tvSubjectName.setText(schedule.getSubjectName());

        // Class Type Badge
        String classType = schedule.getClassType();
        holder.tvClassType.setText(classType.toUpperCase());
        if ("Lab".equalsIgnoreCase(classType)) {
            holder.tvClassType.setBackgroundResource(R.drawable.bg_type_lab);
            holder.tvClassType.setTextColor(ContextCompat.getColor(context, R.color.type_lab_text));
        } else {
            holder.tvClassType.setBackgroundResource(R.drawable.bg_type_theory);
            holder.tvClassType.setTextColor(ContextCompat.getColor(context, R.color.black));
        }

        // Timing
        String timeStr = schedule.getStartTime();
        if (schedule.getEndTime() != null && !schedule.getEndTime().trim().isEmpty()) {
            timeStr += " - " + schedule.getEndTime();
        }
        holder.tvClassTime.setText(timeStr);

        // Room
        String room = schedule.getRoomNumber();
        if (room == null || room.trim().isEmpty()) {
            room = "TBA";
        }
        holder.tvRoomNumber.setText(room);

        // Credits
        holder.tvCreditHours.setText("(" + schedule.getCreditHours() + " Cr)");

        // Status Button styling & Recorded Banner
        resetChipStyles(holder);

        if (currentStatus != null) {
            holder.layoutStatusBadge.setVisibility(View.VISIBLE);
            holder.tvStatusText.setText("Marked: " + currentStatus);

            switch (currentStatus) {
                case AttendanceHistory.STATUS_PRESENT:
                    holder.btnPresent.setBackgroundResource(R.drawable.bg_status_present);
                    holder.btnPresent.setTextColor(ContextCompat.getColor(context, R.color.black));
                    holder.layoutStatusBadge.setBackgroundResource(R.drawable.bg_badge);
                    holder.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.zen_accent));
                    holder.ivStatusIcon.setColorFilter(ContextCompat.getColor(context, R.color.zen_accent));
                    holder.cardView.setStrokeWidth(0);
                    break;

                case AttendanceHistory.STATUS_ABSENT:
                    holder.btnAbsent.setBackgroundResource(R.drawable.bg_status_absent);
                    holder.btnAbsent.setTextColor(ContextCompat.getColor(context, R.color.status_absent));
                    holder.layoutStatusBadge.setBackgroundResource(R.drawable.bg_status_absent);
                    holder.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.status_absent));
                    holder.ivStatusIcon.setColorFilter(ContextCompat.getColor(context, R.color.status_absent));
                    holder.cardView.setStrokeWidth(0);
                    break;

                case AttendanceHistory.STATUS_HOLIDAY:
                    holder.btnHoliday.setBackgroundResource(R.drawable.bg_status_holiday);
                    holder.btnHoliday.setTextColor(ContextCompat.getColor(context, R.color.status_holiday));
                    holder.layoutStatusBadge.setBackgroundResource(R.drawable.bg_status_holiday);
                    holder.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.status_holiday));
                    holder.ivStatusIcon.setColorFilter(ContextCompat.getColor(context, R.color.status_holiday));
                    holder.cardView.setStrokeWidth(0);
                    break;
            }
        } else {
            holder.layoutStatusBadge.setVisibility(View.GONE);
            holder.cardView.setStrokeWidth(0);
        }

        // Action Click Listeners: Grab subject_name, credit_hours, and class_type from selected ClassSchedule
        holder.btnPresent.setOnClickListener(v -> {
            if (listener != null) {
                String subjectName = schedule.getSubjectName();
                int creditHours = schedule.getCreditHours();
                String type = schedule.getClassType();
                listener.onMarkAttendance(subjectName, creditHours, type, AttendanceHistory.STATUS_PRESENT);
            }
        });

        holder.btnAbsent.setOnClickListener(v -> {
            if (listener != null) {
                String subjectName = schedule.getSubjectName();
                int creditHours = schedule.getCreditHours();
                String type = schedule.getClassType();
                listener.onMarkAttendance(subjectName, creditHours, type, AttendanceHistory.STATUS_ABSENT);
            }
        });

        holder.btnHoliday.setOnClickListener(v -> {
            if (listener != null) {
                String subjectName = schedule.getSubjectName();
                int creditHours = schedule.getCreditHours();
                String type = schedule.getClassType();
                listener.onMarkAttendance(subjectName, creditHours, type, AttendanceHistory.STATUS_HOLIDAY);
            }
        });
    }

    private void resetChipStyles(TodayClassViewHolder holder) {
        int unselectedTextColor = Color.parseColor("#A0A0A0");
        holder.btnPresent.setBackgroundResource(R.drawable.bg_chip_status_unselected);
        holder.btnPresent.setTextColor(unselectedTextColor);

        holder.btnAbsent.setBackgroundResource(R.drawable.bg_chip_status_unselected);
        holder.btnAbsent.setTextColor(unselectedTextColor);

        holder.btnHoliday.setBackgroundResource(R.drawable.bg_chip_status_unselected);
        holder.btnHoliday.setTextColor(unselectedTextColor);
    }

    @Override
    public int getItemCount() {
        return classList.size();
    }

    static class TodayClassViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        TextView tvSubjectName;
        TextView tvClassType;
        TextView tvClassTime;
        TextView tvRoomNumber;
        TextView tvCreditHours;
        LinearLayout layoutStatusBadge;
        ImageView ivStatusIcon;
        TextView tvStatusText;
        TextView btnPresent;
        TextView btnAbsent;
        TextView btnHoliday;

        public TodayClassViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_today_class);
            tvSubjectName = itemView.findViewById(R.id.tv_subject_name);
            tvClassType = itemView.findViewById(R.id.tv_class_type);
            tvClassTime = itemView.findViewById(R.id.tv_class_time);
            tvRoomNumber = itemView.findViewById(R.id.tv_room_number);
            tvCreditHours = itemView.findViewById(R.id.tv_credit_hours);
            layoutStatusBadge = itemView.findViewById(R.id.layout_status_badge);
            ivStatusIcon = itemView.findViewById(R.id.iv_status_icon);
            tvStatusText = itemView.findViewById(R.id.tv_status_text);
            btnPresent = itemView.findViewById(R.id.btn_status_present);
            btnAbsent = itemView.findViewById(R.id.btn_status_absent);
            btnHoliday = itemView.findViewById(R.id.btn_status_holiday);
        }
    }
}

package com.example.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.AttendanceRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView Adapter for Subject Attendance History log.
 */
public class AttendanceHistoryAdapter extends RecyclerView.Adapter<AttendanceHistoryAdapter.HistoryViewHolder> {

    public interface OnRecordLongClickListener {
        void onRecordLongClick(AttendanceRecord record, int position);
    }

    public interface OnRecordClickListener {
        void onRecordClick(AttendanceRecord record, int position);
    }

    public interface OnStatusTagClickListener {
        void onStatusTagClick(AttendanceRecord record, int position);
    }

    private final Context context;
    private final List<AttendanceRecord> historyList = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault());
    private OnRecordLongClickListener longClickListener;
    private OnRecordClickListener clickListener;
    private OnStatusTagClickListener statusTagClickListener;

    public AttendanceHistoryAdapter(Context context) {
        this.context = context;
    }

    public void setOnRecordLongClickListener(OnRecordLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setOnRecordClickListener(OnRecordClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnStatusTagClickListener(OnStatusTagClickListener listener) {
        this.statusTagClickListener = listener;
    }

    public void setHistory(List<AttendanceRecord> items) {
        this.historyList.clear();
        if (items != null) {
            this.historyList.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_attendance_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        AttendanceRecord record = historyList.get(position);

        String formattedDate = dateFormat.format(new Date(record.getDate()));
        holder.tvDate.setText(formattedDate);
        holder.tvSubtitle.setText("Subject Session Log");

        String status = record.getStatus();
        holder.tvStatusBadge.setText(status != null ? status.toUpperCase() : "PRESENT");

        if (AttendanceRecord.STATUS_PRESENT.equalsIgnoreCase(status)) {
            holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_present);
            holder.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_present));
        } else if (AttendanceRecord.STATUS_ABSENT.equalsIgnoreCase(status)) {
            holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_absent);
            holder.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_absent));
        } else {
            holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_holiday);
            holder.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_holiday));
        }

        // Tap exclusively on the status tag (Present, Absent, Holiday) to edit status
        holder.tvStatusBadge.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos < historyList.size() && statusTagClickListener != null) {
                statusTagClickListener.onStatusTagClick(historyList.get(pos), pos);
            }
        });

        // Ensure row itself has no click or long-click handlers
        holder.itemView.setOnClickListener(null);
        holder.itemView.setOnLongClickListener(null);
        holder.itemView.setClickable(false);
        holder.itemView.setLongClickable(false);
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate;
        TextView tvSubtitle;
        TextView tvStatusBadge;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_history_date);
            tvSubtitle = itemView.findViewById(R.id.tv_history_day_name);
            tvStatusBadge = itemView.findViewById(R.id.tv_history_status_badge);
        }
    }
}

package com.example.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.ActivityLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter displaying ActivityLog records on a single, clean line per row.
 */
public class ActivityLogAdapter extends RecyclerView.Adapter<ActivityLogAdapter.LogViewHolder> {

    private final Context context;
    private final List<ActivityLog> logList = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault());

    public ActivityLogAdapter(Context context) {
        this.context = context;
    }

    public void setLogs(List<ActivityLog> logs) {
        this.logList.clear();
        if (logs != null) {
            this.logList.addAll(logs);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_activity_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        ActivityLog log = logList.get(position);

        String formattedTime = timeFormat.format(new Date(log.getTimestamp()));
        holder.tvTimestamp.setText(formattedTime);

        String message = log.getMessage() != null ? log.getMessage() : "";
        holder.tvMessage.setText(message);
    }

    @Override
    public int getItemCount() {
        return logList.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTimestamp;
        final TextView tvMessage;

        LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTimestamp = itemView.findViewById(R.id.tv_log_timestamp);
            tvMessage = itemView.findViewById(R.id.tv_log_message);
        }
    }
}

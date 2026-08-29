package com.example.adapter;

import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.Task;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * TaskAdapter is the RecyclerView adapter for the To-Do task checklist.
 *
 * Implements interactive Checkbox behavior with real-time visual strike-through,
 * time window indicators (Start -> End time / duration), and overdue status.
 */
public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private List<Task> tasks = new ArrayList<>();
    private final OnTaskActionListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());

    public interface OnTaskActionListener {
        void onTaskToggle(Task task, boolean isCompleted);
        void onTaskDelete(Task task);
    }

    public TaskAdapter(OnTaskActionListener listener) {
        this.listener = listener;
    }

    public void setTasks(final List<Task> newTasks) {
        if (newTasks == null) {
            this.tasks.clear();
            notifyDataSetChanged();
            return;
        }

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return tasks.size();
            }

            @Override
            public int getNewListSize() {
                return newTasks.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return tasks.get(oldItemPosition).getId() == newTasks.get(newItemPosition).getId();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Task oldTask = tasks.get(oldItemPosition);
                Task newTask = newTasks.get(newItemPosition);
                return TextUtils.equals(oldTask.getDescription(), newTask.getDescription()) &&
                        oldTask.isCompleted() == newTask.isCompleted() &&
                        oldTask.getStartTime() == newTask.getStartTime() &&
                        oldTask.getEndTime() == newTask.getEndTime() &&
                        oldTask.getDurationMinutes() == newTask.getDurationMinutes();
            }
        });

        this.tasks = new ArrayList<>(newTasks);
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task currentTask = tasks.get(position);
        holder.bind(currentTask, listener, timeFormat, dateFormat);
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    public static class TaskViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCheckBox cbStatus;
        private final TextView tvDescription;
        private final ImageButton btnDelete;
        private final LinearLayout layoutTaskTime;
        private final TextView tvTaskTime;
        private final ImageView ivTimeIcon;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            cbStatus = itemView.findViewById(R.id.cb_task_status);
            tvDescription = itemView.findViewById(R.id.tv_task_description);
            btnDelete = itemView.findViewById(R.id.btn_delete_task);
            layoutTaskTime = itemView.findViewById(R.id.layout_task_time);
            tvTaskTime = itemView.findViewById(R.id.tv_task_time);
            ivTimeIcon = itemView.findViewById(R.id.iv_time_icon);
        }

        public void bind(final Task task, final OnTaskActionListener listener,
                         SimpleDateFormat timeFormat, SimpleDateFormat dateFormat) {
            tvDescription.setText(task.getDescription());

            cbStatus.setOnCheckedChangeListener(null);
            cbStatus.setChecked(task.isCompleted());

            applyCompletionStyle(task.isCompleted());

            // Timing Window Formatting
            if (task.hasTimeWindow()) {
                layoutTaskTime.setVisibility(View.VISIBLE);
                long now = System.currentTimeMillis();
                long endTime = task.getEndTime();
                long startTime = task.getStartTime();

                StringBuilder timeBuilder = new StringBuilder();
                if (startTime > 0) {
                    timeBuilder.append(timeFormat.format(new Date(startTime))).append(" → ");
                }
                timeBuilder.append(timeFormat.format(new Date(endTime)));

                if (task.getDurationMinutes() > 0) {
                    int durationMins = task.getDurationMinutes();
                    if (durationMins >= 60) {
                        int hours = durationMins / 60;
                        int mins = durationMins % 60;
                        timeBuilder.append(" (").append(hours).append("h");
                        if (mins > 0) timeBuilder.append(" ").append(mins).append("m");
                        timeBuilder.append(")");
                    } else {
                        timeBuilder.append(" (").append(durationMins).append("m)");
                    }
                }

                if (!task.isCompleted() && now > endTime) {
                    // Overdue
                    tvTaskTime.setText("⚠️ Overdue (" + timeBuilder.toString() + ")");
                    tvTaskTime.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.zen_danger));
                    ivTimeIcon.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.zen_danger));
                } else {
                    tvTaskTime.setText(timeBuilder.toString());
                    tvTaskTime.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.zen_text_secondary));
                    ivTimeIcon.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.zen_accent));
                }
            } else {
                layoutTaskTime.setVisibility(View.GONE);
            }

            cbStatus.setOnCheckedChangeListener((buttonView, isChecked) -> {
                applyCompletionStyle(isChecked);
                if (listener != null) {
                    listener.onTaskToggle(task, isChecked);
                }
            });

            itemView.setOnClickListener(v -> cbStatus.toggle());

            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTaskDelete(task);
                }
            });
        }

        private void applyCompletionStyle(boolean isCompleted) {
            if (isCompleted) {
                tvDescription.setPaintFlags(tvDescription.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                tvDescription.setAlpha(0.45f);
                layoutTaskTime.setAlpha(0.45f);
            } else {
                tvDescription.setPaintFlags(tvDescription.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                tvDescription.setAlpha(1.0f);
                layoutTaskTime.setAlpha(1.0f);
            }
        }
    }
}

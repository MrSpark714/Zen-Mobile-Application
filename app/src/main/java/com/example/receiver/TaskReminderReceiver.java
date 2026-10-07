package com.example.receiver;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.MainActivity;
import com.example.R;
import com.example.database.AppDatabase;
import com.example.model.Task;
import com.example.util.NotificationHelper;

/**
 * BroadcastReceiver triggered when a task's deadline time is reached.
 * Pulls a high-priority system notification to the user's phone status bar.
 */
public class TaskReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "TaskReminderReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        int taskId = intent.getIntExtra(NotificationHelper.EXTRA_TASK_ID, -1);
        String taskDesc = intent.getStringExtra(NotificationHelper.EXTRA_TASK_DESC);

        if (taskDesc == null || taskDesc.trim().isEmpty()) {
            taskDesc = "Scheduled Task";
        }

        Log.d(TAG, "Task reminder triggered for task #" + taskId + ": " + taskDesc);

        // Check if Android 13+ Notification Permission is granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Cannot post notification.");
                return;
            }
        }

        NotificationHelper.createNotificationChannel(context);

        // Intent to launch MainActivity and open the Tasks tab directly
        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openAppIntent.putExtra("NAV_TAB", "tasks");

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                taskId != -1 ? taskId : 1001,
                openAppIntent,
                flags
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tasks)
                .setContentTitle("⏱️ Task Due: " + taskDesc)
                .setContentText("Target completion time reached in ZEN. Tap to review.")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("The completion time for your task \"" + taskDesc + "\" has been reached. Open ZEN to mark it done or review."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 250, 150, 250})
                .setContentIntent(openPendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify(taskId != -1 ? taskId : (int) System.currentTimeMillis(), builder.build());
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while displaying notification", e);
        }
    }
}

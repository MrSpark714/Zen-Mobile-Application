package com.example.receiver;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.MainActivity;
import com.example.R;
import com.example.util.NotificationHelper;

/**
 * BroadcastReceiver triggered by AlarmManager 5 minutes prior to a scheduled class start time.
 *
 * Notification Title: "[Subject Name] in 5 mins"
 * Notification Body: "Room: [Room Number] | Time: [Time]"
 */
public class ClassAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "ClassAlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        if (NotificationHelper.ACTION_CLASS_ALARM.equals(action)) {
            int scheduleId = intent.getIntExtra(NotificationHelper.EXTRA_CLASS_ID, 0);
            String subjectName = intent.getStringExtra(NotificationHelper.EXTRA_CLASS_SUBJECT);
            String roomNumber = intent.getStringExtra(NotificationHelper.EXTRA_CLASS_ROOM);
            String startTime = intent.getStringExtra(NotificationHelper.EXTRA_CLASS_TIME);

            if (subjectName == null) {
                subjectName = "Class";
            }
            if (roomNumber == null || roomNumber.trim().isEmpty()) {
                roomNumber = "TBA";
            }
            if (startTime == null) {
                startTime = "";
            }

            Log.d(TAG, "Triggering 5-minute class alarm for: " + subjectName + " at " + startTime);

            // Ensure channel exists
            NotificationHelper.createNotificationChannel(context);

            // Intent to launch MainActivity when tapped
            Intent openIntent = new Intent(context, MainActivity.class);
            openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            openIntent.putExtra("NAV_TAB", "attendance");

            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent contentPendingIntent = PendingIntent.getActivity(
                    context,
                    scheduleId + 10000,
                    openIntent,
                    pendingFlags
            );

            // Title: "[Subject Name] in 5 mins"
            String title = subjectName + " in 5 mins";
            // Body: "Room: [Room Number] | Time: [Time]"
            String body = "Room: " + roomNumber + " | Time: " + startTime;

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationHelper.CHANNEL_CLASS_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(contentPendingIntent);

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(scheduleId + 20000, builder.build());
            }
        }
    }
}

package com.example.util;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.model.ClassSchedule;
import com.example.model.Task;
import com.example.receiver.ClassAlarmReceiver;
import com.example.receiver.TaskReminderReceiver;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class for managing task due notifications and AlarmManager schedules for both Tasks and Classes.
 */
public class NotificationHelper {

    public static final String CHANNEL_ID = "zen_task_reminders";
    public static final String CHANNEL_NAME = "Task Reminders";
    public static final String CHANNEL_DESC = "Notifications when scheduled task deadlines are reached";

    public static final String CHANNEL_CLASS_ID = "zen_class_alerts";
    public static final String CHANNEL_CLASS_NAME = "Class Alerts";
    public static final String CHANNEL_CLASS_DESC = "Notifications 5 minutes before scheduled classes start";

    public static final String EXTRA_TASK_ID = "extra_task_id";
    public static final String EXTRA_TASK_DESC = "extra_task_desc";
    public static final String ACTION_TASK_REMINDER = "com.example.ACTION_TASK_REMINDER";

    public static final String EXTRA_CLASS_ID = "extra_class_id";
    public static final String EXTRA_CLASS_SUBJECT = "extra_class_subject";
    public static final String EXTRA_CLASS_ROOM = "extra_class_room";
    public static final String EXTRA_CLASS_TIME = "extra_class_time";
    public static final String ACTION_CLASS_ALARM = "com.example.ACTION_CLASS_ALARM";

    private static final String TAG = "NotificationHelper";

    /**
     * Creates notification channels on Android 8.0 (API 26) and above.
     */
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;

            // Task Reminders Channel
            NotificationChannel taskChannel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            taskChannel.setDescription(CHANNEL_DESC);
            taskChannel.enableVibration(true);
            taskChannel.enableLights(true);
            manager.createNotificationChannel(taskChannel);

            // Class Alerts Channel (5 mins prior)
            NotificationChannel classChannel = new NotificationChannel(
                    CHANNEL_CLASS_ID,
                    CHANNEL_CLASS_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            classChannel.setDescription(CHANNEL_CLASS_DESC);
            classChannel.enableVibration(true);
            classChannel.enableLights(true);
            manager.createNotificationChannel(classChannel);
        }
    }

    /**
     * Schedule an exact alarm notification when a task is due.
     */
    public static void scheduleTaskReminder(Context context, Task task) {
        if (task == null || task.getEndTime() <= System.currentTimeMillis()) {
            return;
        }

        createNotificationChannel(context);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, TaskReminderReceiver.class);
        intent.setAction(ACTION_TASK_REMINDER);
        intent.putExtra(EXTRA_TASK_ID, task.getId());
        intent.putExtra(EXTRA_TASK_DESC, task.getDescription());

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                task.getId(),
                intent,
                flags
        );

        long triggerAtMillis = task.getEndTime();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
            Log.d(TAG, "Scheduled reminder for task #" + task.getId() + " at " + triggerAtMillis);
        } catch (SecurityException e) {
            Log.w(TAG, "Exact alarm permission fallback", e);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }
    }

    /**
     * Cancel an active task alarm reminder.
     */
    public static void cancelTaskReminder(Context context, int taskId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, TaskReminderReceiver.class);
        intent.setAction(ACTION_TASK_REMINDER);

        int flags = PendingIntent.FLAG_NO_CREATE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId,
                intent,
                flags
        );

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            Log.d(TAG, "Cancelled reminder for task #" + taskId);
        }
    }

    /**
     * Schedule a local notification exact alarm triggered 5 minutes before the class startTime.
     *
     * Notification Title: "[Subject Name] in 5 mins"
     * Notification Body: "Room: [Room Number] | Time: [Time]"
     */
    public static void scheduleClassReminder(Context context, ClassSchedule schedule) {
        if (schedule == null || schedule.getStartTime() == null || schedule.getDayOfWeek() == null) {
            return;
        }

        createNotificationChannel(context);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        long triggerAtMillis = calculateNextClassAlarmMillis(schedule.getDayOfWeek(), schedule.getStartTime());
        if (triggerAtMillis <= System.currentTimeMillis()) {
            return;
        }

        Intent intent = new Intent(context, ClassAlarmReceiver.class);
        intent.setAction(ACTION_CLASS_ALARM);
        intent.putExtra(EXTRA_CLASS_ID, schedule.getId());
        intent.putExtra(EXTRA_CLASS_SUBJECT, schedule.getSubjectName());
        intent.putExtra(EXTRA_CLASS_ROOM, schedule.getRoomNumber());
        intent.putExtra(EXTRA_CLASS_TIME, schedule.getStartTime());

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                schedule.getId() + 50000,
                intent,
                flags
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
            Log.d(TAG, "Scheduled 5-min alarm for class " + schedule.getSubjectName() + " at " + triggerAtMillis);
        } catch (SecurityException e) {
            Log.w(TAG, "Exact alarm permission fallback for class", e);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }
    }

    /**
     * Cancel scheduled 5-minute class alarm.
     */
    public static void cancelClassReminder(Context context, int scheduleId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, ClassAlarmReceiver.class);
        intent.setAction(ACTION_CLASS_ALARM);

        int flags = PendingIntent.FLAG_NO_CREATE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                scheduleId + 50000,
                intent,
                flags
        );

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            Log.d(TAG, "Cancelled 5-min alarm for class #" + scheduleId);
        }
    }

    /**
     * Helper to compute epoch millis of next occurrence of a class day & time minus 5 minutes.
     */
    public static long calculateNextClassAlarmMillis(String dayOfWeek, String timeStr) {
        int targetDayOfWeek = getCalendarDayOfWeek(dayOfWeek);
        if (targetDayOfWeek == -1) return 0;

        int[] hourMin = parseHourAndMinute(timeStr);
        int hour = hourMin[0];
        int minute = hourMin[1];

        Calendar cal = Calendar.getInstance();
        int currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

        int daysToAdd = (targetDayOfWeek - currentDayOfWeek + 7) % 7;

        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // Subtract 5 minutes for the 5-min advance alarm
        cal.add(Calendar.MINUTE, -5);

        if (daysToAdd == 0 && cal.getTimeInMillis() <= System.currentTimeMillis()) {
            daysToAdd = 7;
        }

        cal.add(Calendar.DAY_OF_YEAR, daysToAdd);
        return cal.getTimeInMillis();
    }

    public static int getCalendarDayOfWeek(String dayOfWeek) {
        if (dayOfWeek == null) return -1;
        switch (dayOfWeek.trim().toLowerCase(Locale.ROOT)) {
            case "monday":
            case "mon":
                return Calendar.MONDAY;
            case "tuesday":
            case "tue":
                return Calendar.TUESDAY;
            case "wednesday":
            case "wed":
                return Calendar.WEDNESDAY;
            case "thursday":
            case "thu":
                return Calendar.THURSDAY;
            case "friday":
            case "fri":
                return Calendar.FRIDAY;
            default:
                return -1;
        }
    }

    public static int[] parseHourAndMinute(String timeStr) {
        int hour = 9;
        int minute = 0;
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return new int[]{hour, minute};
        }

        try {
            SimpleDateFormat sdf12 = new SimpleDateFormat("h:mm a", Locale.getDefault());
            Date d = sdf12.parse(timeStr.trim());
            if (d != null) {
                Calendar c = Calendar.getInstance();
                c.setTime(d);
                return new int[]{c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)};
            }
        } catch (ParseException ignored) {}

        try {
            SimpleDateFormat sdf24 = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date d = sdf24.parse(timeStr.trim());
            if (d != null) {
                Calendar c = Calendar.getInstance();
                c.setTime(d);
                return new int[]{c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)};
            }
        } catch (ParseException ignored) {}

        return new int[]{hour, minute};
    }
}

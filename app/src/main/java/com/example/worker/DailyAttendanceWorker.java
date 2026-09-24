package com.example.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.database.AppDatabase;
import com.example.model.ActivityLog;
import com.example.model.AttendanceHistory;
import com.example.model.ClassSchedule;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * DailyAttendanceWorker: Automated background task executed nightly via WorkManager.
 *
 * Cross-references today's scheduled classes against existing AttendanceHistory records.
 * For any scheduled class that the user did not mark by the end of the day:
 * 1. Inserts a snapshot AttendanceHistory entry with status = "Holiday".
 * 2. Inserts an audit row into ActivityLog: "System auto-marked [subject_name] as Holiday due to no user input."
 */
public class DailyAttendanceWorker extends Worker {

    private static final String TAG = "DailyAttendanceWorker";
    public static final String WORK_NAME = "DailyAttendanceAutoMarkerWork";

    public DailyAttendanceWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting daily attendance auto-marker worker execution...");
        Context context = getApplicationContext();
        AppDatabase database = AppDatabase.getInstance(context);

        try {
            // 1. Determine current day of week and calculate today's epoch timestamp normalized to midnight
            Calendar calendar = Calendar.getInstance();
            int dayOfWeekInt = calendar.get(Calendar.DAY_OF_WEEK);

            // Weekends (Saturday / Sunday) have no scheduled classes in the timetable
            if (dayOfWeekInt == Calendar.SATURDAY || dayOfWeekInt == Calendar.SUNDAY) {
                Log.d(TAG, "Weekend detected - skipping auto-marker.");
                return Result.success();
            }

            String currentDayOfWeek = getDayOfWeekString(dayOfWeekInt);
            long todayMidnightEpoch = getStartOfDayMillis(calendar);

            // 2. Query database for scheduled classes for today (synchronous execution on worker background thread)
            List<ClassSchedule> scheduledClasses = database.classScheduleDao().getClassesForDaySync(currentDayOfWeek);
            if (scheduledClasses == null || scheduledClasses.isEmpty()) {
                Log.d(TAG, "No classes scheduled for today (" + currentDayOfWeek + ").");
                return Result.success();
            }

            // 3. Query database for attendance records already marked for today
            List<AttendanceHistory> existingHistory = database.attendanceHistoryDao().getRecordsForDateSync(todayMidnightEpoch);
            Set<String> markedSubjectKeys = new HashSet<>();
            if (existingHistory != null) {
                for (AttendanceHistory history : existingHistory) {
                    if (history.getSubjectName() != null) {
                        markedSubjectKeys.add(history.getSubjectName().trim().toLowerCase(Locale.ROOT));
                    }
                }
            }

            // 4. Cross-reference scheduled classes with marked records
            long currentTimestamp = System.currentTimeMillis();
            for (ClassSchedule schedule : scheduledClasses) {
                if (schedule == null || schedule.getSubjectName() == null) continue;

                String subjectKey = schedule.getSubjectName().trim().toLowerCase(Locale.ROOT);
                if (!markedSubjectKeys.contains(subjectKey)) {
                    // Action A: Insert snapshot AttendanceHistory
                    int creditHours = schedule.getCreditHours() > 0 ? schedule.getCreditHours() : 1;
                    String classType = schedule.getClassType() != null ? schedule.getClassType() : "Theory";

                    AttendanceHistory autoHolidayRecord = new AttendanceHistory(
                            schedule.getSubjectName(),
                            creditHours,
                            classType,
                            todayMidnightEpoch,
                            AttendanceHistory.STATUS_HOLIDAY
                    );
                    database.attendanceHistoryDao().insert(autoHolidayRecord);

                    // Mark as processed in local set to avoid duplicate entries for multiple periods
                    markedSubjectKeys.add(subjectKey);

                    // Action B: Audit Log entry
                    String logMessage = "System auto-marked " + schedule.getSubjectName() + " as Holiday due to no user input.";
                    ActivityLog auditLog = new ActivityLog(currentTimestamp, logMessage);
                    database.activityLogDao().insert(auditLog);

                    Log.i(TAG, "Auto-marked: " + schedule.getSubjectName() + " as Holiday.");
                }
            }

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Error executing daily attendance auto-marker", e);
            return Result.retry();
        }
    }

    /**
     * Calculates the epoch timestamp for 00:00:00.000 (midnight) of the given calendar date.
     */
    public static long getStartOfDayMillis(Calendar cal) {
        Calendar c = (Calendar) cal.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static String getDayOfWeekString(int dayOfWeek) {
        switch (dayOfWeek) {
            case Calendar.MONDAY:
                return "Monday";
            case Calendar.TUESDAY:
                return "Tuesday";
            case Calendar.WEDNESDAY:
                return "Wednesday";
            case Calendar.THURSDAY:
                return "Thursday";
            case Calendar.FRIDAY:
                return "Friday";
            case Calendar.SATURDAY:
                return "Saturday";
            case Calendar.SUNDAY:
                return "Sunday";
            default:
                return "Monday";
        }
    }

    /**
     * Helper to schedule the DailyAttendanceWorker via WorkManager targeting ~11:55 PM nightly.
     */
    public static void scheduleDailyAutoMarker(@NonNull Context context) {
        Calendar now = Calendar.getInstance();

        // Target 11:55 PM today
        Calendar targetTime = Calendar.getInstance();
        targetTime.set(Calendar.HOUR_OF_DAY, 23);
        targetTime.set(Calendar.MINUTE, 55);
        targetTime.set(Calendar.SECOND, 0);
        targetTime.set(Calendar.MILLISECOND, 0);

        // If current time is already past 11:55 PM, schedule for tomorrow 11:55 PM
        if (now.after(targetTime)) {
            targetTime.add(Calendar.DAY_OF_YEAR, 1);
        }

        long initialDelayMillis = targetTime.getTimeInMillis() - now.getTimeInMillis();

        Constraints constraints = new Constraints.Builder()
                .build();

        PeriodicWorkRequest periodicWorkRequest =
                new PeriodicWorkRequest.Builder(DailyAttendanceWorker.class, 24, TimeUnit.HOURS)
                        .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                        .setConstraints(constraints)
                        .build();

        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest
        );

        Log.d(TAG, "Enqueued periodic WorkManager auto-marker with initial delay of " +
                (initialDelayMillis / 1000 / 60) + " minutes.");
    }
}

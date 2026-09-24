package com.example.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.model.ActivityLog;
import com.example.model.AttendanceHistory;
import com.example.model.ClassSchedule;
import com.example.model.Note;
import com.example.model.Task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AppDatabase is the central Room database holder for the ZEN application.
 *
 * It uses the Singleton design pattern so only one instance of the SQLite database
 * is opened across the entire application process, saving system resources and
 * preventing concurrency locking issues.
 */
@Database(entities = {Note.class, Task.class, ClassSchedule.class, AttendanceHistory.class, ActivityLog.class}, version = 5, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    // Database name on device storage
    private static final String DATABASE_NAME = "zen_database.db";

    // Singleton database instance
    private static volatile AppDatabase INSTANCE;

    // Fixed thread pool executor to perform asynchronous database writes (insert, update, delete)
    // off the Android Main (UI) Thread
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    /**
     * Abstract method to access the NoteDao. Room implements this automatically.
     */
    public abstract NoteDao noteDao();

    /**
     * Abstract method to access the TaskDao. Room implements this automatically.
     */
    public abstract TaskDao taskDao();

    /**
     * Abstract method to access the ClassScheduleDao.
     */
    public abstract ClassScheduleDao classScheduleDao();

    /**
     * Abstract method to access the AttendanceHistoryDao.
     */
    public abstract AttendanceHistoryDao attendanceHistoryDao();

    /**
     * Abstract method to access the ActivityLogDao.
     */
    public abstract ActivityLogDao activityLogDao();

    /**
     * Thread-safe Singleton getter to retrieve the AppDatabase instance.
     *
     * @param context Application context used to build or open the database.
     * @return The singleton AppDatabase instance.
     */
    public static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    )
                    // Fallback to destructive migration during schema changes in development
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}


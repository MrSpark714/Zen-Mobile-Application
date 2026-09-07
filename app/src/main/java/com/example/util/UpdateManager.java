package com.example.util;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.example.BuildConfig;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UpdateManager handles in-app update checks, background JSON polling via HttpURLConnection,
 * version comparison, update dialog presentation, and APK download orchestration.
 */
public class UpdateManager {

    private static final String TAG = "UpdateManager";

    // SharedPreferences configuration
    private static final String PREFS_NAME = "zen_update_preferences";
    public static final String KEY_AUTO_UPDATE_ENABLED = "enable_auto_update_checks";
    private static final String KEY_PENDING_DOWNLOAD_ID = "pending_download_id";
    private static final String KEY_PENDING_DOWNLOAD_FILE = "pending_download_file";

    // Default GitHub raw JSON endpoint for ZEN app updates
    public static final String DEFAULT_UPDATE_URL =
            "https://raw.githubusercontent.com/MrSpark714/Zen-Mobile-Application/main/update.json";

    private static final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Checks if auto-update checks are enabled by the user (default: true).
     */
    public static boolean isAutoUpdateEnabled(Context context) {
        if (context == null) return true;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_AUTO_UPDATE_ENABLED, true);
    }

    /**
     * Sets whether auto-update checks are enabled.
     */
    public static void setAutoUpdateEnabled(Context context, boolean enabled) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_AUTO_UPDATE_ENABLED, enabled).apply();
    }

    /**
     * Retrieves recorded pending download ID.
     */
    public static long getPendingDownloadId(Context context) {
        if (context == null) return -1;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_PENDING_DOWNLOAD_ID, -1);
    }

    /**
     * Retrieves recorded pending download file name.
     */
    public static String getPendingDownloadFileName(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PENDING_DOWNLOAD_FILE, null);
    }

    /**
     * Saves pending download details for DownloadReceiver tracking.
     */
    public static void savePendingDownload(Context context, long downloadId, String fileName) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putLong(KEY_PENDING_DOWNLOAD_ID, downloadId)
                .putString(KEY_PENDING_DOWNLOAD_FILE, fileName)
                .apply();
    }

    /**
     * Clears pending download details.
     */
    public static void clearPendingDownload(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_PENDING_DOWNLOAD_ID)
                .remove(KEY_PENDING_DOWNLOAD_FILE)
                .apply();
    }

    /**
     * Initiates update check with default update JSON URL.
     */
    public static void checkForUpdates(Activity activity) {
        checkForUpdates(activity, DEFAULT_UPDATE_URL, false);
    }

    /**
     * Initiates update check with a specified JSON URL.
     *
     * @param activity Context activity for showing the update dialog.
     * @param updateJsonUrl Target URL hosting the version metadata JSON.
     * @param isManualCheck True if triggered manually by user tap (overrides auto-check check & shows toast if current).
     */
    public static void checkForUpdates(Activity activity, String updateJsonUrl, boolean isManualCheck) {
        if (activity == null) return;

        // CRUCIAL: Before executing network request, check if auto-update toggle is OFF
        if (!isManualCheck && !isAutoUpdateEnabled(activity)) {
            Log.d(TAG, "Auto-update checks disabled by user. Aborting update check.");
            return;
        }

        if (updateJsonUrl == null || updateJsonUrl.trim().isEmpty()) {
            updateJsonUrl = DEFAULT_UPDATE_URL;
        }

        final String finalUrl = updateJsonUrl;

        backgroundExecutor.execute(() -> {
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {
                URL url = new URL(finalUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                // connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "ZEN-Android-App/" + BuildConfig.VERSION_NAME);

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "Update check returned response code: " + responseCode);
                    if (isManualCheck) {
                        mainHandler.post(() -> {
                            if (!activity.isFinishing() && !activity.isDestroyed()) {
                                Toast.makeText(activity, "Unable to reach update server (Code " + responseCode + ")", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    return;
                }

                InputStream in = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder jsonBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBuilder.append(line);
                }

                String jsonString = jsonBuilder.toString();
                JSONObject json = new JSONObject(jsonString);

                int latestVersionCode = json.optInt("latest_version_code", -1);
                String latestVersionName = json.optString("latest_version_name", "");
                String releaseNotes = json.optString("release_notes", "Bug fixes and performance improvements.");
                String apkUrl = json.optString("apk_url", "");

                int currentVersionCode = BuildConfig.VERSION_CODE;
                Log.d(TAG, "Current Version Code: " + currentVersionCode + " | Remote Latest: " + latestVersionCode);

                mainHandler.post(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }

                    if (latestVersionCode > currentVersionCode && !apkUrl.isEmpty()) {
                        showUpdatePromptDialog(activity, latestVersionName, releaseNotes, apkUrl);
                    } else if (isManualCheck) {
                        Toast.makeText(activity, "ZEN is up to date (v" + BuildConfig.VERSION_NAME + ")", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error checking for updates", e);
                if (isManualCheck) {
                    mainHandler.post(() -> {
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            Toast.makeText(activity, "Check failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception ignored) {}
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * Shows Material Design 3 AlertDialog informing user about available update.
     */
    private static void showUpdatePromptDialog(Activity activity, String latestVersionName, String releaseNotes, String apkUrl) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Update Available")
                .setMessage("A new version of ZEN (v" + latestVersionName + ") is available!\n\nRelease Notes:\n" + releaseNotes)
                .setPositiveButton("Update Now", (dialog, which) -> {
                    startApkDownload(activity, apkUrl, latestVersionName);
                })
                .setNegativeButton("Later", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    /**
     * Enqueues APK download via Android's system DownloadManager.
     */
    public static void startApkDownload(Context context, String apkUrl, String latestVersionName) {
        try {
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) {
                Toast.makeText(context, "Download Manager is unavailable on this device", Toast.LENGTH_SHORT).show();
                return;
            }

            Uri downloadUri = Uri.parse(apkUrl);
            String fileName = "ZEN_v" + latestVersionName.replace(" ", "_") + ".apk";

            // Clean up previous leftover update file with same name if any
            try {
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs();
                }
                File existingApk = new File(downloadDir, fileName);
                if (existingApk.exists()) {
                    existingApk.delete();
                }
            } catch (Exception ignored) {}

            DownloadManager.Request request = new DownloadManager.Request(downloadUri);
            request.setTitle("ZEN v" + latestVersionName + " Update");
            request.setDescription("Downloading latest application update...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            request.setMimeType("application/vnd.android.package-archive");

            long downloadId = downloadManager.enqueue(request);
            savePendingDownload(context, downloadId, fileName);

            Toast.makeText(context, "Download started. You'll be prompted to install once complete.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start APK download", e);
            Toast.makeText(context, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}

package com.example.receiver;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.example.util.UpdateManager;

import java.io.File;

/**
 * BroadcastReceiver listening for DownloadManager.ACTION_DOWNLOAD_COMPLETE.
 * Secures downloaded APK with FileProvider and triggers Android's package installer.
 */
public class DownloadReceiver extends BroadcastReceiver {

    private static final String TAG = "DownloadReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
            return;
        }

        long completedDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        if (completedDownloadId == -1) {
            return;
        }

        long expectedDownloadId = UpdateManager.getPendingDownloadId(context);
        if (expectedDownloadId != -1 && completedDownloadId != expectedDownloadId) {
            // Completed download belongs to another task/app
            return;
        }

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            return;
        }

        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(completedDownloadId);

        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor != null && cursor.moveToFirst()) {
                int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                if (statusIndex != -1) {
                    int status = cursor.getInt(statusIndex);
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        File apkFile = resolveDownloadedApk(context, cursor);
                        if (apkFile != null && apkFile.exists()) {
                            UpdateManager.clearPendingDownload(context);
                            triggerApkInstallation(context, apkFile);
                        } else {
                            Log.e(TAG, "Downloaded APK file not found on disk");
                        }
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                        int reason = (reasonIndex != -1) ? cursor.getInt(reasonIndex) : -1;
                        Log.e(TAG, "Download failed with reason code: " + reason);
                        Toast.makeText(context, "Download failed (code " + reason + ")", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling download completion", e);
        }
    }

    /**
     * Resolves the downloaded APK file on device storage.
     */
    private File resolveDownloadedApk(Context context, Cursor cursor) {
        String savedFileName = UpdateManager.getPendingDownloadFileName(context);
        if (savedFileName != null && !savedFileName.isEmpty()) {
            File publicDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File candidate = new File(publicDownloadDir, savedFileName);
            if (candidate.exists()) {
                return candidate;
            }
        }

        int localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
        if (localUriIndex != -1) {
            String uriString = cursor.getString(localUriIndex);
            if (uriString != null) {
                Uri localUri = Uri.parse(uriString);
                if ("file".equals(localUri.getScheme()) && localUri.getPath() != null) {
                    File file = new File(localUri.getPath());
                    if (file.exists()) {
                        return file;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Triggers package installer with FileProvider content URI and read permission.
     */
    private void triggerApkInstallation(Context context, File apkFile) {
        try {
            String authority = context.getPackageName() + ".fileprovider";
            Uri contentUri = FileProvider.getUriForFile(context, authority, apkFile);

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(contentUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(installIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch package installer", e);
            Toast.makeText(context, "Unable to launch installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}

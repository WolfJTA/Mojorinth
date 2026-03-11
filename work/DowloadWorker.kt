package com.example.modrinthforandroid.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.app.DownloadManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.delay
import java.io.File

const val NOTIF_CHANNEL_ID   = "modrinth_downloads"
const val NOTIF_CHANNEL_NAME = "Mod Downloads"

const val KEY_DOWNLOAD_ID = "download_id"
const val KEY_FILENAME    = "filename"
const val KEY_TITLE       = "title"
const val KEY_NOTIF_ID    = "notif_id"
const val KEY_FINAL_DIR   = "final_dir"
const val KEY_TEMP_PATH   = "temp_path"

private const val TAG = "DownloadWorker"

/**
 * Polls DownloadManager until the download completes, then moves the file
 * from Downloads/ to the real instance folder.
 *
 * Shows a persistent progress notification while downloading, and a
 * tappable "Download complete" system notification when done.
 */
class DownloadWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val dm = ctx.getSystemService(DownloadManager::class.java)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureChannel()
        val notif = buildProgressNotif(
            title         = inputData.getString(KEY_TITLE) ?: "Downloading…",
            filename      = inputData.getString(KEY_FILENAME) ?: "",
            progress      = 0,
            max           = 0,
            indeterminate = true
        )
        return ForegroundInfo(inputData.getInt(KEY_NOTIF_ID, 0), notif)
    }

    override suspend fun doWork(): Result {
        ensureChannel()

        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        val filename   = inputData.getString(KEY_FILENAME)  ?: "file"
        val title      = inputData.getString(KEY_TITLE)     ?: "Downloading…"
        val notifId    = inputData.getInt(KEY_NOTIF_ID, downloadId.toInt())
        val finalDir   = inputData.getString(KEY_FINAL_DIR)
        val tempPath   = inputData.getString(KEY_TEMP_PATH)

        if (downloadId == -1L) return Result.failure()

        Log.d(TAG, "Starting poll for downloadId=$downloadId finalDir=$finalDir")

        // Poll DownloadManager until done
        while (true) {
            val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))

            if (cursor == null || !cursor.moveToFirst()) {
                cursor?.close()
                Log.w(TAG, "Cursor empty — download may have been cancelled")
                break
            }

            val status     = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total      = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val reason     = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            cursor.close()

            when (status) {
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PENDING -> {
                    val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    nm.notify(notifId, buildProgressNotif(title, filename, progress, 100,
                        indeterminate = total <= 0))
                    setProgress(workDataOf("progress" to progress))
                    delay(750)
                }

                DownloadManager.STATUS_SUCCESSFUL -> {
                    Log.d(TAG, "Download complete. Moving to finalDir=$finalDir")
                    nm.cancel(notifId) // dismiss progress notification
                    if (!finalDir.isNullOrBlank() && !tempPath.isNullOrBlank()) {
                        moveToFinalDestination(tempPath, finalDir, filename, title)
                    } else {
                        // No instance active — stays in Downloads/
                        showCompletionNotif(title, filename, "Saved to Downloads/")
                    }
                    return Result.success()
                }

                DownloadManager.STATUS_FAILED -> {
                    Log.e(TAG, "Download failed, reason code=$reason")
                    nm.cancel(notifId)
                    showErrorNotif(title, filename)
                    return Result.failure()
                }

                else -> delay(750)
            }
        }
        return Result.success()
    }

    // ── File move ─────────────────────────────────────────────────────────

    private fun moveToFinalDestination(
        tempPath: String,
        finalDir: String,
        filename: String,
        title: String
    ) {
        try {
            val src = File(tempPath)
            val dir = File(finalDir).also {
                val created = it.mkdirs()
                Log.d(TAG, "mkdirs($finalDir) = $created, exists=${it.exists()}")
            }
            val dest = File(dir, filename)

            Log.d(TAG, "Moving ${src.absolutePath} → ${dest.absolutePath}")
            Log.d(TAG, "src.exists=${src.exists()} src.length=${src.length()}")

            if (src.exists()) {
                src.copyTo(dest, overwrite = true)
                val deleted = src.delete()
                Log.d(TAG, "Copy done, src deleted=$deleted, dest.exists=${dest.exists()}")
                showCompletionNotif(title, filename, "Saved to ${dir.name}/")
            } else {
                // File already moved or missing — notify done anyway
                Log.w(TAG, "Source file not found at $tempPath")
                showCompletionNotif(title, filename, "Saved to Downloads/")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Move failed: ${e.message}", e)
            // File stays in Downloads — still notify the user
            showCompletionNotif(title, filename, "In Downloads/ (couldn't move: ${e.message})")
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────

    private fun buildProgressNotif(
        title: String,
        filename: String,
        progress: Int,
        max: Int,
        indeterminate: Boolean
    ) = NotificationCompat.Builder(ctx, NOTIF_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(title)
        .setContentText(filename)
        .setProgress(max, progress, indeterminate)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    /**
     * Tappable "Download complete" notification — opens the Downloads app.
     */
    private fun showCompletionNotif(title: String, filename: String, subtitle: String) {
        // Intent that opens the system Downloads app
        val openDownloads = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx, 0, openDownloads,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(ctx, NOTIF_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Downloaded: $title")
            .setContentText(subtitle)
            .setSubText(filename)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // heads-up on completion
            .setContentIntent(pendingIntent)
            .build()

        // Use a unique ID so multiple completions each show their own notification
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }

    private fun showErrorNotif(title: String, filename: String) {
        val notif = NotificationCompat.Builder(ctx, NOTIF_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText(title)
            .setSubText(filename)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID, NOTIF_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT // allows heads-up for completion
            ).apply { description = "Mod download progress and completion" }
            nm.createNotificationChannel(channel)
        }
    }
}
package com.example.modrinthforandroid.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.app.DownloadManager
import android.os.Build
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

/**
 * Polls DownloadManager progress and shows a notification. On completion,
 * moves the file from Downloads/ to the real instance folder using plain
 * File I/O (safe with MANAGE_EXTERNAL_STORAGE).
 *
 * Why the two-step? DownloadManager.setDestinationUri(Uri.fromFile(...)) throws
 * SecurityException for any path outside Downloads/ on modern Android, even
 * with MANAGE_EXTERNAL_STORAGE granted.
 */
class DownloadWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val dm = ctx.getSystemService(DownloadManager::class.java)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureChannel()
        val notif = buildNotif(
            title         = inputData.getString(KEY_TITLE) ?: "Downloading...",
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
        val title      = inputData.getString(KEY_TITLE)     ?: "Downloading..."
        val notifId    = inputData.getInt(KEY_NOTIF_ID, downloadId.toInt())
        val finalDir   = inputData.getString(KEY_FINAL_DIR)
        val tempPath   = inputData.getString(KEY_TEMP_PATH)

        if (downloadId == -1L) return Result.failure()

        while (true) {
            val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))

            if (cursor == null || !cursor.moveToFirst()) {
                cursor?.close()
                break
            }

            val status     = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total      = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            cursor.close()

            when (status) {
                DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                    val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    nm.notify(notifId, buildNotif(title, filename, progress, 100,
                        indeterminate = total <= 0))
                    setProgress(workDataOf("progress" to progress))
                    delay(750)
                }

                DownloadManager.STATUS_SUCCESSFUL -> {
                    if (!finalDir.isNullOrBlank() && !tempPath.isNullOrBlank()) {
                        moveToFinalDestination(tempPath, finalDir, filename, notifId, title)
                    } else {
                        // No instance active — stays in Downloads/
                        nm.notify(notifId, buildNotif(title, filename, 100, 100,
                            indeterminate = false, done = true, subtitle = "Saved to Downloads/"))
                    }
                    return Result.success()
                }

                DownloadManager.STATUS_FAILED -> {
                    nm.cancel(notifId)
                    return Result.failure()
                }

                else -> delay(750)
            }
        }
        return Result.success()
    }

    private fun moveToFinalDestination(
        tempPath: String,
        finalDir: String,
        filename: String,
        notifId: Int,
        title: String
    ) {
        try {
            val src  = File(tempPath)
            val dir  = File(finalDir).also { it.mkdirs() }
            val dest = File(dir, filename)

            if (src.exists()) {
                src.copyTo(dest, overwrite = true)
                src.delete()
                nm.notify(notifId, buildNotif(title, filename, 100, 100,
                    indeterminate = false, done = true, subtitle = "Saved to ${dir.name}/"))
            } else {
                // File already gone (edge case) — notify done anyway
                nm.notify(notifId, buildNotif(title, filename, 100, 100,
                    indeterminate = false, done = true, subtitle = "Saved to ${dir.name}/"))
            }
        } catch (e: Exception) {
            // Move failed — file stays in Downloads/
            nm.notify(notifId, buildNotif(title, filename, 100, 100,
                indeterminate = false, done = true,
                subtitle = "In Downloads/ (move failed: ${e.message})"))
        }
    }

    private fun buildNotif(
        title: String,
        filename: String,
        progress: Int,
        max: Int,
        indeterminate: Boolean,
        done: Boolean = false,
        subtitle: String? = null
    ) = NotificationCompat.Builder(ctx, NOTIF_CHANNEL_ID)
        .setSmallIcon(
            if (done) android.R.drawable.stat_sys_download_done
            else android.R.drawable.stat_sys_download
        )
        .setContentTitle(if (done) "Downloaded: $title" else title)
        .setContentText(subtitle ?: filename)
        .setProgress(max, progress, indeterminate)
        .setOngoing(!done)
        .setAutoCancel(done)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID, NOTIF_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Mod download progress" }
            nm.createNotificationChannel(channel)
        }
    }
}
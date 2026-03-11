package com.example.modrinthforandroid.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.app.DownloadManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import kotlinx.coroutines.delay
import java.io.File

const val NOTIF_CHANNEL_ID   = "modrinth_downloads"
const val NOTIF_CHANNEL_NAME = "Mod Downloads"

const val KEY_DOWNLOAD_ID    = "download_id"
const val KEY_FILENAME       = "filename"
const val KEY_TITLE          = "title"
const val KEY_NOTIF_ID       = "notif_id"
const val KEY_FINAL_DIR      = "final_dir"       // legacy plain-path fallback (no instance set)
const val KEY_TEMP_PATH      = "temp_path"
const val KEY_ROOT_URI       = "root_uri"         // content:// URI string for the instances root
const val KEY_INSTANCE_NAME  = "instance_name"    // subfolder name e.g. "test1"
const val KEY_PROJECT_TYPE   = "project_type"     // "mod", "shader", etc.

/**
 * Polls DownloadManager progress and shows a notification. On completion,
 * moves the file from Downloads/ to the correct instance subfolder.
 *
 * Two move strategies, tried in order:
 *   1. SAF (DocumentFile) — used when KEY_ROOT_URI + KEY_INSTANCE_NAME are present.
 *      This works because the user granted us a persistent content:// URI via
 *      ACTION_OPEN_DOCUMENT_TREE, so we can write anywhere inside that tree
 *      without MANAGE_EXTERNAL_STORAGE.
 *   2. Plain File I/O — fallback when no instance is active (saves to the
 *      legacy per-type folder inside Downloads/).
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

        val downloadId   = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        val filename     = inputData.getString(KEY_FILENAME)     ?: "file"
        val title        = inputData.getString(KEY_TITLE)        ?: "Downloading..."
        val notifId      = inputData.getInt(KEY_NOTIF_ID, downloadId.toInt())
        val finalDir     = inputData.getString(KEY_FINAL_DIR)
        val tempPath     = inputData.getString(KEY_TEMP_PATH)
        val rootUriStr   = inputData.getString(KEY_ROOT_URI)
        val instanceName = inputData.getString(KEY_INSTANCE_NAME)
        val projectType  = inputData.getString(KEY_PROJECT_TYPE) ?: "mod"

        if (downloadId == -1L) return Result.failure()

        // ── Poll DownloadManager until done ──────────────────────────────
        while (true) {
            val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))

            if (cursor == null || !cursor.moveToFirst()) {
                cursor?.close()
                break
            }

            val status     = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total      = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
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
                    if (!tempPath.isNullOrBlank()) {
                        if (!rootUriStr.isNullOrBlank() && !instanceName.isNullOrBlank()) {
                            // ── Strategy 1: SAF move ──────────────────────
                            moveViaSaf(
                                tempPath     = tempPath,
                                rootUriStr   = rootUriStr,
                                instanceName = instanceName,
                                projectType  = projectType,
                                filename     = filename,
                                notifId      = notifId,
                                title        = title
                            )
                        } else if (!finalDir.isNullOrBlank()) {
                            // ── Strategy 2: plain File I/O fallback ───────
                            moveViaFile(tempPath, finalDir, filename, notifId, title)
                        } else {
                            // No instance set — file stays in Downloads/
                            nm.notify(notifId, buildNotif(title, filename, 100, 100,
                                indeterminate = false, done = true,
                                subtitle = "Saved to Downloads/"))
                        }
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

    // ── SAF move ─────────────────────────────────────────────────────────────

    private fun moveViaSaf(
        tempPath: String,
        rootUriStr: String,
        instanceName: String,
        projectType: String,
        filename: String,
        notifId: Int,
        title: String
    ) {
        try {
            val rootUri  = Uri.parse(rootUriStr)
            val rootDoc  = DocumentFile.fromTreeUri(ctx, rootUri)
                ?: throw IllegalStateException("Cannot open root URI")

            // Navigate/create: <root>/<instanceName>/<subfolder>/
            val subfolder = projectTypeToSubfolder(projectType)
            val instanceDoc = rootDoc.findFile(instanceName)
                ?: rootDoc.createDirectory(instanceName)
                ?: throw IllegalStateException("Cannot find/create instance folder '$instanceName'")
            val destDir = instanceDoc.findFile(subfolder)
                ?: instanceDoc.createDirectory(subfolder)
                ?: throw IllegalStateException("Cannot find/create subfolder '$subfolder'")

            // Delete existing file with same name to allow overwrite
            destDir.findFile(filename)?.delete()

            // Create the destination file via SAF
            val mimeType = mimeTypeFor(filename)
            val destFile = destDir.createFile(mimeType, filename)
                ?: throw IllegalStateException("Cannot create destination file '$filename'")

            // Stream temp file → SAF destination
            val src = File(tempPath)
            ctx.contentResolver.openOutputStream(destFile.uri, "wt")?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } ?: throw IllegalStateException("Cannot open output stream for ${destFile.uri}")

            // Clean up temp file from Downloads/
            src.delete()

            nm.notify(notifId, buildNotif(title, filename, 100, 100,
                indeterminate = false, done = true,
                subtitle = "Saved to $instanceName/$subfolder/"))

        } catch (e: Exception) {
            // SAF move failed — notify with error, file stays in Downloads/
            nm.notify(notifId, buildNotif(title, filename, 100, 100,
                indeterminate = false, done = true,
                subtitle = "In Downloads/ (SAF error: ${e.message})"))
        }
    }

    // ── Plain File I/O fallback ───────────────────────────────────────────────

    private fun moveViaFile(
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
                    indeterminate = false, done = true,
                    subtitle = "Saved to ${dir.name}/"))
            } else {
                nm.notify(notifId, buildNotif(title, filename, 100, 100,
                    indeterminate = false, done = true,
                    subtitle = "Saved to ${dir.name}/"))
            }
        } catch (e: Exception) {
            nm.notify(notifId, buildNotif(title, filename, 100, 100,
                indeterminate = false, done = true,
                subtitle = "In Downloads/ (move failed: ${e.message})"))
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun projectTypeToSubfolder(projectType: String) = when (projectType) {
        "mod", "modpack" -> "mods"
        "shader"         -> "shaderpacks"
        "resourcepack"   -> "resourcepacks"
        "datapack"       -> "datapacks"
        "plugin"         -> "plugins"
        else             -> "mods"
    }

    private fun mimeTypeFor(filename: String) = when (filename.substringAfterLast('.').lowercase()) {
        "jar"    -> "application/java-archive"
        "zip"    -> "application/zip"
        "mrpack" -> "application/zip"
        else     -> "application/octet-stream"
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
            else      android.R.drawable.stat_sys_download
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
package com.example.modrinthforandroid.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Holds the currently active MojoLauncher instance for this app session.
 *
 * The user picks the instances ROOT folder via ACTION_OPEN_DOCUMENT_TREE,
 * which gives us a persistable content:// URI. We convert that URI to a
 * real file path for DownloadWorker to use with plain File I/O.
 */
object InstanceManager {

    private const val TAG = "InstanceManager"

    // Persisted content:// URI for the instances root folder
    var rootUri: Uri? = null
        private set

    // Display-friendly path shown in the UI (e.g. ".../MojoLauncher/instances")
    var rootDisplayPath: String? = null
        private set

    // The subfolder name the user selected (e.g. "test1")
    var activeInstanceName: String? = null
        private set

    // ── Init ──────────────────────────────────────────────────────────────────

    /**
     * Call from MainActivity.onCreate — restores state from SharedPreferences.
     */
    fun init(context: Context) {
        val settings = AppSettings.get(context)
        val uriString = settings.savedInstancesUri
        if (!uriString.isNullOrBlank()) {
            rootUri = Uri.parse(uriString)
            rootDisplayPath = settings.savedInstancesDisplayPath
            Log.d(TAG, "Restored root URI: $rootUri  display: $rootDisplayPath")
        }
        val saved = settings.savedActiveInstance
        if (!saved.isNullOrBlank()) {
            activeInstanceName = saved
            Log.d(TAG, "Restored active instance: $activeInstanceName")
        }
    }

    // ── Root folder ───────────────────────────────────────────────────────────

    /**
     * Called when the user picks a folder via ACTION_OPEN_DOCUMENT_TREE.
     * Takes persistable permission and saves to prefs.
     */
    fun setRootFromUri(context: Context, uri: Uri) {
        // Persist permission so it survives app restarts
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        rootUri = uri

        // Build a human-readable display path
        val docFile = DocumentFile.fromTreeUri(context, uri)
        rootDisplayPath = docFile?.uri?.path
            ?.replace("/tree/", "")
            ?.replace("primary:", "/storage/emulated/0/")
            ?: uri.toString()

        val settings = AppSettings.get(context)
        settings.savedInstancesUri = uri.toString()
        settings.savedInstancesDisplayPath = rootDisplayPath

        Log.d(TAG, "Root set → URI: $uri  display: $rootDisplayPath")
    }

    fun clearRoot(context: Context) {
        rootUri = null
        rootDisplayPath = null
        activeInstanceName = null
        val settings = AppSettings.get(context)
        settings.savedInstancesUri = null
        settings.savedInstancesDisplayPath = null
        settings.savedActiveInstance = null
        Log.d(TAG, "Root cleared")
    }

    // ── Active instance ───────────────────────────────────────────────────────

    fun setActiveInstance(context: Context, name: String) {
        activeInstanceName = name
        AppSettings.get(context).savedActiveInstance = name
        Log.d(TAG, "Active instance set: $name")
    }

    fun clearActiveInstance(context: Context) {
        activeInstanceName = null
        AppSettings.get(context).savedActiveInstance = null
        Log.d(TAG, "Active instance cleared")
    }

    // ── Instance listing ──────────────────────────────────────────────────────

    /**
     * Lists subfolder names inside the root URI using DocumentFile.
     */
    fun listInstances(context: Context): List<String> {
        val uri = rootUri ?: return emptyList()
        return try {
            DocumentFile.fromTreeUri(context, uri)
                ?.listFiles()
                ?.filter { it.isDirectory }
                ?.mapNotNull { it.name }
                ?.sorted()
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "listInstances failed", e)
            emptyList()
        }
    }

    // ── Download folder resolution ────────────────────────────────────────────

    /**
     * Returns the real file-system path for [projectType] files given the
     * active instance. Falls back to AppSettings legacy folder if no instance.
     */
    fun resolveDownloadFolder(context: Context, projectType: String): String {
        val instanceName = activeInstanceName
        val uri = rootUri

        return if (instanceName != null && uri != null) {
            val rootPath = uriToFilePath(uri)
            val sub = when (projectType) {
                "mod", "modpack" -> "mods"
                "shader"         -> "shaderpacks"
                "resourcepack"   -> "resourcepacks"
                "datapack"       -> "datapacks"
                "plugin"         -> "plugins"
                else             -> "mods"
            }
            val resolved = "$rootPath/$instanceName/$sub"
            Log.d(TAG, "resolveDownloadFolder → $resolved")
            resolved
        } else {
            val fallback = AppSettings.get(context).getDownloadFolder(projectType)
            Log.d(TAG, "resolveDownloadFolder fallback → $fallback")
            fallback
        }
    }

    // ── URI → file path ───────────────────────────────────────────────────────

    /**
     * Converts a DocumentTree content:// URI to an absolute file path.
     *
     * URI tree path examples from Android:
     *   /tree/primary:games/MojoLauncher/instances     → /storage/emulated/0/games/MojoLauncher/instances
     *   /tree/primary:Android/data/...                 → /storage/emulated/0/Android/data/...
     *   /tree/sdcard1:some/path                        → /storage/sdcard1/some/path
     *   /tree/storage/emulated/0/some/path             → /storage/emulated/0/some/path  (already absolute)
     *
     * The key fix: if the decoded relative part already starts with '/', it IS
     * an absolute path — return it directly without prepending /storage/.
     */
    fun uriToFilePath(uri: Uri): String {
        // DocumentFile tree URIs encode the path as the last segment of /tree/<encoded>
        // Uri.getLastPathSegment() decodes it for us: e.g. "primary:games/MojoLauncher"
        val encoded = uri.lastPathSegment ?: uri.path ?: return ""

        Log.d(TAG, "uriToFilePath raw segment: '$encoded'")

        return when {
            // Already an absolute path (starts with /) — use as-is
            encoded.startsWith("/") -> {
                Log.d(TAG, "uriToFilePath: already absolute → $encoded")
                encoded
            }

            // Standard DocumentProvider format: "primary:<relative>" or "<volume>:<relative>"
            encoded.contains(":") -> {
                val colonIdx = encoded.indexOf(':')
                val volume   = encoded.substring(0, colonIdx)
                val relative = encoded.substring(colonIdx + 1)

                val base = if (volume.equals("primary", ignoreCase = true))
                    "/storage/emulated/0"
                else
                    "/storage/$volume"

                val path = "$base/$relative"
                Log.d(TAG, "uriToFilePath: $volume + $relative → $path")
                path
            }

            // Fallback: treat as relative to primary storage
            else -> {
                val path = "/storage/emulated/0/$encoded"
                Log.d(TAG, "uriToFilePath: fallback → $path")
                path
            }
        }
    }
}
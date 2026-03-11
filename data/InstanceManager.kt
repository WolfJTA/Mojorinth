package com.example.modrinthforandroid.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.modrinthforandroid.data.model.MojoInstance
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
    private const val INSTANCE_CONFIG_FILENAME = "mojo_instance.json"

    // Persisted content:// URI for the instances root folder
    var rootUri: Uri? = null
        private set

    // Display-friendly path shown in the UI (e.g. ".../MojoLauncher/instances")
    var rootDisplayPath: String? = null
        private set

    // The subfolder name the user selected (e.g. "test1")
    var activeInstanceName: String? = null
        private set

    // Parsed config for the active instance — null if not yet read or failed
    var activeInstanceConfig: MojoInstance? = null
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
            // Re-read the config so it's available immediately after app restart
            readInstanceConfig(context, saved)
        }
    }

    // ── Root folder ───────────────────────────────────────────────────────────

    fun setRootFromUri(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        rootUri = uri

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
        activeInstanceConfig = null
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
        // Parse the config file immediately so the UI can reflect it
        readInstanceConfig(context, name)
    }

    fun clearActiveInstance(context: Context) {
        activeInstanceName = null
        activeInstanceConfig = null
        AppSettings.get(context).savedActiveInstance = null
        Log.d(TAG, "Active instance cleared")
    }

    // ── Instance config reading ───────────────────────────────────────────────

    /**
     * Reads and parses mojo_instance.json from the given instance subfolder.
     * Result is stored in [activeInstanceConfig] and also returned for
     * callers that want it immediately.
     *
     * Uses SAF (DocumentFile) so no MANAGE_EXTERNAL_STORAGE is needed.
     */
    fun readInstanceConfig(context: Context, instanceName: String): MojoInstance? {
        val uri = rootUri ?: run {
            Log.w(TAG, "readInstanceConfig: no root URI set")
            return null
        }

        return try {
            val rootDoc = DocumentFile.fromTreeUri(context, uri)
                ?: throw IllegalStateException("Cannot open root URI")

            val instanceDoc = rootDoc.findFile(instanceName)
                ?: throw IllegalStateException("Instance folder '$instanceName' not found")

            val configFile = instanceDoc.findFile(INSTANCE_CONFIG_FILENAME)
                ?: throw IllegalStateException("$INSTANCE_CONFIG_FILENAME not found in '$instanceName'")

            val json = context.contentResolver
                .openInputStream(configFile.uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: throw IllegalStateException("Could not read $INSTANCE_CONFIG_FILENAME")

            val parsed = MojoInstance.parse(json)
            activeInstanceConfig = parsed

            if (parsed != null) {
                Log.d(TAG, "Parsed config: ${parsed.summary} renderer=${parsed.renderer}")
            } else {
                Log.w(TAG, "Failed to parse $INSTANCE_CONFIG_FILENAME")
            }

            parsed
        } catch (e: Exception) {
            Log.e(TAG, "readInstanceConfig failed for '$instanceName'", e)
            activeInstanceConfig = null
            null
        }
    }

    // ── Instance listing ──────────────────────────────────────────────────────

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
            "$rootPath/$instanceName/$sub"
        } else {
            AppSettings.get(context).getDownloadFolder(projectType)
        }
    }

    // ── URI → file path ───────────────────────────────────────────────────────

    fun uriToFilePath(uri: Uri): String {
        val encoded = uri.lastPathSegment ?: uri.path ?: return ""
        return when {
            encoded.startsWith("/") -> encoded
            encoded.contains(":") -> {
                val colonIdx = encoded.indexOf(':')
                val volume   = encoded.substring(0, colonIdx)
                val relative = encoded.substring(colonIdx + 1)
                val base = if (volume.equals("primary", ignoreCase = true))
                    "/storage/emulated/0" else "/storage/$volume"
                "$base/$relative"
            }
            else -> "/storage/emulated/0/$encoded"
        }
    }
}
package com.example.modrinthforandroid.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.core.content.edit

class AppSettings private constructor(private val prefs: SharedPreferences) {

    companion object {
        private const val PREFS_NAME = "modrinth_settings"
        @Volatile private var instance: AppSettings? = null
        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }

        const val THEME_DARK   = "dark"
        const val THEME_LIGHT  = "light"
        const val THEME_AMOLED = "amoled"

        // Default MojoLauncher instances path
        val DEFAULT_MOJO_INSTANCES_PATH: String get() =
            Environment.getExternalStorageDirectory().absolutePath +
                    "/games/MojoLauncher/instances"

        // Subfolder names inside a MojoLauncher instance — used in Settings display
        val TYPE_FOLDER_DEFAULTS = mapOf(
            "mod"          to "mods",
            "modpack"      to "mods",
            "shader"       to "shaderpacks",
            "resourcepack" to "resourcepacks",
            "datapack"     to "datapacks",
            "plugin"       to "plugins"
        )
    }

    // ── Theme ──────────────────────────────────────────────────────────────
    var theme: String
        get()  = prefs.getString("theme", THEME_DARK) ?: THEME_DARK
        set(v) = prefs.edit { putString("theme", v) }

    // ── Duplicate download warning ─────────────────────────────────────────
    var showDuplicateWarning: Boolean
        get()  = prefs.getBoolean("show_duplicate_warning", true)
        set(v) = prefs.edit { putBoolean("show_duplicate_warning", v) }

    // ── MojoLauncher instances root path ───────────────────────────────────
    // Persisted so the user only needs to correct it once if their path differs
    var mojoInstancesPath: String
        get()  = prefs.getString("mojo_instances_path", DEFAULT_MOJO_INSTANCES_PATH)
            ?: DEFAULT_MOJO_INSTANCES_PATH
        set(v) = prefs.edit { putString("mojo_instances_path", v) }

    // ── Legacy per-type fallback folder ───────────────────────────────────
    // Used when no instance is active. Defaults to Downloads/<type>.
    fun getDownloadFolder(projectType: String): String {
        val defaultSub = when (projectType) {
            "mod", "modpack" -> "Mods"
            "shader"         -> "Shaders"
            "resourcepack"   -> "ResourcePacks"
            "datapack"       -> "DataPacks"
            "plugin"         -> "Plugins"
            else             -> "Mods"
        }
        val default = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .absolutePath + "/$defaultSub"
        return prefs.getString("folder_$projectType", default) ?: default
    }

    fun setDownloadFolder(projectType: String, path: String) =
        prefs.edit { putString("folder_$projectType", path) }
}
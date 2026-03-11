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

        val TYPE_FOLDER_DEFAULTS = mapOf(
            "mod"          to "mods",
            "modpack"      to "mods",
            "shader"       to "shaderpacks",
            "resourcepack" to "resourcepacks",
            "datapack"     to "datapacks",
            "plugin"       to "plugins"
        )
    }

    var theme: String
        get()  = prefs.getString("theme", THEME_DARK) ?: THEME_DARK
        set(v) = prefs.edit { putString("theme", v) }

    var showDuplicateWarning: Boolean
        get()  = prefs.getBoolean("show_duplicate_warning", true)
        set(v) = prefs.edit { putBoolean("show_duplicate_warning", v) }

    // ── Instances root URI (from system folder picker) ─────────────────────
    var savedInstancesUri: String?
        get()  = prefs.getString("instances_uri", null)
        set(v) = prefs.edit {
            if (v != null) putString("instances_uri", v) else remove("instances_uri")
        }

    var savedInstancesDisplayPath: String?
        get()  = prefs.getString("instances_display_path", null)
        set(v) = prefs.edit {
            if (v != null) putString("instances_display_path", v) else remove("instances_display_path")
        }

    // ── Active instance subfolder name ─────────────────────────────────────
    var savedActiveInstance: String?
        get()  = prefs.getString("active_instance", null)
        set(v) = prefs.edit {
            if (v != null) putString("active_instance", v) else remove("active_instance")
        }

    // ── Fallback per-type download folders ────────────────────────────────
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
package com.example.modrinthforandroid.data

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Holds the currently active MojoLauncher instance for this app session.
 * Not persisted — the user picks (or re-picks) an instance each launch.
 *
 * MojoLauncher stores instances at:
 *   <externalStorage>/games/MojoLauncher/instances/<instanceName>/
 *
 * Subfolders per content type:
 *   mods/          ← mod, modpack
 *   shaderpacks/   ← shader
 *   resourcepacks/ ← resourcepack
 *   datapacks/     ← datapack
 *   plugins/       ← plugin
 */
object InstanceManager {

    // The subfolder name inside instances/ — set by the user on the home screen
    var activeInstanceName: String? = null
        private set

    // Full path to the MojoLauncher instances directory
    fun instancesRoot(context: Context): String {
        val settings = AppSettings.get(context)
        return settings.mojoInstancesPath
    }

    /**
     * Set the active instance by full path (from browser) or bare name (typed manually).
     * If a full absolute path is given we use it directly; otherwise we resolve it
     * relative to the instances root from Settings.
     */
    fun setInstance(pathOrName: String) {
        activeInstanceName = pathOrName.trim()
    }

    /** Clear the active instance (user taps "Clear" on home screen). */
    fun clearInstance() {
        activeInstanceName = null
    }

    /**
     * Returns the correct download folder for [projectType] given the active instance.
     * Falls back to the legacy per-type folder from AppSettings if no instance is set.
     */
    fun resolveDownloadFolder(context: Context, projectType: String): String {
        val instancePath = activeInstanceName
        return if (instancePath != null) {
            // If it looks like an absolute path use it directly, otherwise
            // resolve relative to the user-configured instances root
            val instanceRoot = if (instancePath.startsWith("/"))
                File(instancePath)
            else
                File(instancesRoot(context), instancePath)

            val sub = when (projectType) {
                "mod", "modpack" -> "mods"
                "shader"         -> "shaderpacks"
                "resourcepack"   -> "resourcepacks"
                "datapack"       -> "datapacks"
                "plugin"         -> "plugins"
                else             -> "mods"
            }
            File(instanceRoot, sub).absolutePath
        } else {
            AppSettings.get(context).getDownloadFolder(projectType)
        }
    }

    /**
     * Scans the instances root and returns all instance folder names found.
     * Returns empty list if the path doesn't exist yet.
     */
    fun listInstances(context: Context): List<String> {
        val root = File(instancesRoot(context))
        if (!root.exists() || !root.isDirectory) return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    /**
     * Returns true if the given instance folder actually exists on disk.
     */
    fun instanceExists(context: Context, name: String): Boolean {
        val root = File(instancesRoot(context), name.trim())
        return root.exists() && root.isDirectory
    }
}
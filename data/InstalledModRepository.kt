package com.example.modrinthforandroid.data

import android.content.Context
import com.example.modrinthforandroid.data.db.AppDatabase
import com.example.modrinthforandroid.data.db.InstalledMod
import kotlinx.coroutines.flow.Flow

class InstalledModRepository(context: Context) {

    private val dao = AppDatabase.get(context).installedModDao()

    // ── Write ─────────────────────────────────────────────────────────────────

    suspend fun recordInstall(
        projectId:    String,
        instanceName: String,
        fileName:     String,
        projectType:  String
    ) {
        dao.upsert(
            InstalledMod(
                projectId    = projectId,
                instanceName = instanceName,
                fileName     = fileName,
                projectType  = projectType
            )
        )
    }

    /**
     * Reconcile Room records against the actual files on disk for [instanceName].
     * Pass in all filenames currently present in the instance's mod/shader/pack
     * folders; any Room record whose filename is not in that set gets pruned.
     *
     * If [liveFileNames] is empty we skip pruning — this avoids wiping everything
     * if the folder scan fails or the instance has no files yet.
     */
    suspend fun reconcile(instanceName: String, liveFileNames: List<String>) {
        if (liveFileNames.isEmpty()) return
        dao.pruneStale(instanceName, liveFileNames)
    }

    suspend fun deleteAllForInstance(instanceName: String) {
        dao.deleteAllForInstance(instanceName)
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Live Flow of installed project IDs for the given instance. */
    fun observeInstalledIds(instanceName: String): Flow<List<String>> =
        dao.observeInstalledProjectIds(instanceName)

    /** One-shot snapshot — for ViewModels that just need the current set. */
    suspend fun getInstalledIds(instanceName: String): Set<String> =
        dao.getInstalledProjectIds(instanceName).toHashSet()

    /** Which instances have [projectId] installed — for mod detail screen. */
    suspend fun getInstancesForProject(projectId: String): List<String> =
        dao.getInstancesForProject(projectId)
}

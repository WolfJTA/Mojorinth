package com.example.modrinthforandroid.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InstalledModDao {

    // ── Write ─────────────────────────────────────────────────────────────────

    /** Insert or update — replaces on conflict so re-downloads refresh the record. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mod: InstalledMod)

    /** Remove a single record by projectId + instanceName. */
    @Query("DELETE FROM installed_mods WHERE projectId = :projectId AND instanceName = :instanceName")
    suspend fun delete(projectId: String, instanceName: String)

    /**
     * Reconciliation: delete any records whose fileName is NOT in the provided
     * set of live filenames for a given instance. Called by StatsViewModel after
     * scanning the actual folder on disk.
     */
    @Query("""
        DELETE FROM installed_mods
        WHERE instanceName = :instanceName
          AND fileName NOT IN (:liveFileNames)
    """)
    suspend fun pruneStale(instanceName: String, liveFileNames: List<String>)

    /** Wipe all records for an instance (e.g. if the instance folder is deleted). */
    @Query("DELETE FROM installed_mods WHERE instanceName = :instanceName")
    suspend fun deleteAllForInstance(instanceName: String)

    // ── Read ──────────────────────────────────────────────────────────────────

    /** All project IDs installed in a specific instance — used to drive badges. */
    @Query("SELECT projectId FROM installed_mods WHERE instanceName = :instanceName")
    fun observeInstalledProjectIds(instanceName: String): Flow<List<String>>

    /** One-shot version for ViewModels that don't need live updates. */
    @Query("SELECT projectId FROM installed_mods WHERE instanceName = :instanceName")
    suspend fun getInstalledProjectIds(instanceName: String): List<String>

    /** Which instances have this project installed — shown on the mod detail screen. */
    @Query("SELECT instanceName FROM installed_mods WHERE projectId = :projectId")
    suspend fun getInstancesForProject(projectId: String): List<String>

    /** All filenames currently logged for an instance — used during reconciliation. */
    @Query("SELECT fileName FROM installed_mods WHERE instanceName = :instanceName")
    suspend fun getFileNamesForInstance(instanceName: String): List<String>

    /** Full records for an instance — useful for debugging / future features. */
    @Query("SELECT * FROM installed_mods WHERE instanceName = :instanceName ORDER BY installedAt DESC")
    suspend fun getAllForInstance(instanceName: String): List<InstalledMod>
}

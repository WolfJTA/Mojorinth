package com.example.modrinthforandroid.data.db

import androidx.room.Entity
import androidx.room.Index

/**
 * Tracks every mod/shader/resource pack downloaded through Mojorinth.
 *
 * Primary key is (projectId + instanceName) so each project can only be
 * "installed" once per instance — re-downloading just updates the record.
 *
 * [fileName] is stored so the reconciliation pass can verify the file still
 * exists on disk and prune stale records.
 */
@Entity(
    tableName = "installed_mods",
    primaryKeys = ["projectId", "instanceName"],
    indices = [Index("instanceName"), Index("projectId")]
)
data class InstalledMod(
    val projectId:    String,
    val instanceName: String,
    val fileName:     String,
    val projectType:  String,   // "mod", "shader", "resourcepack", etc.
    val installedAt:  Long = System.currentTimeMillis()
)

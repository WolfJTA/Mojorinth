package com.example.modrinthforandroid.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.modrinthforandroid.data.InstanceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "InstanceManagerVM"

// ─── Model ────────────────────────────────────────────────────────────────────

/**
 * Represents a single file entry inside an instance subfolder (mods/,
 * shaderpacks/, or resourcepacks/).
 *
 * [isDisabled] is true when the filename ends with ".disabled" — the standard
 * MojLauncher / MultiMC convention for temporarily disabling a mod without
 * deleting it.
 */
data class InstanceFileEntry(
    val uri: Uri,
    /** Full filename as stored on disk, e.g. "sodium-0.6.0.jar" */
    val filename: String,
    /** Filename without the trailing ".disabled" suffix, for display. */
    val displayName: String,
    /** True when the file ends with ".disabled" */
    val isDisabled: Boolean,
    /** Cleaned file extension shown in the chip (never "disabled"). */
    val extension: String
) {
    companion object {
        fun from(doc: DocumentFile): InstanceFileEntry? {
            val name = doc.name ?: return null
            val isDisabled = name.endsWith(".disabled", ignoreCase = true)
            // Strip the .disabled suffix to get the "real" name
            val realName = if (isDisabled) name.removeSuffix(".disabled") else name
            // Extension is the last segment after the final dot in the real name
            val ext = realName.substringAfterLast('.', missingDelimiterValue = "")
                .ifBlank { "file" }
            // Display name is the real name without the extension for concision,
            // but keeping both parts is cleaner — keep full realName here so
            // users see "sodium-0.6.0.jar" rather than "sodium-0.6.0"
            return InstanceFileEntry(
                uri         = doc.uri,
                filename    = name,
                displayName = realName,
                isDisabled  = isDisabled,
                extension   = ext
            )
        }
    }
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class InstanceManagerViewModel(private val appContext: Context) : ViewModel() {

    private val _allFiles    = MutableStateFlow<List<InstanceFileEntry>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading   = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val isLoading:   StateFlow<Boolean> = _isLoading.asStateFlow()
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** Filtered + sorted file list derived from raw list + search query. */
    val files: StateFlow<List<InstanceFileEntry>> = combine(
        _allFiles, _searchQuery
    ) { all, query ->
        if (query.isBlank()) all
        else all.filter { it.displayName.contains(query, ignoreCase = true) }
    }.stateIn(
        scope         = viewModelScope,
        started       = SharingStarted.WhileSubscribed(5_000),
        initialValue  = emptyList()
    )

    // Tracks which subfolder is currently open so refreshes work after mutations
    private var currentSubfolder: String = "mods"

    // ── Public API ────────────────────────────────────────────────────────────

    fun setSearch(query: String) {
        _searchQuery.value = query
    }

    /** Load (or reload) the files from [subfolder] inside the active instance. */
    fun loadFiles(subfolder: String) {
        currentSubfolder = subfolder
        _errorMessage.value = null
        viewModelScope.launch {
            _isLoading.value = true
            _allFiles.value  = fetchFiles(subfolder)
            _isLoading.value = false
        }
    }

    /**
     * Toggle a mod's disabled state:
     * - enabled  (.jar)      → rename to .jar.disabled
     * - disabled (.disabled) → rename back to original name
     *
     * Uses SAF rename, which works without MANAGE_EXTERNAL_STORAGE.
     */
    fun toggleDisabled(entry: InstanceFileEntry) {
        viewModelScope.launch {
            _errorMessage.value = null
            val success = withContext(Dispatchers.IO) {
                try {
                    val doc = DocumentFile.fromSingleUri(appContext, entry.uri)
                        ?: return@withContext false
                    val newName = if (entry.isDisabled) {
                        // Re-enable: drop the .disabled suffix
                        entry.filename.removeSuffix(".disabled")
                    } else {
                        // Disable: append .disabled
                        "${entry.filename}.disabled"
                    }
                    doc.renameTo(newName).also {
                        Log.d(TAG, "renameTo($newName) → $it")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "toggleDisabled failed for ${entry.filename}", e)
                    false
                }
            }
            if (success) {
                loadFiles(currentSubfolder) // Refresh list
            } else {
                _errorMessage.value = "Could not rename \"${entry.displayName}\". Try again."
            }
        }
    }

    /** Permanently delete [entry] from the instance folder. */
    fun deleteFile(entry: InstanceFileEntry) {
        viewModelScope.launch {
            _errorMessage.value = null
            val success = withContext(Dispatchers.IO) {
                try {
                    val doc = DocumentFile.fromSingleUri(appContext, entry.uri)
                        ?: return@withContext false
                    doc.delete().also {
                        Log.d(TAG, "delete(${entry.filename}) → $it")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "deleteFile failed for ${entry.filename}", e)
                    false
                }
            }
            if (success) {
                // Remove locally — faster than a full reload
                _allFiles.value = _allFiles.value.filter { it.uri != entry.uri }
            } else {
                _errorMessage.value = "Could not delete \"${entry.displayName}\". Try again."
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun fetchFiles(subfolder: String): List<InstanceFileEntry> =
        withContext(Dispatchers.IO) {
            val rootUri      = InstanceManager.rootUri      ?: return@withContext emptyList()
            val instanceName = InstanceManager.activeInstanceName ?: return@withContext emptyList()
            try {
                val rootDoc     = DocumentFile.fromTreeUri(appContext, rootUri)
                    ?: return@withContext emptyList()
                val instanceDoc = rootDoc.findFile(instanceName)
                    ?: return@withContext emptyList()
                val subDoc      = instanceDoc.findFile(subfolder)
                    ?: return@withContext emptyList() // Subfolder may not exist yet

                subDoc.listFiles()
                    .filter { it.isFile }
                    .mapNotNull { InstanceFileEntry.from(it) }
                    .sortedWith(
                        // Disabled files sink to the bottom; within each group sort by name
                        compareBy({ it.isDisabled }, { it.displayName.lowercase() })
                    )
            } catch (e: Exception) {
                Log.e(TAG, "fetchFiles failed for $instanceName/$subfolder", e)
                _errorMessage.value = "Failed to read $subfolder/ folder."
                emptyList()
            }
        }
}

// ─── Factory ─────────────────────────────────────────────────────────────────

class InstanceManagerViewModelFactory(private val appContext: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return InstanceManagerViewModel(appContext) as T
    }
}
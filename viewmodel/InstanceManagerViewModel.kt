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

// ─── Filters ──────────────────────────────────────────────────────────────────

enum class DisabledFilter { ALL, ENABLED_ONLY, DISABLED_ONLY }

/**
 * Client-side filters applied to the already-loaded file list.
 * All filtering is instant — no re-fetch needed.
 */
data class ManageFilters(
    val disabledFilter: DisabledFilter = DisabledFilter.ALL,
    /** Null = any extension. Otherwise only files whose extension matches. */
    val extensionFilter: String? = null,
    val sortAZ: Boolean = true
) {
    val isActive: Boolean
        get() = disabledFilter != DisabledFilter.ALL ||
                extensionFilter != null ||
                !sortAZ

    val activeCount: Int
        get() = listOf(
            disabledFilter != DisabledFilter.ALL,
            extensionFilter != null,
            !sortAZ
        ).count { it }
}

// ─── Model ────────────────────────────────────────────────────────────────────

data class InstanceFileEntry(
    val uri: Uri,
    val parentUri: Uri,
    val filename: String,
    val displayName: String,
    val isDisabled: Boolean,
    val extension: String
) {
    companion object {
        fun from(doc: DocumentFile, parentUri: Uri): InstanceFileEntry? {
            val name       = doc.name ?: return null
            val isDisabled = name.endsWith(".disabled", ignoreCase = true)
            val realName   = if (isDisabled) name.removeSuffix(".disabled") else name
            val ext        = realName.substringAfterLast('.', missingDelimiterValue = "").ifBlank { "file" }
            return InstanceFileEntry(
                uri         = doc.uri,
                parentUri   = parentUri,
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

    private val _allFiles     = MutableStateFlow<List<InstanceFileEntry>>(emptyList())
    private val _searchQuery  = MutableStateFlow("")
    private val _filters      = MutableStateFlow(ManageFilters())
    private val _isLoading    = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val searchQuery:  StateFlow<String>        = _searchQuery.asStateFlow()
    val filters:      StateFlow<ManageFilters> = _filters.asStateFlow()
    val isLoading:    StateFlow<Boolean>       = _isLoading.asStateFlow()
    val errorMessage: StateFlow<String?>       = _errorMessage.asStateFlow()

    /** Derived list: search + filters applied together. */
    val files: StateFlow<List<InstanceFileEntry>> = combine(
        _allFiles, _searchQuery, _filters
    ) { all, query, f ->
        all
            // 1. Search
            .let { list ->
                if (query.isBlank()) list
                else list.filter { it.displayName.contains(query, ignoreCase = true) }
            }
            // 2. Disabled filter
            .let { list ->
                when (f.disabledFilter) {
                    DisabledFilter.ENABLED_ONLY  -> list.filter { !it.isDisabled }
                    DisabledFilter.DISABLED_ONLY -> list.filter {  it.isDisabled }
                    DisabledFilter.ALL           -> list
                }
            }
            // 3. Extension filter
            .let { list ->
                if (f.extensionFilter == null) list
                else list.filter { it.extension.equals(f.extensionFilter, ignoreCase = true) }
            }
            // 4. Sort
            .let { list ->
                if (f.sortAZ) list.sortedBy { it.displayName.lowercase() }
                else list.sortedByDescending { it.displayName.lowercase() }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All unique extensions present in the raw (unfiltered) file list. */
    val availableExtensions: StateFlow<List<String>> = _allFiles.map { files ->
        files.map { it.extension }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var currentSubfolder = "mods"

    // ── Public API ────────────────────────────────────────────────────────────

    fun setSearch(query: String)           { _searchQuery.value = query }
    fun setFilters(f: ManageFilters)       { _filters.value = f }
    fun resetFilters()                     { _filters.value = ManageFilters() }

    fun loadFiles(subfolder: String) {
        currentSubfolder    = subfolder
        _errorMessage.value = null
        // Reset extension filter when switching tabs — the extensions differ per tab
        _filters.value = _filters.value.copy(extensionFilter = null)
        viewModelScope.launch {
            _isLoading.value = true
            _allFiles.value  = fetchFiles(subfolder)
            _isLoading.value = false
        }
    }

    /**
     * Toggle disabled: rename via SAF tree root (fromSingleUri can't rename).
     */
    fun toggleDisabled(entry: InstanceFileEntry) {
        viewModelScope.launch {
            _errorMessage.value = null
            val newName = if (entry.isDisabled)
                entry.filename.removeSuffix(".disabled")
            else
                "${entry.filename}.disabled"

            val success = withContext(Dispatchers.IO) {
                try {
                    val rootUri      = InstanceManager.rootUri ?: return@withContext false
                    val instanceName = InstanceManager.activeInstanceName ?: return@withContext false
                    val rootDoc      = DocumentFile.fromTreeUri(appContext, rootUri) ?: return@withContext false
                    val instanceDoc  = rootDoc.findFile(instanceName)               ?: return@withContext false
                    val subDoc       = instanceDoc.findFile(currentSubfolder)       ?: return@withContext false
                    val fileDoc      = subDoc.findFile(entry.filename)              ?: return@withContext false

                    Log.d(TAG, "Renaming '${entry.filename}' → '$newName'")
                    fileDoc.renameTo(newName).also { Log.d(TAG, "renameTo=$it") }
                } catch (e: Exception) {
                    Log.e(TAG, "toggleDisabled failed", e); false
                }
            }
            if (success) loadFiles(currentSubfolder)
            else _errorMessage.value = "Could not rename \"${entry.displayName}\"."
        }
    }

    fun deleteFile(entry: InstanceFileEntry) {
        viewModelScope.launch {
            _errorMessage.value = null
            val success = withContext(Dispatchers.IO) {
                try {
                    val rootUri      = InstanceManager.rootUri ?: return@withContext false
                    val instanceName = InstanceManager.activeInstanceName ?: return@withContext false
                    val rootDoc      = DocumentFile.fromTreeUri(appContext, rootUri) ?: return@withContext false
                    val instanceDoc  = rootDoc.findFile(instanceName)               ?: return@withContext false
                    val subDoc       = instanceDoc.findFile(currentSubfolder)       ?: return@withContext false
                    val fileDoc      = subDoc.findFile(entry.filename)              ?: return@withContext false

                    Log.d(TAG, "Deleting '${entry.filename}'")
                    fileDoc.delete().also { Log.d(TAG, "delete=$it") }
                } catch (e: Exception) {
                    Log.e(TAG, "deleteFile failed", e); false
                }
            }
            if (success) _allFiles.value = _allFiles.value.filter { it.uri != entry.uri }
            else _errorMessage.value = "Could not delete \"${entry.displayName}\"."
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun fetchFiles(subfolder: String): List<InstanceFileEntry> =
        withContext(Dispatchers.IO) {
            val rootUri      = InstanceManager.rootUri            ?: return@withContext emptyList()
            val instanceName = InstanceManager.activeInstanceName  ?: return@withContext emptyList()
            try {
                val rootDoc     = DocumentFile.fromTreeUri(appContext, rootUri) ?: return@withContext emptyList()
                val instanceDoc = rootDoc.findFile(instanceName)                ?: return@withContext emptyList()
                val subDoc      = instanceDoc.findFile(subfolder)               ?: return@withContext emptyList()

                subDoc.listFiles()
                    .filter { it.isFile }
                    .mapNotNull { InstanceFileEntry.from(it, subDoc.uri) }
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
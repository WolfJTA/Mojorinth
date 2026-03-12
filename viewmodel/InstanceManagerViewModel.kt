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
 * Represents a single file inside an instance subfolder.
 *
 * [isDisabled] is true when the filename ends with ".disabled" — the standard
 * mod-manager convention for temporarily disabling a mod without deleting it.
 *
 * We store the parent folder's URI separately so rename/delete can re-resolve
 * the file through the SAF tree rather than via fromSingleUri (which can't
 * rename child-document URIs obtained from listFiles()).
 */
data class InstanceFileEntry(
    /** URI of the file itself (child doc URI from listFiles). Used for display only. */
    val uri: Uri,
    /** URI of the parent DocumentFile directory, for re-resolving the file by name. */
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
    private val _isLoading    = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val searchQuery:  StateFlow<String>              = _searchQuery.asStateFlow()
    val isLoading:    StateFlow<Boolean>             = _isLoading.asStateFlow()
    val errorMessage: StateFlow<String?>             = _errorMessage.asStateFlow()

    val files: StateFlow<List<InstanceFileEntry>> = combine(_allFiles, _searchQuery) { all, query ->
        if (query.isBlank()) all
        else all.filter { it.displayName.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var currentSubfolder = "mods"

    // ── Public API ────────────────────────────────────────────────────────────

    fun setSearch(query: String) { _searchQuery.value = query }

    fun loadFiles(subfolder: String) {
        currentSubfolder  = subfolder
        _errorMessage.value = null
        viewModelScope.launch {
            _isLoading.value = true
            _allFiles.value  = fetchFiles(subfolder)
            _isLoading.value = false
        }
    }

    /**
     * Toggle disabled state by renaming via the SAF tree.
     *
     * The critical fix: instead of DocumentFile.fromSingleUri (which can't
     * rename child-document URIs), we navigate from the root tree URI down to
     * the parent directory and call renameTo() on the DocumentFile obtained
     * from that tree context. This is the only reliable way to rename files
     * accessed via ACTION_OPEN_DOCUMENT_TREE on Android 11+.
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
                    val rootUri      = InstanceManager.rootUri
                        ?: return@withContext false.also { Log.e(TAG, "toggleDisabled: no root URI") }
                    val instanceName = InstanceManager.activeInstanceName
                        ?: return@withContext false.also { Log.e(TAG, "toggleDisabled: no active instance") }

                    // Navigate root → instance → subfolder via the persistent tree URI
                    val rootDoc     = DocumentFile.fromTreeUri(appContext, rootUri)
                        ?: return@withContext false.also { Log.e(TAG, "toggleDisabled: fromTreeUri failed") }
                    val instanceDoc = rootDoc.findFile(instanceName)
                        ?: return@withContext false.also { Log.e(TAG, "toggleDisabled: instance folder not found") }
                    val subDoc      = instanceDoc.findFile(currentSubfolder)
                        ?: return@withContext false.also { Log.e(TAG, "toggleDisabled: subfolder not found") }

                    // Re-find the file inside the tree — this gives us a tree-backed
                    // DocumentFile which supports renameTo(), unlike fromSingleUri().
                    val fileDoc = subDoc.findFile(entry.filename)
                        ?: return@withContext false.also { Log.e(TAG, "toggleDisabled: file '${entry.filename}' not found in tree") }

                    Log.d(TAG, "Renaming '${entry.filename}' → '$newName' (uri=${fileDoc.uri})")
                    val result = fileDoc.renameTo(newName)
                    Log.d(TAG, "renameTo result: $result")
                    result
                } catch (e: Exception) {
                    Log.e(TAG, "toggleDisabled exception for '${entry.filename}'", e)
                    false
                }
            }

            if (success) {
                loadFiles(currentSubfolder)
            } else {
                _errorMessage.value = "Could not rename \"${entry.displayName}\". Check logs."
            }
        }
    }

    /** Delete a file by re-resolving it through the SAF tree. */
    fun deleteFile(entry: InstanceFileEntry) {
        viewModelScope.launch {
            _errorMessage.value = null
            val success = withContext(Dispatchers.IO) {
                try {
                    val rootUri      = InstanceManager.rootUri      ?: return@withContext false
                    val instanceName = InstanceManager.activeInstanceName ?: return@withContext false

                    val rootDoc     = DocumentFile.fromTreeUri(appContext, rootUri) ?: return@withContext false
                    val instanceDoc = rootDoc.findFile(instanceName)               ?: return@withContext false
                    val subDoc      = instanceDoc.findFile(currentSubfolder)       ?: return@withContext false
                    val fileDoc     = subDoc.findFile(entry.filename)               ?: return@withContext false

                    Log.d(TAG, "Deleting '${entry.filename}' (uri=${fileDoc.uri})")
                    fileDoc.delete().also { Log.d(TAG, "delete result: $it") }
                } catch (e: Exception) {
                    Log.e(TAG, "deleteFile exception for '${entry.filename}'", e)
                    false
                }
            }
            if (success) {
                _allFiles.value = _allFiles.value.filter { it.uri != entry.uri }
            } else {
                _errorMessage.value = "Could not delete \"${entry.displayName}\". Try again."
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun fetchFiles(subfolder: String): List<InstanceFileEntry> =
        withContext(Dispatchers.IO) {
            val rootUri      = InstanceManager.rootUri           ?: return@withContext emptyList()
            val instanceName = InstanceManager.activeInstanceName ?: return@withContext emptyList()
            try {
                val rootDoc     = DocumentFile.fromTreeUri(appContext, rootUri) ?: return@withContext emptyList()
                val instanceDoc = rootDoc.findFile(instanceName)                ?: return@withContext emptyList()
                val subDoc      = instanceDoc.findFile(subfolder)               ?: return@withContext emptyList()

                subDoc.listFiles()
                    .filter { it.isFile }
                    .mapNotNull { InstanceFileEntry.from(it, subDoc.uri) }
                    .sortedWith(compareBy({ it.isDisabled }, { it.displayName.lowercase() }))
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
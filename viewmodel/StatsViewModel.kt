package com.example.modrinthforandroid.viewmodel

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.ui.screens.InstanceStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatsViewModel(private val appContext: Context) : ViewModel() {

    private val _isScanning  = MutableStateFlow(true)
    private val _statsList   = MutableStateFlow<List<InstanceStats>>(emptyList())
    private val _totalBytes  = MutableStateFlow(0L)

    val isScanning: StateFlow<Boolean>           = _isScanning.asStateFlow()
    val statsList:  StateFlow<List<InstanceStats>> = _statsList.asStateFlow()
    val totalBytes: StateFlow<Long>              = _totalBytes.asStateFlow()

    init {
        scan()
    }

    /** Re-runs the scan (e.g. after the user adds/removes mods). */
    fun refresh() {
        if (_isScanning.value) return
        scan()
    }

    private fun scan() {
        viewModelScope.launch {
            _isScanning.value = true
            withContext(Dispatchers.IO) {
                val rootUri = InstanceManager.rootUri
                if (rootUri == null) {
                    _isScanning.value = false
                    return@withContext
                }

                val rootDoc   = DocumentFile.fromTreeUri(appContext, rootUri)
                val instances = rootDoc?.listFiles()?.filter { it.isDirectory } ?: emptyList()

                val results = mutableListOf<InstanceStats>()
                for (instanceDir in instances) {
                    val folderName  = instanceDir.name ?: continue
                    val displayName = folderName

                    val modFiles     = instanceDir.findFile("mods")?.listFiles() ?: emptyArray()
                    val modsEnabled  = modFiles.count { f ->
                        val n = f.name ?: ""
                        n.endsWith(".jar", ignoreCase = true) &&
                                !n.endsWith(".disabled", ignoreCase = true)
                    }
                    val modsDisabled = modFiles.count { f ->
                        (f.name ?: "").endsWith(".disabled", ignoreCase = true)
                    }
                    val shaders       = instanceDir.findFile("shaderpacks")
                        ?.listFiles()?.count { it.isFile } ?: 0
                    val resourcePacks = instanceDir.findFile("resourcepacks")
                        ?.listFiles()?.count { it.isFile } ?: 0
                    val size = folderSizeBytes(instanceDir)

                    results += InstanceStats(
                        folderName     = folderName,
                        displayName    = displayName,
                        modsEnabled    = modsEnabled,
                        modsDisabled   = modsDisabled,
                        shaders        = shaders,
                        resourcePacks  = resourcePacks,
                        totalSizeBytes = size
                    )

                    val sorted = results.sortedBy { it.displayName.lowercase() }
                    _statsList.value  = sorted
                    _totalBytes.value = sorted.sumOf { it.totalSizeBytes }
                }
            }
            _isScanning.value = false
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun folderSizeBytes(dir: DocumentFile): Long {
        var total = 0L
        for (file in dir.listFiles()) {
            total += if (file.isDirectory) folderSizeBytes(file)
            else                  file.length()
        }
        return total
    }
}

// ─── Factory ─────────────────────────────────────────────────────────────────

class StatsViewModelFactory(private val appContext: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return StatsViewModel(appContext) as T
    }
}
package com.example.modrinthforandroid.viewmodel

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.modrinthforandroid.data.InstalledModRepository
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.ui.screens.InstanceStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class StatsViewModel(private val appContext: Context) : ViewModel() {

    private val _isScanning  = MutableStateFlow(false)
    private val _statsList   = MutableStateFlow<List<InstanceStats>>(emptyList())
    private val _totalBytes  = MutableStateFlow(0L)

    val isScanning: StateFlow<Boolean>             = _isScanning.asStateFlow()
    val statsList:  StateFlow<List<InstanceStats>> = _statsList.asStateFlow()
    val totalBytes: StateFlow<Long>                = _totalBytes.asStateFlow()

    private var hasScanned = false
    private val scanMutex  = Mutex()

    private val repo = InstalledModRepository(appContext)

    init {
        scan(force = false)
    }

    fun refresh() {
        scan(force = true)
    }

    private fun scan(force: Boolean) {
        viewModelScope.launch {
            scanMutex.withLock {
                if (_isScanning.value) return@launch
                if (hasScanned && !force) return@launch
                _isScanning.value = true
            }

            withContext(Dispatchers.IO) {
                val rootUri = InstanceManager.rootUri
                if (rootUri == null) {
                    _isScanning.value = false
                    return@withContext
                }

                val rootDoc   = DocumentFile.fromTreeUri(appContext, rootUri)
                val instances = rootDoc?.listFiles()?.filter { it.isDirectory } ?: emptyList()

                // Scan all instances in parallel
                val results = instances.map { instanceDir ->
                    async {
                        val folderName = instanceDir.name ?: return@async null

                        val modFilesDeferred = async {
                            instanceDir.findFile("mods")?.listFiles() ?: emptyArray()
                        }
                        val shadersDeferred = async {
                            instanceDir.findFile("shaderpacks")?.listFiles() ?: emptyArray()
                        }
                        val resourcePacksDeferred = async {
                            instanceDir.findFile("resourcepacks")?.listFiles() ?: emptyArray()
                        }
                        val sizeDeferred = async {
                            folderSizeBytes(instanceDir)
                        }

                        val modFiles      = modFilesDeferred.await()
                        val shaderFiles   = shadersDeferred.await()
                        val rpFiles       = resourcePacksDeferred.await()
                        val size          = sizeDeferred.await()

                        val modsEnabled = modFiles.count { f ->
                            val n = f.name ?: ""
                            n.endsWith(".jar", ignoreCase = true) &&
                                    !n.endsWith(".disabled", ignoreCase = true)
                        }
                        val modsDisabled = modFiles.count { f ->
                            (f.name ?: "").endsWith(".disabled", ignoreCase = true)
                        }

                        // ── Reconcile Room records against actual files on disk ──
                        // Collect all live filenames across mods + shaders + resource packs
                        val liveFileNames = (modFiles.asSequence() + shaderFiles + rpFiles)
                            .mapNotNull { it.name }
                            .toList()
                        try {
                            repo.reconcile(folderName, liveFileNames)
                        } catch (_: Exception) {
                            // Non-critical — don't let reconciliation break the stats scan
                        }

                        InstanceStats(
                            folderName     = folderName,
                            displayName    = folderName,
                            modsEnabled    = modsEnabled,
                            modsDisabled   = modsDisabled,
                            shaders        = shaderFiles.count { it.isFile },
                            resourcePacks  = rpFiles.count { it.isFile },
                            totalSizeBytes = size
                        )
                    }
                }.awaitAll().filterNotNull()
                    .sortedBy { it.displayName.lowercase() }

                _statsList.value  = results
                _totalBytes.value = results.sumOf { it.totalSizeBytes }
            }

            _isScanning.value = false
            hasScanned = true
        }
    }

    private fun folderSizeBytes(dir: DocumentFile): Long {
        var total = 0L
        for (file in dir.listFiles()) {
            total += if (file.isDirectory) folderSizeBytes(file)
            else file.length()
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
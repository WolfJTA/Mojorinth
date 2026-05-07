package com.example.modrinthforandroid.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.modrinthforandroid.data.InstalledModRepository
import com.example.modrinthforandroid.data.ModrinthRepository
import com.example.modrinthforandroid.data.model.ModProject
import com.example.modrinthforandroid.data.model.ModVersion
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ModDetailUiState {
    object Loading : ModDetailUiState()
    data class Success(val project: ModProject, val versions: List<ModVersion> = emptyList()) : ModDetailUiState()
    data class Error(val message: String) : ModDetailUiState()
}

class ModDetailViewModel(
    private val projectId: String,
    private val appContext: Context? = null
) : ViewModel() {

    private val repository = ModrinthRepository()

    private val _uiState = MutableStateFlow<ModDetailUiState>(ModDetailUiState.Loading)
    val uiState: StateFlow<ModDetailUiState> = _uiState.asStateFlow()

    /**
     * List of instance names that have this project installed.
     * Empty list = not installed anywhere (or no context provided).
     */
    private val _installedInInstances = MutableStateFlow<List<String>>(emptyList())
    val installedInInstances: StateFlow<List<String>> = _installedInInstances.asStateFlow()

    init {
        loadProject()
        loadInstallStatus()
    }

    fun loadProject() {
        viewModelScope.launch {
            _uiState.value = ModDetailUiState.Loading
            try {
                val projectDeferred  = async { repository.getModDetails(projectId) }
                val versionsDeferred = async { repository.getProjectVersions(projectId) }
                _uiState.value = ModDetailUiState.Success(
                    projectDeferred.await(),
                    versionsDeferred.await()
                )
            } catch (e: Exception) {
                _uiState.value = ModDetailUiState.Error(e.message ?: "Failed to load mod details.")
            }
        }
    }

    /** Refresh install status — call after a download completes on this screen. */
    fun loadInstallStatus() {
        val ctx = appContext ?: return
        viewModelScope.launch {
            try {
                _installedInInstances.value =
                    InstalledModRepository(ctx).getInstancesForProject(projectId)
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }
}

// ─── Factory ─────────────────────────────────────────────────────────────────

class ModDetailViewModelFactory(
    private val projectId: String,
    private val appContext: Context? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ModDetailViewModel(projectId, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
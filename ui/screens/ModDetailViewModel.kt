package com.example.modrinthforandroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

class ModDetailViewModel(private val projectId: String) : ViewModel() {
    private val repository = ModrinthRepository()
    private val _uiState = MutableStateFlow<ModDetailUiState>(ModDetailUiState.Loading)
    val uiState: StateFlow<ModDetailUiState> = _uiState.asStateFlow()

    init { loadProject() }

    fun loadProject() {
        viewModelScope.launch {
            _uiState.value = ModDetailUiState.Loading
            try {
                val projectDeferred = async { repository.getModDetails(projectId) }
                val versionsDeferred = async { repository.getProjectVersions(projectId) }
                _uiState.value = ModDetailUiState.Success(projectDeferred.await(), versionsDeferred.await())
            } catch (e: Exception) {
                _uiState.value = ModDetailUiState.Error(e.message ?: "Failed to load mod details.")
            }
        }
    }
}

class ModDetailViewModelFactory(private val projectId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return ModDetailViewModel(projectId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
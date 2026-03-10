package com.example.modrinthforandroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modrinthforandroid.data.ModrinthRepository
import com.example.modrinthforandroid.data.model.SearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Kept for BrowseScreen compatibility
sealed class ModListUiState {
    object Loading : ModListUiState()
    data class Success(val mods: List<SearchResult>) : ModListUiState()
    data class Error(val message: String) : ModListUiState()
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val trendingMods: List<SearchResult> = emptyList(),
    val trendingModpacks: List<SearchResult> = emptyList(),
    val trendingShaders: List<SearchResult> = emptyList(),
    val newlyUpdated: List<SearchResult> = emptyList()
)

class HomeViewModel : ViewModel() {

    private val repository = ModrinthRepository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = HomeUiState(isLoading = true)
            try {
                // Fetch all rows in parallel
                val modsDeferred        = async { repository.searchMods(sortIndex = "downloads", projectType = "mod",      limit = 10) }
                val modpacksDeferred    = async { repository.searchMods(sortIndex = "downloads", projectType = "modpack",  limit = 10) }
                val shadersDeferred     = async { repository.searchMods(sortIndex = "downloads", projectType = "shader",   limit = 10) }
                val updatedDeferred     = async { repository.searchMods(sortIndex = "updated",   projectType = "mod",      limit = 10) }

                _uiState.value = HomeUiState(
                    isLoading        = false,
                    trendingMods     = modsDeferred.await(),
                    trendingModpacks = modpacksDeferred.await(),
                    trendingShaders  = shadersDeferred.await(),
                    newlyUpdated     = updatedDeferred.await()
                )
            } catch (e: Exception) {
                _uiState.value = HomeUiState(
                    isLoading = false,
                    error     = e.message ?: "Failed to load. Check your connection."
                )
            }
        }
    }

    fun refresh() = load()
}
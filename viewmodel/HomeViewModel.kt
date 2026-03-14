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
import kotlinx.coroutines.supervisorScope

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
                // supervisorScope ensures a failure in one async child doesn't
                // cancel the others or propagate as an uncaught exception
                supervisorScope {
                    val modsDeferred     = async { repository.searchMods(sortIndex = "downloads", projectType = "mod",     limit = 10) }
                    val modpacksDeferred = async { repository.searchMods(sortIndex = "downloads", projectType = "modpack", limit = 10) }
                    val shadersDeferred  = async { repository.searchMods(sortIndex = "downloads", projectType = "shader",  limit = 10) }
                    val updatedDeferred  = async { repository.searchMods(sortIndex = "updated",   projectType = "mod",     limit = 10) }

                    // Await each individually so one network failure doesn't crash all
                    val mods     = try { modsDeferred.await()     } catch (e: Exception) { emptyList() }
                    val modpacks = try { modpacksDeferred.await() } catch (e: Exception) { emptyList() }
                    val shaders  = try { shadersDeferred.await()  } catch (e: Exception) { emptyList() }
                    val updated  = try { updatedDeferred.await()  } catch (e: Exception) { emptyList() }

                    val anyLoaded = mods.isNotEmpty() || modpacks.isNotEmpty() ||
                            shaders.isNotEmpty() || updated.isNotEmpty()

                    _uiState.value = HomeUiState(
                        isLoading        = false,
                        error            = if (!anyLoaded) "No internet connection." else null,
                        trendingMods     = mods,
                        trendingModpacks = modpacks,
                        trendingShaders  = shaders,
                        newlyUpdated     = updated
                    )
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState(
                    isLoading = false,
                    error     = "No internet connection."
                )
            }
        }
    }

    fun refresh() = load()
}
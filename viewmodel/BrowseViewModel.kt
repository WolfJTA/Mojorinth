package com.example.modrinthforandroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modrinthforandroid.data.ModrinthRepository
import com.example.modrinthforandroid.data.model.SearchResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BrowseFilters(
    val gameVersion: String? = null,
    val loader: String? = null,
    val category: String? = null,
    val sortIndex: String = "relevance"
)

data class BrowseUiState(
    val results: List<SearchResult> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val canLoadMore: Boolean = true,
    val totalHits: Int = 0
)

@OptIn(FlowPreview::class)
class BrowseViewModel(
    val projectType: String,
    initialFilters: BrowseFilters = BrowseFilters()   // ← accepts pre-populated filters
) : ViewModel() {

    private val repository = ModrinthRepository()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filters = MutableStateFlow(initialFilters)
    val filters: StateFlow<BrowseFilters> = _filters.asStateFlow()

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    private var currentOffset = 0
    private val pageSize = 20
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            combine(_query, _filters) { query, filters -> Pair(query, filters) }
                .debounce(400)
                .collectLatest { (query, filters) ->
                    resetAndSearch(query, filters)
                }
        }
    }

    fun onQueryChange(newQuery: String) { _query.value = newQuery }

    fun onFiltersChange(newFilters: BrowseFilters) { _filters.value = newFilters }

    fun loadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.canLoadMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val more = repository.searchMods(
                    query       = _query.value,
                    projectType = projectType,
                    loader      = _filters.value.loader,
                    gameVersion = _filters.value.gameVersion,
                    category    = _filters.value.category,
                    sortIndex   = _filters.value.sortIndex,
                    limit       = pageSize,
                    offset      = currentOffset
                )
                currentOffset += more.size
                _uiState.update {
                    it.copy(
                        results       = it.results + more,
                        isLoadingMore = false,
                        canLoadMore   = more.size == pageSize
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private fun resetAndSearch(query: String, filters: BrowseFilters) {
        searchJob?.cancel()
        currentOffset = 0
        _uiState.value = BrowseUiState(isLoading = true)
        searchJob = viewModelScope.launch {
            try {
                val results = repository.searchMods(
                    query       = query,
                    projectType = projectType,
                    loader      = filters.loader,
                    gameVersion = filters.gameVersion,
                    category    = filters.category,
                    sortIndex   = filters.sortIndex,
                    limit       = pageSize,
                    offset      = 0
                )
                currentOffset = results.size
                _uiState.value = BrowseUiState(
                    results     = results,
                    isLoading   = false,
                    canLoadMore = results.size == pageSize
                )
            } catch (e: Exception) {
                _uiState.value = BrowseUiState(
                    isLoading = false,
                    error     = e.message ?: "Search failed"
                )
            }
        }
    }
}
package com.example.modrinthforandroid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.ui.components.FilterBottomSheet
import com.example.modrinthforandroid.ui.components.ModCard
import com.example.modrinthforandroid.viewmodel.BrowseFilters
import com.example.modrinthforandroid.viewmodel.BrowseViewModel
import com.example.modrinthforandroid.viewmodel.BrowseViewModelFactory

fun projectTypeDisplayName(type: String) = when (type) {
    "mod"          -> "Mods"
    "modpack"      -> "Modpacks"
    "shader"       -> "Shaders"
    "resourcepack" -> "Resource Packs"
    "datapack"     -> "Data Packs"
    "plugin"       -> "Plugins"
    else           -> type.replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    projectType: String,
    onModClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Build initial filters from active instance config (if any)
    val instanceConfig = remember { InstanceManager.activeInstanceConfig }
    val initialFilters = remember(instanceConfig) {
        BrowseFilters(
            gameVersion = instanceConfig?.mcVersion,
            loader      = instanceConfig?.loaderSlug
                ?.takeIf { projectType == "mod" || projectType == "plugin" }
        )
    }

    // ViewModel is keyed on projectType — initial filters are only applied once at creation
    val viewModel: BrowseViewModel = viewModel(
        key     = projectType,
        factory = BrowseViewModelFactory(projectType, initialFilters)
    )

    val uiState by viewModel.uiState.collectAsState()
    val query   by viewModel.query.collectAsState()
    val filters by viewModel.filters.collectAsState()

    var showFilters by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Infinite scroll
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total       = listState.layoutInfo.totalItemsCount
            lastVisible >= total - 5 && total > 0
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    val activeFilterCount = listOf(filters.gameVersion, filters.loader, filters.category)
        .count { it != null } + if (filters.sortIndex != "relevance") 1 else 0

    // Whether current filters match what the instance suggested
    val isInstanceFiltered = instanceConfig != null &&
            filters.gameVersion == instanceConfig.mcVersion &&
            (filters.loader == instanceConfig.loaderSlug || projectType !in listOf("mod", "plugin"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            projectTypeDisplayName(projectType),
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                        // Subtitle showing instance context
                        if (isInstanceFiltered && instanceConfig != null) {
                            Text(
                                "🎮 ${instanceConfig.summary}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (activeFilterCount > 0) Badge { Text("$activeFilterCount") }
                        }
                    ) {
                        IconButton(onClick = { showFilters = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Filters")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SearchBar(query = query, onQueryChange = { viewModel.onQueryChange(it) })

            // Active filter chips — includes instance badge if pre-filtered
            if (activeFilterCount > 0) {
                ActiveFilterChips(
                    filters          = filters,
                    isInstanceFilter = isInstanceFiltered,
                    instanceSummary  = instanceConfig?.summary,
                    onClearFilters   = { viewModel.onFiltersChange(BrowseFilters()) }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color    = MaterialTheme.colorScheme.primary
                    )

                    uiState.error != null -> Column(
                        modifier            = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("😕 ${uiState.error}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.onQueryChange(query) }) { Text("Retry") }
                    }

                    uiState.results.isEmpty() -> Column(
                        modifier            = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🔍 No results found", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Try different search terms or filters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        if (isInstanceFiltered) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(onClick = { viewModel.onFiltersChange(BrowseFilters()) }) {
                                Text("Clear instance filters")
                            }
                        }
                    }

                    else -> LazyColumn(
                        state           = listState,
                        contentPadding  = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.results, key = { it.projectId }) { mod ->
                            ModCard(mod = mod, onClick = { onModClick(mod.projectId) })
                        }
                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier         = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterBottomSheet(
            currentFilters   = filters,
            onFiltersChanged = { viewModel.onFiltersChange(it) },
            onDismiss        = { showFilters = false }
        )
    }
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value         = query,
        onValueChange = onQueryChange,
        modifier      = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder   = { Text("Search...") },
        leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine    = true,
        shape         = MaterialTheme.shapes.large,
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )
    )
}

@Composable
fun ActiveFilterChips(
    filters: BrowseFilters,
    isInstanceFilter: Boolean = false,
    instanceSummary: String? = null,
    onClearFilters: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Show a single "instance" chip instead of separate mc/loader chips
        // when filters match the active instance
        if (isInstanceFilter && instanceSummary != null) {
            AssistChip(
                onClick = {},
                label   = {
                    Text(
                        "🎮 $instanceSummary",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        } else {
            filters.gameVersion?.let {
                AssistChip(onClick = {}, label = { Text(it, style = MaterialTheme.typography.labelSmall) })
            }
            filters.loader?.let {
                AssistChip(onClick = {}, label = { Text(it, style = MaterialTheme.typography.labelSmall) })
            }
        }
        filters.category?.let {
            AssistChip(onClick = {}, label = { Text(it, style = MaterialTheme.typography.labelSmall) })
        }
        if (filters.sortIndex != "relevance") {
            AssistChip(onClick = {}, label = { Text(filters.sortIndex, style = MaterialTheme.typography.labelSmall) })
        }
        TextButton(onClick = onClearFilters) {
            Text("Clear", style = MaterialTheme.typography.labelSmall)
        }
    }
}
package com.example.modrinthforandroid.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.viewmodel.BrowseFilters

val GAME_VERSIONS = listOf(
    "1.21.11", "1.21.10", "1.21.9", "1.21.8", "1.21.7", "1.21.6",
    "1.21.5", "1.21.4", "1.21.3", "1.21.1", "1.21",
    "1.20.6", "1.20.4", "1.20.2", "1.20.1", "1.20",
    "1.19.4", "1.19.2", "1.19.1", "1.19",
    "1.18.2", "1.18.1", "1.18",
    "1.17.1", "1.17",
    "1.16.5", "1.16.4", "1.16.3", "1.16.1",
    "1.15.2", "1.14.4", "1.13.2", "1.12.2", "1.8.9", "1.7.10"
)

val LOADERS = listOf("fabric", "forge", "neoforge", "quilt", "bukkit", "spigot", "paper", "purpur")

val SORT_OPTIONS = listOf(
    "relevance" to "Relevance",
    "downloads" to "Most Downloaded",
    "follows"   to "Most Followed",
    "newest"    to "Newest",
    "updated"   to "Recently Updated"
)

val CATEGORIES = listOf(
    "adventure", "cursed", "decoration", "economy", "equipment",
    "food", "game-mechanics", "library", "magic", "management",
    "minigame", "mobs", "optimization", "social", "storage",
    "technology", "transportation", "utility", "worldgen"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    currentFilters: BrowseFilters,
    onFiltersChanged: (BrowseFilters) -> Unit,
    onDismiss: () -> Unit
) {
    val instanceConfig = remember { InstanceManager.activeInstanceConfig }

    // If current filters have no game version set, pre-fill from instance config
    val defaultVersion = remember(currentFilters, instanceConfig) {
        currentFilters.gameVersion
            ?: instanceConfig?.mcVersion?.takeIf { it in GAME_VERSIONS }
    }
    val defaultLoader = remember(currentFilters, instanceConfig) {
        currentFilters.loader
            ?: instanceConfig?.loaderSlug?.takeIf { it in LOADERS }
    }

    var selectedVersion  by remember { mutableStateOf(defaultVersion) }
    var selectedLoader   by remember { mutableStateOf(defaultLoader) }
    var selectedCategory by remember { mutableStateOf(currentFilters.category) }
    var selectedSort     by remember { mutableStateOf(currentFilters.sortIndex) }

    // Whether current selections match the instance config (for hint display)
    val isInstanceVersion = instanceConfig != null &&
            selectedVersion == instanceConfig.mcVersion
    val isInstanceLoader  = instanceConfig != null &&
            selectedLoader == instanceConfig.loaderSlug

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Filter & Sort",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                // Instance hint badge in header
                if (instanceConfig != null) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            "🎮 ${instanceConfig.mcVersion}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Sort ─────────────────────────────────────────────────────
            FilterSection(title = "Sort By") {
                FilterChipGroup(
                    options    = SORT_OPTIONS.map { it.second },
                    selected   = SORT_OPTIONS.find { it.first == selectedSort }?.second,
                    onSelected = { label ->
                        selectedSort = SORT_OPTIONS.find { it.second == label }?.first ?: "relevance"
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Game version ──────────────────────────────────────────────
            FilterSection(
                title = "Game Version",
                hint  = if (isInstanceVersion) "🎮 From your instance" else null
            ) {
                FilterChipGroup(
                    options    = GAME_VERSIONS,
                    selected   = selectedVersion,
                    onSelected = { selectedVersion = if (selectedVersion == it) null else it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Loader ────────────────────────────────────────────────────
            FilterSection(
                title = "Loader",
                hint  = if (isInstanceLoader) "🎮 From your instance" else null
            ) {
                FilterChipGroup(
                    options    = LOADERS,
                    selected   = selectedLoader,
                    onSelected = { selectedLoader = if (selectedLoader == it) null else it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Category ──────────────────────────────────────────────────
            FilterSection(title = "Category") {
                FilterChipGroup(
                    options    = CATEGORIES,
                    selected   = selectedCategory,
                    onSelected = { selectedCategory = if (selectedCategory == it) null else it }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick  = {
                        selectedVersion  = null
                        selectedLoader   = null
                        selectedCategory = null
                        selectedSort     = "relevance"
                        onFiltersChanged(BrowseFilters())
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Reset") }

                Button(
                    onClick  = {
                        onFiltersChanged(BrowseFilters(
                            gameVersion = selectedVersion,
                            loader      = selectedLoader,
                            category    = selectedCategory,
                            sortIndex   = selectedSort
                        ))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Apply") }
            }
        }
    }
}

// ─── Section header with optional hint ───────────────────────────────────────

@Composable
fun FilterSection(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        if (hint != null) {
            Text(
                text  = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    content()
}

@Composable
fun FilterChipGroup(
    options: List<String>,
    selected: String?,
    onSelected: (String) -> Unit
) {
    val rows = options.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { option ->
                    FilterChip(
                        selected = selected == option,
                        onClick  = { onSelected(option) },
                        label    = { Text(option, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}
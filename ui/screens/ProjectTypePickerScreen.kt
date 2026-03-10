package com.example.modrinthforandroid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Each project type has a display name, an emoji icon, and the API slug
data class ProjectTypeItem(
    val label: String,
    val emoji: String,
    val apiType: String  // what we send to the Modrinth API
)

val PROJECT_TYPES = listOf(
    ProjectTypeItem("Mods", "⚙️", "mod"),
    ProjectTypeItem("Modpacks", "📦", "modpack"),
    ProjectTypeItem("Shaders", "✨", "shader"),
    ProjectTypeItem("Resource Packs", "🎨", "resourcepack"),
    ProjectTypeItem("Data Packs", "📋", "datapack"),
    ProjectTypeItem("Plugins", "🔌", "plugin"),
    ProjectTypeItem("Servers", "🖥️", "modpack"), // Modrinth uses modpack type for server packs
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectTypePickerScreen(
    onTypeSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "What are you looking for?",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = 16.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(PROJECT_TYPES) { type ->
                ProjectTypeCard(
                    item = type,
                    onClick = { onTypeSelected(type.apiType) }
                )
            }
        }
    }
}

@Composable
fun ProjectTypeCard(
    item: ProjectTypeItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.1f) // Slightly taller than wide
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.emoji,
                fontSize = 40.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
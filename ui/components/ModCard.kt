package com.example.modrinthforandroid.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.data.model.SearchResult

@Composable
fun ModCard(
    mod: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Read active instance config once — null means no instance selected
    val instanceConfig = remember { InstanceManager.activeInstanceConfig }

    // Determine compatibility only when an instance is active
    val compatibility: CompatibilityState = remember(mod, instanceConfig) {
        when {
            instanceConfig == null -> CompatibilityState.Unknown

            else -> {
                val mcMatch = mod.versions.any { it == instanceConfig.mcVersion }

                // For mod/plugin types check loader too; shaders/resource packs don't have loaders
                val loaderRelevant = mod.projectType in listOf("mod", "plugin")
                val loaderMatch    = !loaderRelevant ||
                        mod.categories.any { it.equals(instanceConfig.loaderSlug, ignoreCase = true) }

                when {
                    mcMatch && loaderMatch -> CompatibilityState.Compatible
                    mcMatch               -> CompatibilityState.WrongLoader(instanceConfig.loader)
                    else                  -> CompatibilityState.Incompatible(instanceConfig.mcVersion)
                }
            }
        }
    }

    Card(
        onClick   = onClick,
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier          = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model              = mod.iconUrl,
                contentDescription = "${mod.title} icon",
                modifier           = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title row + compatibility badge
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text     = mod.title,
                        style    = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(Modifier.width(6.dp))

                    CompatibilityBadge(compatibility)
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text     = mod.description,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text  = "⬇ ${formatNumber(mod.downloads)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    mod.categories.firstOrNull()?.let { category ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text     = category,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Incompatibility detail line — shown below the main row
                if (compatibility is CompatibilityState.Incompatible ||
                    compatibility is CompatibilityState.WrongLoader) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = when (compatibility) {
                            is CompatibilityState.Incompatible ->
                                "No version for MC ${compatibility.requiredMc}"
                            is CompatibilityState.WrongLoader  ->
                                "No ${compatibility.requiredLoader} version available"
                            else -> ""
                        },
                        style    = MaterialTheme.typography.labelSmall,
                        color    = Color(0xFFEF5350).copy(alpha = 0.85f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// ─── Compatibility state ──────────────────────────────────────────────────────

sealed class CompatibilityState {
    object Unknown     : CompatibilityState()   // no instance selected
    object Compatible  : CompatibilityState()   // MC version + loader both match
    data class WrongLoader(val requiredLoader: String) : CompatibilityState()  // MC ok, loader mismatch
    data class Incompatible(val requiredMc: String)    : CompatibilityState()  // MC version not found
}

// ─── Badge composable ─────────────────────────────────────────────────────────

@Composable
fun CompatibilityBadge(state: CompatibilityState) {
    when (state) {
        is CompatibilityState.Unknown -> Unit  // show nothing when no instance active

        is CompatibilityState.Compatible -> Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF1BD96A).copy(alpha = 0.15f)
        ) {
            Row(
                modifier          = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Compatible",
                    modifier = Modifier.size(10.dp),
                    tint     = Color(0xFF1BD96A)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "Compatible",
                    style      = MaterialTheme.typography.labelSmall,
                    color      = Color(0xFF1BD96A),
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 10.sp
                )
            }
        }

        is CompatibilityState.WrongLoader -> Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFFFFA726).copy(alpha = 0.15f)
        ) {
            Row(
                modifier          = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚠", fontSize = 9.sp, color = Color(0xFFFFA726))
                Spacer(Modifier.width(3.dp))
                Text(
                    "Wrong loader",
                    style      = MaterialTheme.typography.labelSmall,
                    color      = Color(0xFFFFA726),
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 10.sp
                )
            }
        }

        is CompatibilityState.Incompatible -> Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFFEF5350).copy(alpha = 0.15f)
        ) {
            Row(
                modifier          = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Incompatible",
                    modifier = Modifier.size(10.dp),
                    tint     = Color(0xFFEF5350)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "No match",
                    style      = MaterialTheme.typography.labelSmall,
                    color      = Color(0xFFEF5350),
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 10.sp
                )
            }
        }
    }
}

// ─── Number formatter (shared) ────────────────────────────────────────────────

fun formatNumber(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000     -> "%.1fK".format(n / 1_000.0)
    else           -> n.toString()
}
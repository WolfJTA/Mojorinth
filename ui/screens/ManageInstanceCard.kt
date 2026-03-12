package com.example.modrinthforandroid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modrinthforandroid.data.InstanceManager

/**
 * A compact action card shown on the Home screen when an instance is active.
 * Tapping it navigates to [InstanceManagerScreen].
 *
 * Drop this into HomeContent's LazyColumn as an `item {}` directly beneath
 * the [InstancePickerCard] item.
 *
 * Example placement in HomeContent:
 *
 *     item { InstancePickerCard() }
 *     item { ManageInstanceCard(onManageClick = onManageInstance) }
 */
@Composable
fun ManageInstanceCard(onManageClick: () -> Unit) {
    val context      = LocalContext.current
    val instanceName = InstanceManager.activeInstanceName
    val config       = InstanceManager.activeInstanceConfig

    // Only render when an instance is actually selected
    AnimatedVisibility(
        visible = instanceName != null,
        enter   = fadeIn() + expandVertically(),
        exit    = fadeOut() + shrinkVertically()
    ) {
        Surface(
            onClick        = onManageClick,
            modifier       = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape          = RoundedCornerShape(14.dp),
            color          = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon container
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp)
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Manage Instance",
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                    val subtitle = config?.name?.takeIf { it.isNotBlank() }
                        ?: instanceName
                        ?: ""
                    Text(
                        "Mods • Shaders • Resource Packs — $subtitle",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1
                    )
                }

                // Chevron hint
                Text(
                    "›",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            }
        }
    }
}
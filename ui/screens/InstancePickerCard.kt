// ─── InstancePickerCard ───────────────────────────────────────────────────────
// Drop-in replacement for the InstancePickerCard composable in HomeScreen.kt.
//
// Three exclusive states:
//   UNSET  — no folder linked yet  → numbered guide + "Link Folder" button
//   WRONG  — wrong folder picked   → red error, indented path tree, "Try Again"
//   LOCKED — correct folder found  → padlock row, instance list, no change btn

package com.example.modrinthforandroid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modrinthforandroid.data.InstanceManager

private enum class FolderState { UNSET, WRONG, LOCKED }

@Composable
fun InstancePickerCard() {
    val context      = LocalContext.current
    val focusManager = LocalFocusManager.current

    var folderState    by remember {
        mutableStateOf(
            if (InstanceManager.rootValidated) FolderState.LOCKED else FolderState.UNSET
        )
    }
    var activeName     by remember { mutableStateOf(InstanceManager.activeInstanceName) }
    var rootDisplay    by remember { mutableStateOf(InstanceManager.rootDisplayPath) }
    var instances      by remember { mutableStateOf(InstanceManager.listInstances(context)) }
    var instanceConfig by remember { mutableStateOf(InstanceManager.activeInstanceConfig) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (InstanceManager.isCorrectFolder(uri)) {
            InstanceManager.setRootFromUri(context, uri)
            rootDisplay    = InstanceManager.rootDisplayPath
            instances      = InstanceManager.listInstances(context)
            activeName     = null
            instanceConfig = null
            folderState    = FolderState.LOCKED
        } else {
            folderState = FolderState.WRONG
        }
    }

    val green = MaterialTheme.colorScheme.primary
    val red   = MaterialTheme.colorScheme.error

    Surface(
        modifier       = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape          = RoundedCornerShape(16.dp),
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Header ────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when (folderState) {
                                FolderState.LOCKED -> green.copy(alpha = 0.15f)
                                FolderState.WRONG  -> red.copy(alpha = 0.12f)
                                FolderState.UNSET  -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (folderState) {
                            FolderState.LOCKED -> "🎮"
                            FolderState.WRONG  -> "⚠️"
                            FolderState.UNSET  -> "📁"
                        },
                        fontSize = 18.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = when (folderState) {
                            FolderState.LOCKED -> "Active Instance"
                            FolderState.WRONG  -> "Wrong Folder"
                            FolderState.UNSET  -> "Link MojoLauncher Folder"
                        },
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = if (folderState == FolderState.WRONG) red
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (folderState) {
                            FolderState.LOCKED -> if (activeName != null)
                                "Downloads → …/$activeName"
                            else
                                "Folder linked ✓ — pick an instance below"
                            FolderState.WRONG  -> "That's not the right folder"
                            FolderState.UNSET  -> "Required to browse your instances"
                        },
                        style    = MaterialTheme.typography.labelSmall,
                        color    = if (folderState == FolderState.WRONG) red.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ═══════════════════════════════════════════════════════════════
            // UNSET — numbered guide + link button
            // ═══════════════════════════════════════════════════════════════
            AnimatedVisibility(visible = folderState == FolderState.UNSET) {
                Column {
                    Surface(
                        shape    = RoundedCornerShape(10.dp),
                        color    = green.copy(alpha = 0.07f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Follow these steps exactly:",
                                style      = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color      = green
                            )
                            Spacer(Modifier.height(10.dp))
                            listOf(
                                "Tap \"Link Folder\" below",
                                "Tap the  ☰  hamburger menu (top-left)",
                                "Tap  MojoLauncher  in the sidebar",
                                "Tap  instances",
                                "Tap  \"Use this folder\"  at the bottom"
                            ).forEachIndexed { i, text ->
                                Row(
                                    modifier          = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(green.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${i + 1}",
                                            fontSize   = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color      = green
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text       = text,
                                        style      = MaterialTheme.typography.labelSmall,
                                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            // Exact path callout box
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                Row(
                                    modifier          = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.List, null,
                                        modifier = Modifier.size(13.dp),
                                        tint     = green.copy(alpha = 0.8f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Android/data/git.artdeell.mojo/files/instances",
                                        style      = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = green
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick  = { folderPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = green)
                    ) {
                        Icon(Icons.Default.List, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Link Folder", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // WRONG — red error, indented path tree, try again
            // ═══════════════════════════════════════════════════════════════
            AnimatedVisibility(visible = folderState == FolderState.WRONG) {
                Column {
                    Surface(
                        shape    = RoundedCornerShape(10.dp),
                        color    = red.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "❌  That's not the right folder",
                                style      = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color      = red
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "You need to navigate all the way inside to the " +
                                        "instances folder itself — not a parent folder:",
                                style      = MaterialTheme.typography.bodySmall,
                                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                lineHeight = 18.sp
                            )
                            Spacer(Modifier.height(10.dp))

                            // Indented folder tree so the path is impossible to misread
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    data class PathNode(val name: String, val depth: Int, val isTarget: Boolean = false)
                                    listOf(
                                        PathNode("Android",            0),
                                        PathNode("data",               1),
                                        PathNode("git.artdeell.mojo",  2),
                                        PathNode("files",              3),
                                        PathNode("instances",          4, isTarget = true)
                                    ).forEachIndexed { i, node ->
                                        Row(
                                            modifier          = Modifier.padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Spacer(Modifier.width((node.depth * 14).dp))
                                            Text(
                                                if (node.depth == 0) "📂 " else "└ 📂 ",
                                                fontSize = 11.sp,
                                                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                            Text(
                                                text      = if (node.isTarget) "${node.name}  ← select this"
                                                else node.name,
                                                style     = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (node.isTarget) FontWeight.Bold else FontWeight.Normal,
                                                color     = if (node.isTarget) green
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Tip: tap the ☰ hamburger menu (top-left) in the picker, " +
                                        "select MojoLauncher, then tap instances.",
                                style      = MaterialTheme.typography.labelSmall,
                                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick  = { folderPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = red)
                    ) {
                        Text(
                            "↩  Try Again",
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // LOCKED — padlock + path (read-only), instance list
            // ═══════════════════════════════════════════════════════════════
            AnimatedVisibility(visible = folderState == FolderState.LOCKED) {
                Column {

                    // Active instance chip
                    AnimatedVisibility(visible = activeName != null) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(green.copy(alpha = 0.12f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📁", fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    activeName ?: "",
                                    style      = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = green,
                                    modifier   = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick  = {
                                        InstanceManager.clearActiveInstance(context)
                                        activeName     = null
                                        instanceConfig = null
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close, "Clear instance",
                                        tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Config badges
                            val config = instanceConfig
                            if (config != null) {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    Row(
                                        modifier              = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        InstanceBadge(
                                            icon  = "⚙️",
                                            label = "Loader",
                                            value = if (config.loaderVersion.isNotEmpty())
                                                "${config.loader} ${config.loaderVersion}"
                                            else config.loader,
                                            color = when (config.loader) {
                                                "Fabric"   -> Color(0xFFDBB155)
                                                "Forge"    -> Color(0xFF8B5E3C)
                                                "NeoForge" -> Color(0xFFE87B2B)
                                                "Quilt"    -> Color(0xFF9B59B6)
                                                else       -> green
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        InstanceBadge(
                                            icon     = "🟩",
                                            label    = "MC Version",
                                            value    = config.mcVersion,
                                            color    = Color(0xFF5DA85D),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    InstanceBadge(
                                        icon     = "🖥️",
                                        label    = "Renderer",
                                        value    = config.rendererDisplay,
                                        color    = Color(0xFF4A90D9),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // mojo_instance.json unreadable warning
                            if (activeName != null && instanceConfig == null) {
                                Row(
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⚠️", fontSize = 14.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Could not read mojo_instance.json",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                        }
                    }

                    // Locked path row — padlock icon, NO change button
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Folder locked",
                            modifier = Modifier.size(13.dp),
                            tint     = green.copy(alpha = 0.65f)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = rootDisplay
                                ?.split("/")?.takeLast(4)?.joinToString("/")
                                ?: "git.artdeell.mojo/files/instances",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = green.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = green.copy(alpha = 0.12f)
                        ) {
                            Text(
                                "✓ Linked",
                                modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color      = green
                            )
                        }
                    }

                    // Instance list
                    if (instances.isNotEmpty()) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Text(
                                "${instances.size} instance${if (instances.size != 1) "s" else ""} found:",
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Surface(
                                shape    = RoundedCornerShape(10.dp),
                                color    = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                            ) {
                                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                    items(instances) { name ->
                                        val isActive = name == activeName
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isActive) green.copy(alpha = 0.08f)
                                                    else Color.Transparent
                                                )
                                                .clickable {
                                                    InstanceManager.setActiveInstance(context, name)
                                                    activeName     = InstanceManager.activeInstanceName
                                                    instanceConfig = InstanceManager.activeInstanceConfig
                                                    focusManager.clearFocus()
                                                }
                                                .padding(horizontal = 12.dp, vertical = 11.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(if (isActive) "🎮" else "📁", fontSize = 14.sp)
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    name,
                                                    style      = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                    color      = if (isActive) green
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (isActive && instanceConfig != null) {
                                                    Text(
                                                        instanceConfig!!.summary,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = green.copy(alpha = 0.75f)
                                                    )
                                                }
                                            }
                                            if (isActive) {
                                                Icon(
                                                    Icons.Default.Check, null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint     = green
                                                )
                                            }
                                        }
                                        if (name != instances.last()) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            modifier   = Modifier.padding(top = 10.dp),
                            text       = "No instances found. Create one in MojoLauncher first.",
                            style      = MaterialTheme.typography.labelSmall,
                            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// ─── Instance badge chip ──────────────────────────────────────────────────────

@Composable
private fun InstanceBadge(
    icon: String,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape    = RoundedCornerShape(8.dp),
        color    = color.copy(alpha = 0.10f),
        modifier = modifier
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    label,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = color.copy(alpha = 0.7f),
                    fontSize = 9.sp
                )
                Text(
                    value,
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = color,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
            }
        }
    }
}
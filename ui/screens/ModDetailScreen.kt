package com.example.modrinthforandroid.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.*
import coil.compose.AsyncImage
import com.example.modrinthforandroid.data.AppSettings
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.data.model.ModProject
import com.example.modrinthforandroid.data.model.ModVersion
import com.example.modrinthforandroid.ui.components.formatNumber
import com.example.modrinthforandroid.viewmodel.ModDetailUiState
import com.example.modrinthforandroid.viewmodel.ModDetailViewModel
import com.example.modrinthforandroid.viewmodel.ModDetailViewModelFactory
import com.example.modrinthforandroid.work.DownloadWorker
import com.example.modrinthforandroid.work.KEY_DOWNLOAD_ID
import com.example.modrinthforandroid.work.KEY_FILENAME
import com.example.modrinthforandroid.work.KEY_FINAL_DIR
import com.example.modrinthforandroid.work.KEY_NOTIF_ID
import com.example.modrinthforandroid.work.KEY_TEMP_PATH
import com.example.modrinthforandroid.work.KEY_TITLE
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─── Download helper ──────────────────────────────────────────────────────────

fun triggerDownload(
    context: Context,
    url: String,
    filename: String,
    title: String,
    projectType: String
) {
    // DownloadManager only accepts paths inside the public Downloads folder.
    // Writing directly to an arbitrary path throws SecurityException on modern Android.
    //
    // Strategy:
    //   1. Download into Downloads/ via DownloadManager (safe, always works)
    //   2. DownloadWorker waits for completion then moves the file to the real
    //      instance folder using plain File I/O (works with MANAGE_EXTERNAL_STORAGE)
    //      and deletes the temp copy from Downloads.

    val downloadsDir = android.os.Environment
        .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        .also { it.mkdirs() }

    // The real destination e.g. /storage/emulated/0/Mojolauncher/test1/mods/
    val finalDir = InstanceManager.resolveDownloadFolder(context, projectType)

    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(title)
        .setDescription("Downloading via Mojorinth…")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        .setDestinationInExternalPublicDir(
            android.os.Environment.DIRECTORY_DOWNLOADS, filename
        )
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(false)

    val dm         = context.getSystemService(DownloadManager::class.java)
    val downloadId = dm.enqueue(request)
    val notifId    = downloadId.toInt()

    val work = OneTimeWorkRequestBuilder<DownloadWorker>()
        .setInputData(workDataOf(
            KEY_DOWNLOAD_ID to downloadId,
            KEY_FILENAME    to filename,
            KEY_TITLE       to title,
            KEY_NOTIF_ID    to notifId,
            KEY_FINAL_DIR   to finalDir,
            KEY_TEMP_PATH   to File(downloadsDir, filename).absolutePath
        ))
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .build()

    WorkManager.getInstance(context).enqueue(work)
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModDetailScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: ModDetailViewModel = viewModel(factory = ModDetailViewModelFactory(projectId))
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (uiState as? ModDetailUiState.Success)?.project?.title ?: ""
                    Text(text = title, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val state = uiState) {
                is ModDetailUiState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary)
                is ModDetailUiState.Error ->
                    Column(modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("😕 Failed to load", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.message, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadProject() }) { Text("Retry") }
                    }
                is ModDetailUiState.Success ->
                    ModDetailContent(
                        project  = state.project,
                        versions = state.versions,
                        context  = context
                    )
            }
        }
    }
}

// ─── Content + tabs ───────────────────────────────────────────────────────────

@Composable
private fun ModDetailContent(project: ModProject, versions: List<ModVersion>, context: Context) {
    var selectedTab      by remember { mutableIntStateOf(0) }
    // Tracks which version IDs have been downloaded this session (can download again with warning)
    var downloadedIds    by remember { mutableStateOf(setOf<String>()) }
    var showDownloadSheet by remember { mutableStateOf(false) }

    val tabs = buildList {
        add("Description")
        if (!project.gallery.isNullOrEmpty()) add("Gallery")
        add("Versions")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ModDetailHeader(
            project         = project,
            versions        = versions,
            downloadedIds   = downloadedIds,
            onDownloadClick = { showDownloadSheet = true }
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor   = MaterialTheme.colorScheme.background,
            contentColor     = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size)
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color    = MaterialTheme.colorScheme.primary)
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick  = { selectedTab = index },
                    text = {
                        Text(title, style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal)
                    }
                )
            }
        }

        when (tabs.getOrNull(selectedTab)) {
            "Description" -> DescriptionTab(project = project)
            "Gallery"     -> GalleryTab(images = project.gallery?.map { it.url } ?: emptyList())
            "Versions"    -> VersionsTab(
                versions      = versions,
                projectTitle  = project.title,
                projectType   = project.projectType,
                downloadedIds = downloadedIds,
                context       = context,
                onDownloaded  = { id -> downloadedIds = downloadedIds + id }
            )
        }
    }

    if (showDownloadSheet) {
        DownloadPickerSheet(
            project       = project,
            versions      = versions,
            downloadedIds = downloadedIds,
            context       = context,
            onDownloaded  = { id -> downloadedIds = downloadedIds + id },
            onDismiss     = { showDownloadSheet = false }
        )
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun ModDetailHeader(
    project: ModProject,
    versions: List<ModVersion>,
    downloadedIds: Set<String>,
    onDownloadClick: () -> Unit
) {
    val latestVersion   = versions.firstOrNull { it.versionType == "release" } ?: versions.firstOrNull()
    val isAnyDownloaded = versions.any { downloadedIds.contains(it.id) }

    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = project.iconUrl,
                    contentDescription = "${project.title} icon",
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(project.title, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                        Text(project.projectType.replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(project.description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), lineHeight = 20.sp)
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatChip(Icons.Default.KeyboardArrowDown, "Downloads", formatNumber(project.downloads))
                StatChip(Icons.Default.Favorite,          "Followers", formatNumber(project.followers))
                StatChip(Icons.Default.Build,             "Versions",  (project.versions?.size ?: 0).toString())
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick  = onDownloadClick,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null,
                    modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Download", fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary)
                    if (latestVersion != null) {
                        val mcVer = latestVersion.gameVersions
                            .filter { it.matches(Regex("""\d+\.\d+(\.\d+)?""")) }
                            .maxByOrNull { it } ?: ""
                        Text(
                            text  = "${latestVersion.versionNumber}  •  $mcVer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val loaders = project.loaders ?: emptyList()
            if (loaders.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(loaders) { LoaderChip(it) }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            if (project.categories.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(project.categories) { CategoryChip(it) }
                }
            }
        }
    }
}

// ─── Download Picker Sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadPickerSheet(
    project: ModProject,
    versions: List<ModVersion>,
    downloadedIds: Set<String>,
    context: Context,
    onDownloaded: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val settings = remember { AppSettings.get(context) }

    val allMcVersions = remember(versions) {
        versions.flatMap { it.gameVersions }
            .filter { it.matches(Regex("""\d+\.\d+(\.\d+)?""")) }
            .distinct()
            .sortedWith(compareByDescending { versionKey(it) })
    }
    val allLoaders = remember(versions) {
        versions.flatMap { it.loaders }.distinct().sorted()
    }

    var selectedMc     by remember { mutableStateOf<String?>(null) }
    var selectedLoader by remember { mutableStateOf<String?>(null) }

    // Version to show a duplicate warning for before downloading
    var pendingDownloadVersion by remember { mutableStateOf<ModVersion?>(null) }

    val filteredVersions = remember(versions, selectedMc, selectedLoader) {
        versions.filter { v ->
            val mcOk     = selectedMc     == null || v.gameVersions.contains(selectedMc)
            val loaderOk = selectedLoader == null || v.loaders.contains(selectedLoader)
            mcOk && loaderOk
        }
    }

    // Duplicate-download warning dialog
    pendingDownloadVersion?.let { version ->
        AlertDialog(
            onDismissRequest = { pendingDownloadVersion = null },
            title   = { Text("Already downloaded") },
            text    = {
                Column {
                    Text("You've already downloaded ${version.name} this session. Download another copy?")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var warnPref by remember { mutableStateOf(settings.showDuplicateWarning) }
                        Checkbox(
                            checked         = !warnPref,
                            onCheckedChange = { dontWarn ->
                                warnPref = !dontWarn
                                settings.showDuplicateWarning = !dontWarn
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Don't warn me again",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val file = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull()
                    if (file != null) {
                        triggerDownload(context, file.url, file.filename,
                            "${project.title} ${version.name}", project.projectType)
                        onDownloaded(version.id)
                    }
                    pendingDownloadVersion = null
                    onDismiss()
                }) { Text("Download again") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDownloadVersion = null }) { Text("Cancel") }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            Text("Download ${project.title}", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(16.dp))

            Text("Minecraft Version", style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = selectedMc == null, onClick = { selectedMc = null },
                        label = { Text("Any") })
                }
                items(allMcVersions) { ver ->
                    FilterChip(
                        selected = selectedMc == ver,
                        onClick  = { selectedMc = if (selectedMc == ver) null else ver },
                        label    = { Text(ver) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (allLoaders.isNotEmpty()) {
                Text("Loader", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(selected = selectedLoader == null,
                            onClick = { selectedLoader = null }, label = { Text("Any") })
                    }
                    items(allLoaders) { loader ->
                        FilterChip(
                            selected = selectedLoader == loader,
                            onClick  = { selectedLoader = if (selectedLoader == loader) null else loader },
                            label    = { Text(loader.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            Text(
                text  = "${filteredVersions.size} version${if (filteredVersions.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (filteredVersions.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center) {
                            Text("No versions match your filters",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                } else {
                    itemsIndexed(filteredVersions) { index, version ->
                        PickerVersionCard(
                            version      = version,
                            isLatest     = index == 0,
                            isDownloaded = downloadedIds.contains(version.id),
                            onDownload   = {
                                val alreadyDownloaded = downloadedIds.contains(version.id)
                                if (alreadyDownloaded && settings.showDuplicateWarning) {
                                    pendingDownloadVersion = version
                                } else {
                                    val file = version.files.firstOrNull { it.primary }
                                        ?: version.files.firstOrNull()
                                    if (file != null) {
                                        triggerDownload(context, file.url, file.filename,
                                            "${project.title} ${version.name}", project.projectType)
                                        onDownloaded(version.id)
                                    }
                                    onDismiss()
                                }
                            }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun PickerVersionCard(
    version: ModVersion,
    isLatest: Boolean,
    isDownloaded: Boolean,
    onDownload: () -> Unit
) {
    val typeColor = when (version.versionType) {
        "release" -> MaterialTheme.colorScheme.primary
        "beta"    -> Color(0xFFFFA726)
        "alpha"   -> Color(0xFFEF5350)
        else      -> MaterialTheme.colorScheme.onSurface
    }
    val mcVersions = version.gameVersions
        .filter { it.matches(Regex("""\d+\.\d+(\.\d+)?""")) }
        .sortedWith(compareByDescending { versionKey(it) })

    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLatest) 3.dp else 1.dp,
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(version.name, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        Surface(shape = RoundedCornerShape(4.dp), color = typeColor.copy(alpha = 0.15f)) {
                            Text(version.versionType.replaceFirstChar { it.uppercase() },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = typeColor, fontWeight = FontWeight.Bold)
                        }
                        if (isLatest) {
                            Surface(shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                                Text("Latest",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        mcVersions.take(3).forEach { gv ->
                            Surface(shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                                Text(gv, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (mcVersions.size > 3) {
                            Text("+${mcVersions.size - 3}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.align(Alignment.CenterVertically))
                        }
                        version.loaders.take(2).forEach { loader ->
                            Surface(shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)) {
                                Text(loader, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${formatDate(version.datePublished)}  •  ⬇ ${formatNumber(version.downloads)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onDownload,
                    shape   = RoundedCornerShape(8.dp),
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = if (isDownloaded)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (isDownloaded) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Again", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Get", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Expandable changelog ───────────────────────────────────────
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // All MC versions when expanded
                    if (mcVersions.size > 3) {
                        Text("All MC versions:", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(4.dp))
                        // Simple wrap layout using chunked rows
                        mcVersions.chunked(5).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(bottom = 4.dp)) {
                                row.forEach { gv ->
                                    Surface(shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                                        Text(gv, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text("Changelog", style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text  = if (version.changelog.isNullOrBlank()) "No changelog provided."
                        else version.changelog.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        lineHeight = 18.sp
                    )
                }
            }

            // Tap hint
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Show changelog",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

// ─── Versions Tab (full list) ─────────────────────────────────────────────────

@Composable
private fun VersionsTab(
    versions: List<ModVersion>,
    projectTitle: String,
    projectType: String,
    downloadedIds: Set<String>,
    context: Context,
    onDownloaded: (String) -> Unit
) {
    val settings = remember { AppSettings.get(context) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var pendingDownloadVersion by remember { mutableStateOf<ModVersion?>(null) }

    // Duplicate-download warning dialog for the Versions tab
    pendingDownloadVersion?.let { version ->
        AlertDialog(
            onDismissRequest = { pendingDownloadVersion = null },
            title   = { Text("Already downloaded") },
            text    = {
                Column {
                    Text("You've already downloaded ${version.name} this session. Download another copy?")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var warnPref by remember { mutableStateOf(settings.showDuplicateWarning) }
                        Checkbox(
                            checked         = !warnPref,
                            onCheckedChange = { dontWarn ->
                                warnPref = !dontWarn
                                settings.showDuplicateWarning = !dontWarn
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Don't warn me again",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val file = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull()
                    if (file != null) {
                        triggerDownload(context, file.url, file.filename,
                            "$projectTitle - ${version.name}", projectType)
                        onDownloaded(version.id)
                        snackbarMessage = "Downloading ${file.filename}"
                    }
                    pendingDownloadVersion = null
                }) { Text("Download again") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDownloadVersion = null }) { Text("Cancel") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (versions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No versions available", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("${versions.size} version${if (versions.size != 1) "s" else ""} available",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                itemsIndexed(versions) { index, version ->
                    VersionCard(
                        version      = version,
                        isLatest     = index == 0,
                        isDownloaded = downloadedIds.contains(version.id),
                        onDownload   = {
                            val alreadyDownloaded = downloadedIds.contains(version.id)
                            if (alreadyDownloaded && settings.showDuplicateWarning) {
                                pendingDownloadVersion = version
                            } else {
                                val file = version.files.firstOrNull { it.primary }
                                    ?: version.files.firstOrNull()
                                if (file != null) {
                                    triggerDownload(context, file.url, file.filename,
                                        "$projectTitle - ${version.name}", projectType)
                                    onDownloaded(version.id)
                                    snackbarMessage = "Downloading ${file.filename}"
                                }
                            }
                        }
                    )
                }
            }
        }

        snackbarMessage?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action   = {
                    TextButton(onClick = { snackbarMessage = null }) {
                        Text("OK", color = MaterialTheme.colorScheme.primary)
                    }
                }
            ) { Text(msg, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun VersionCard(
    version: ModVersion,
    isLatest: Boolean,
    isDownloaded: Boolean,
    onDownload: () -> Unit
) {
    val typeColor = when (version.versionType) {
        "release" -> MaterialTheme.colorScheme.primary
        "beta"    -> Color(0xFFFFA726)
        "alpha"   -> Color(0xFFEF5350)
        else      -> MaterialTheme.colorScheme.onSurface
    }
    val mcVersions = version.gameVersions
        .filter { it.matches(Regex("""\d+\.\d+(\.\d+)?""")) }
        .sortedWith(compareByDescending { versionKey(it) })

    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLatest) 3.dp else 1.dp,
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // ── Header row ────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(version.name, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(version.versionNumber, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(4.dp), color = typeColor.copy(alpha = 0.15f)) {
                        Text(version.versionType.replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = typeColor, fontWeight = FontWeight.Bold)
                    }
                    if (isLatest) {
                        Surface(shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                            Text("Latest",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (mcVersions.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(mcVersions.take(5)) { gv ->
                        Surface(shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                            Text(gv, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (mcVersions.size > 5) {
                        item {
                            Surface(shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)) {
                                Text("+${mcVersions.size - 5} more",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (version.loaders.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    version.loaders.take(3).forEach { loader ->
                        Surface(shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)) {
                            Text(loader, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(formatDate(version.datePublished), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text("⬇ ${formatNumber(version.downloads)}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Button(
                    onClick = onDownload,
                    shape   = RoundedCornerShape(8.dp),
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = if (isDownloaded)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    if (isDownloaded) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download again", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // ── Expandable changelog ───────────────────────────────────────
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(10.dp))

                    if (mcVersions.size > 5) {
                        Text("All MC versions:", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(4.dp))
                        mcVersions.chunked(5).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(bottom = 4.dp)) {
                                row.forEach { gv ->
                                    Surface(shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                                        Text(gv, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text("Changelog", style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text  = if (version.changelog.isNullOrBlank()) "No changelog provided."
                        else version.changelog.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        lineHeight = 18.sp
                    )
                }
            }

            // Expand/collapse chevron
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Show changelog",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

// ─── Description Tab ──────────────────────────────────────────────────────────

@Composable
private fun DescriptionTab(project: ModProject) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (project.gameVersions.isNotEmpty()) {
            InfoSection("Supported Versions") {
                val releaseVersions = project.gameVersions
                    .filter { it.matches(Regex("""\d+\.\d+(\.\d+)?""")) }
                    .sortedWith(compareByDescending { versionKey(it) })
                    .take(8)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(releaseVersions) { version ->
                        Surface(shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                            Text(version, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        InfoSection("Project Info") {
            InfoRow("Created",      formatDate(project.dateCreated))
            InfoRow("Last Updated", formatDate(project.dateModified))
            project.license?.let { InfoRow("License", it.name) }
        }
        project.license?.url?.let { licenseUrl ->
            OutlinedButton(onClick = { uriHandler.openUri(licenseUrl) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("View License", style = MaterialTheme.typography.labelMedium)
            }
        }
        Button(
            onClick = { uriHandler.openUri("https://modrinth.com/${project.projectType}/${project.slug}") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("View on Modrinth", color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(10.dp))
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), content = content)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

// ─── Gallery Tab ──────────────────────────────────────────────────────────────

@Composable
private fun GalleryTab(images: List<String>) {
    var selectedImage by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        images.forEach { imageUrl ->
            Card(onClick = { selectedImage = imageUrl }, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                AsyncImage(model = imageUrl, contentDescription = "Gallery image",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                    contentScale = ContentScale.Crop)
            }
        }
    }
    if (selectedImage != null)
        FullscreenImageViewer(imageUrl = selectedImage!!, onDismiss = { selectedImage = null })
}

@Composable
private fun FullscreenImageViewer(imageUrl: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(model = imageUrl, contentDescription = "Full size image",
            modifier = Modifier.fillMaxWidth().padding(16.dp), contentScale = ContentScale.Fit)
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                .background(Color.White.copy(alpha = 0.15f), CircleShape)
        ) {
            Icon(Icons.Default.Close, "Close", tint = Color.White)
        }
    }
}

// ─── Chips ────────────────────────────────────────────────────────────────────

@Composable
private fun StatChip(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, null, modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text(value, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
private fun LoaderChip(label: String) {
    Surface(
        shape  = RoundedCornerShape(20.dp),
        color  = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CategoryChip(label: String) {
    Surface(shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Convert a semver-ish MC version string like "1.20.4" into an integer key
 * so we can sort numerically (e.g. 1.20.4 > 1.9 correctly).
 */
private fun versionKey(v: String): Int {
    val parts = v.split(".").mapNotNull { it.toIntOrNull() }
    val major = parts.getOrElse(0) { 0 }
    val minor = parts.getOrElse(1) { 0 }
    val patch = parts.getOrElse(2) { 0 }
    return major * 10_000 + minor * 100 + patch
}

private fun formatDate(iso: String?): String {
    if (iso == null) return "Unknown"
    return try {
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
        formatter.format(Instant.parse(iso))
    } catch (e: Exception) { iso.take(10) }
}
package com.example.modrinthforandroid.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.launch
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import coil.compose.AsyncImage
import com.example.modrinthforandroid.data.AppSettings
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.data.RendererChangeResult
import com.example.modrinthforandroid.data.RendererRules
import com.example.modrinthforandroid.data.model.ModProject
import com.example.modrinthforandroid.data.model.ModVersion
import com.example.modrinthforandroid.ui.components.DownloadProgressBanner
import com.example.modrinthforandroid.ui.components.DownloadStatus
import com.example.modrinthforandroid.ui.components.ModpackInstallSheet
import com.example.modrinthforandroid.ui.components.formatNumber
import com.example.modrinthforandroid.viewmodel.ModDetailUiState
import com.example.modrinthforandroid.viewmodel.ModDetailViewModel
import com.example.modrinthforandroid.viewmodel.ModDetailViewModelFactory
import com.example.modrinthforandroid.work.DownloadWorker
import com.example.modrinthforandroid.work.KEY_DOWNLOAD_ID
import com.example.modrinthforandroid.work.KEY_FILENAME
import com.example.modrinthforandroid.work.KEY_FINAL_DIR
import com.example.modrinthforandroid.work.KEY_INSTANCE_NAME
import com.example.modrinthforandroid.work.KEY_NOTIF_ID
import com.example.modrinthforandroid.work.KEY_PROJECT_TYPE
import com.example.modrinthforandroid.work.KEY_ROOT_URI
import com.example.modrinthforandroid.work.KEY_TEMP_PATH
import com.example.modrinthforandroid.work.KEY_TITLE
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// ─── Download state ────────────────────────────────────────────────────────────

enum class DownloadState { IDLE, QUEUED, DOWNLOADING, DONE, FAILED }

data class VersionDownloadInfo(
    val versionId: String,
    val projectSlug: String = "",
    val workerId: UUID? = null,
    val state: DownloadState = DownloadState.IDLE,
    val progress: Int = 0
)

// ─── Download helper ──────────────────────────────────────────────────────────

fun triggerDownload(
    context: Context,
    url: String,
    filename: String,
    title: String,
    projectType: String
): UUID {
    val downloadsDir = android.os.Environment
        .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        .also { it.mkdirs() }

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

    val rootUriStr   = InstanceManager.rootUri?.toString() ?: ""
    val instanceName = InstanceManager.activeInstanceName ?: ""

    val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
        .setInputData(workDataOf(
            KEY_DOWNLOAD_ID   to downloadId,
            KEY_FILENAME      to filename,
            KEY_TITLE         to title,
            KEY_NOTIF_ID      to notifId,
            KEY_FINAL_DIR     to finalDir,
            KEY_TEMP_PATH     to File(downloadsDir, filename).absolutePath,
            KEY_ROOT_URI      to rootUriStr,
            KEY_INSTANCE_NAME to instanceName,
            KEY_PROJECT_TYPE  to projectType
        ))
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .build()

    WorkManager.getInstance(context).enqueue(workRequest)
    return workRequest.id
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

    var downloadInfoMap by remember { mutableStateOf(mapOf<String, VersionDownloadInfo>()) }

    val bannerDownloads = remember(downloadInfoMap) {
        downloadInfoMap.values
            .filter { it.state != DownloadState.IDLE }
            .map { info ->
                val proj = (uiState as? ModDetailUiState.Success)?.project
                val ver  = (uiState as? ModDetailUiState.Success)?.versions
                    ?.firstOrNull { it.id == info.versionId }
                DownloadStatus(
                    id       = info.versionId,
                    title    = "${proj?.title ?: "Mod"} — ${ver?.name ?: info.versionId}",
                    filename = ver?.files?.firstOrNull { it.primary }?.filename
                        ?: ver?.files?.firstOrNull()?.filename ?: "",
                    progress = if (info.state == DownloadState.QUEUED) -1 else info.progress,
                    isDone   = info.state == DownloadState.DONE,
                    isFailed = info.state == DownloadState.FAILED
                )
            }
    }

    val repository     = remember { com.example.modrinthforandroid.data.ModrinthRepository() }
    val coroutineScope = rememberCoroutineScope()

    var toastMessage by remember { mutableStateOf<String?>(null) }
    var rendererChangeResult: RendererChangeResult? by remember { mutableStateOf(null) }

    // ── Modpack install sheet state ───────────────────────────────────────────
    var pendingModpackVersion by remember { mutableStateOf<Pair<ModVersion, ModProject>?>(null) }

    fun enqueueOne(
        version: ModVersion,
        displayTitle: String,
        projectType: String,
        projectSlug: String = ""
    ) {
        val file = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull()
        ?: return
        downloadInfoMap = downloadInfoMap + (version.id to VersionDownloadInfo(
            versionId   = version.id,
            projectSlug = projectSlug,
            state       = DownloadState.QUEUED,
            progress    = 0
        ))
        val workerId = triggerDownload(
            context     = context,
            url         = file.url,
            filename    = file.filename,
            title       = displayTitle,
            projectType = projectType
        )
        downloadInfoMap = downloadInfoMap + (version.id to VersionDownloadInfo(
            versionId   = version.id,
            projectSlug = projectSlug,
            workerId    = workerId,
            state       = DownloadState.DOWNLOADING,
            progress    = 0
        ))
    }

    fun startDownload(version: ModVersion, project: ModProject) {
        // Modpacks get their own install flow — create a new instance instead
        if (project.projectType == "modpack") {
            pendingModpackVersion = version to project
            return
        }

        val instanceConfig = InstanceManager.activeInstanceConfig

        // Always enqueue the main mod immediately for instant feedback
        enqueueOne(version, "${project.title} ${version.name}", project.projectType, project.slug)

        // If we have an active instance, resolve dependencies in the background
        if (instanceConfig != null && version.dependencies.any { it.isRequired || it.isOptional }) {
            coroutineScope.launch {
                val resolved = try {
                    repository.resolveRequiredDependencies(
                        version    = version,
                        mcVersion  = instanceConfig.mcVersion,
                        loaderSlug = instanceConfig.loaderSlug
                    )
                } catch (e: Exception) { emptyList() }

                val required = resolved.filter { !it.isOptional && it.version != null }
                val optional = resolved.filter {  it.isOptional && it.version != null }

                required.forEach { dep ->
                    dep.version?.let { depVer ->
                        enqueueOne(
                            version      = depVer,
                            displayTitle = "Dependency: ${depVer.name}",
                            projectType  = "mod"
                        )
                    }
                }

                val depCount = required.size
                toastMessage = when {
                    depCount == 0 && optional.isNotEmpty() ->
                        "Downloading ${project.title} (${optional.size} optional dep${if (optional.size != 1) "s" else ""} not included)"
                    depCount > 0 ->
                        "Downloading ${project.title} + $depCount dependenc${if (depCount != 1) "ies" else "y"}"
                    else -> null
                }
            }
        }
    }

    val wm = WorkManager.getInstance(context)

    downloadInfoMap.values
        .filter { it.workerId != null && it.state !in listOf(DownloadState.DONE, DownloadState.FAILED) }
        .forEach { info ->
            key(info.workerId) {
                LaunchedEffect(info.workerId) {
                    wm.getWorkInfoByIdFlow(info.workerId!!)
                        .collect { wi ->
                            if (wi == null) return@collect
                            val progress = wi.progress.getInt("progress", 0)
                            val newState = when (wi.state) {
                                WorkInfo.State.SUCCEEDED -> DownloadState.DONE
                                WorkInfo.State.FAILED,
                                WorkInfo.State.CANCELLED -> DownloadState.FAILED
                                WorkInfo.State.RUNNING   -> DownloadState.DOWNLOADING
                                else                     -> DownloadState.QUEUED
                            }
                            downloadInfoMap = downloadInfoMap + (info.versionId to info.copy(
                                state    = newState,
                                progress = if (newState == DownloadState.DONE) 100 else progress
                            ))
                            if (newState == DownloadState.DONE && info.projectSlug.isNotEmpty()) {
                                val result = RendererRules.applyIfNeeded(
                                    context     = context,
                                    projectSlug = info.projectSlug
                                )
                                if (result != null) rendererChangeResult = result
                            }
                        }
                }
            }
        }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        val title = (uiState as? ModDetailUiState.Success)?.project?.title ?: ""
                        Text(
                            text       = title,
                            fontWeight = FontWeight.Bold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            color      = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )

                DownloadProgressBanner(
                    downloads = bannerDownloads,
                    onDismiss = { id -> downloadInfoMap = downloadInfoMap - id }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val state = uiState) {
                is ModDetailUiState.Loading ->
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color    = MaterialTheme.colorScheme.primary
                    )
                is ModDetailUiState.Error ->
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("😕 Failed to load", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadProject() }) { Text("Retry") }
                    }
                is ModDetailUiState.Success ->
                    ModDetailContent(
                        project         = state.project,
                        versions        = state.versions,
                        context         = context,
                        downloadInfoMap = downloadInfoMap,
                        onStartDownload = { version -> startDownload(version, state.project) }
                    )
            }

            toastMessage?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(4_000)
                    toastMessage = null
                }
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { toastMessage = null }) {
                            Text("OK", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                ) {
                    Text(msg, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    // ── Renderer change dialog ────────────────────────────────────────────────
    rendererChangeResult?.let { result ->
        AlertDialog(
            onDismissRequest = { rendererChangeResult = null },
            title = {
                Text(
                    "⚙️  Renderer Updated",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(result.reason, style = MaterialTheme.typography.bodyMedium)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Before:", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.width(46.dp))
                                Surface(shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)) {
                                    Text(result.oldRendererDisplay,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("After:", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.width(46.dp))
                                Surface(shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                                    Text(result.newRendererDisplay,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { rendererChangeResult = null }) { Text("Got it") }
            }
        )
    }

    // ── Modpack install sheet ─────────────────────────────────────────────────
    pendingModpackVersion?.let { (version, project) ->
        val mrpackFile = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull()
        if (mrpackFile != null) {
            ModpackInstallSheet(
                modpackTitle = project.title,
                mrpackUrl    = mrpackFile.url,
                iconUrl      = project.iconUrl,
                onDismiss    = { pendingModpackVersion = null }
            )
        }
    }
}

// ─── Content + tabs ───────────────────────────────────────────────────────────

@Composable
private fun ModDetailContent(
    project: ModProject,
    versions: List<ModVersion>,
    context: Context,
    downloadInfoMap: Map<String, VersionDownloadInfo>,
    onStartDownload: (ModVersion) -> Unit
) {
    var selectedTab       by remember { mutableIntStateOf(0) }
    var showDownloadSheet by remember { mutableStateOf(false) }

    val tabs = buildList {
        add("Description")
        if (!project.gallery.isNullOrEmpty()) add("Gallery")
        add("Versions")
    }

    val instanceConfig   = remember { InstanceManager.activeInstanceConfig }
    val recommendedVersion = remember(versions, instanceConfig) {
        if (instanceConfig == null) null else {
            val mcAndLoader = versions.filter { v ->
                v.gameVersions.contains(instanceConfig.mcVersion) &&
                        v.loaders.any { it.equals(instanceConfig.loaderSlug, ignoreCase = true) }
            }
            val mcOnly = versions.filter { v ->
                v.gameVersions.contains(instanceConfig.mcVersion)
            }
            fun List<ModVersion>.bestRelease() =
                firstOrNull { it.versionType == "release" }
                    ?: firstOrNull { it.versionType == "beta" }
                    ?: firstOrNull()

            mcAndLoader.bestRelease() ?: mcOnly.bestRelease()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ModDetailHeader(
            project               = project,
            versions              = versions,
            downloadInfoMap       = downloadInfoMap,
            onDownloadClick       = { showDownloadSheet = true },
            recommendedVersion    = recommendedVersion,
            onRecommendedDownload = {
                if (recommendedVersion != null) onStartDownload(recommendedVersion)
            }
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor   = MaterialTheme.colorScheme.background,
            contentColor     = MaterialTheme.colorScheme.primary,
            indicator        = { tabPositions ->
                if (selectedTab < tabPositions.size)
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color    = MaterialTheme.colorScheme.primary
                    )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick  = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        when (tabs.getOrNull(selectedTab)) {
            "Description" -> DescriptionTab(project = project)
            "Gallery"     -> GalleryTab(images = project.gallery?.map { it.url } ?: emptyList())
            "Versions"    -> VersionsTab(
                versions        = versions,
                projectTitle    = project.title,
                projectType     = project.projectType,
                downloadInfoMap = downloadInfoMap,
                context         = context,
                onStartDownload = onStartDownload
            )
        }
    }

    if (showDownloadSheet) {
        DownloadPickerSheet(
            project         = project,
            versions        = versions,
            downloadInfoMap = downloadInfoMap,
            context         = context,
            onStartDownload = onStartDownload,
            onDismiss       = { showDownloadSheet = false }
        )
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun ModDetailHeader(
    project: ModProject,
    versions: List<ModVersion>,
    downloadInfoMap: Map<String, VersionDownloadInfo>,
    onDownloadClick: () -> Unit,
    recommendedVersion: ModVersion? = null,
    onRecommendedDownload: () -> Unit = {}
) {
    val latestVersion = versions.firstOrNull { it.versionType == "release" } ?: versions.firstOrNull()
    val anyDownloading = downloadInfoMap.values.any {
        it.state in listOf(DownloadState.QUEUED, DownloadState.DOWNLOADING)
    }
    val anyDone = downloadInfoMap.values.any { it.state == DownloadState.DONE }
    val recommendedInfo = recommendedVersion?.let { downloadInfoMap[it.id] }

    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model              = project.iconUrl,
                    contentDescription = "${project.title} icon",
                    modifier           = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale       = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        project.title,
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            project.projectType.replaceFirstChar { it.uppercase() },
                            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style      = MaterialTheme.typography.labelSmall,
                            color      = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                project.description,
                style      = MaterialTheme.typography.bodyMedium,
                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatChip(Icons.Default.KeyboardArrowDown, "Downloads", formatNumber(project.downloads))
                StatChip(Icons.Default.Favorite,          "Followers", formatNumber(project.followers))
                StatChip(Icons.Default.Build,             "Versions",  (project.versions?.size ?: 0).toString())
            }

            Spacer(modifier = Modifier.height(14.dp))

            val green = MaterialTheme.colorScheme.primary
            Button(
                onClick        = onDownloadClick,
                modifier       = Modifier.fillMaxWidth(),
                shape          = RoundedCornerShape(10.dp),
                colors         = ButtonDefaults.buttonColors(
                    containerColor = when {
                        anyDownloading -> green.copy(alpha = 0.7f)
                        anyDone        -> green
                        else           -> green
                    }
                ),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                when {
                    anyDownloading -> {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color       = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Downloading…",
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    anyDone -> {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint     = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Downloaded!",
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                "Tap to download another version",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                            )
                        }
                    }
                    else -> {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint     = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (project.projectType == "modpack") "Install Modpack" else "Download",
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onPrimary
                            )
                            if (latestVersion != null) {
                                val mcVer = latestVersion.gameVersions
                                    .filter { it.matches(Regex("""\d+\.\d+(\.\d+)?""")) }
                                    .maxByOrNull { it } ?: ""
                                Text(
                                    "${latestVersion.versionNumber}  •  $mcVer",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }

            if (recommendedVersion != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val recState    = recommendedInfo?.state ?: DownloadState.IDLE
                val recProgress = recommendedInfo?.progress ?: 0
                val instanceConfig = InstanceManager.activeInstanceConfig

                OutlinedButton(
                    onClick        = onRecommendedDownload,
                    modifier       = Modifier.fillMaxWidth(),
                    shape          = RoundedCornerShape(10.dp),
                    colors         = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    border         = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.primary.copy(
                            alpha = if (recState == DownloadState.DONE) 0.4f else 1f
                        )
                    )
                ) {
                    when (recState) {
                        DownloadState.QUEUED -> {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Queued…", fontWeight = FontWeight.SemiBold)
                        }
                        DownloadState.DOWNLOADING -> {
                            CircularProgressIndicator(
                                progress    = { recProgress / 100f },
                                modifier    = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.primary,
                                trackColor  = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("$recProgress%", fontWeight = FontWeight.SemiBold)
                        }
                        DownloadState.DONE -> {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Recommended Downloaded!", fontWeight = FontWeight.SemiBold)
                                Text(
                                    recommendedVersion.versionNumber,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                )
                            }
                        }
                        else -> {
                            Text("⚡", fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Download Recommended",
                                    fontWeight = FontWeight.SemiBold,
                                    color      = MaterialTheme.colorScheme.primary
                                )
                                val mc     = instanceConfig?.mcVersion ?: ""
                                val loader = instanceConfig?.loader ?: ""
                                Text(
                                    "${recommendedVersion.versionNumber}  •  $loader $mc",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                )
                            }
                        }
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

// ─── Download Picker Sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadPickerSheet(
    project: ModProject,
    versions: List<ModVersion>,
    downloadInfoMap: Map<String, VersionDownloadInfo>,
    context: Context,
    onStartDownload: (ModVersion) -> Unit,
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

    val instanceConfig = remember { InstanceManager.activeInstanceConfig }

    var selectedMc     by remember {
        mutableStateOf<String?>(
            instanceConfig?.mcVersion?.let { mc ->
                allMcVersions.firstOrNull { it == mc }
            }
        )
    }
    var selectedLoader by remember {
        mutableStateOf<String?>(
            instanceConfig?.loaderSlug?.let { slug ->
                allLoaders.firstOrNull { it == slug }
            }
        )
    }
    var pendingDownloadVersion by remember { mutableStateOf<ModVersion?>(null) }

    val filteredVersions = remember(versions, selectedMc, selectedLoader) {
        versions.filter { v ->
            val mcOk     = selectedMc     == null || v.gameVersions.contains(selectedMc)
            val loaderOk = selectedLoader == null || v.loaders.contains(selectedLoader)
            mcOk && loaderOk
        }
    }

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
                        Text(
                            "Don't warn me again",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onStartDownload(version)
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
            Text(
                if (project.projectType == "modpack") "Install ${project.title}" else "Download ${project.title}",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (instanceConfig != null && (selectedMc != null || selectedLoader != null)) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎮", fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Pre-filtered for ${instanceConfig.summary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Minecraft Version",
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
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
                Text(
                    "Loader",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedLoader == null,
                            onClick  = { selectedLoader = null },
                            label    = { Text("Any") }
                        )
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
                "${filteredVersions.size} version${if (filteredVersions.size != 1) "s" else ""}",
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
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No versions match your filters",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    itemsIndexed(filteredVersions) { index, version ->
                        val info = downloadInfoMap[version.id]
                        PickerVersionCard(
                            version     = version,
                            isLatest    = index == 0,
                            downloadInfo = info,
                            onDownload  = {
                                val isDone = info?.state == DownloadState.DONE
                                if (isDone && settings.showDuplicateWarning) {
                                    pendingDownloadVersion = version
                                } else {
                                    onStartDownload(version)
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
    downloadInfo: VersionDownloadInfo?,
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

    val state    = downloadInfo?.state ?: DownloadState.IDLE
    val progress = downloadInfo?.progress ?: 0

    Surface(
        shape          = RoundedCornerShape(12.dp),
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLatest) 3.dp else 1.dp,
        modifier       = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment    = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            version.name,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            modifier   = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = typeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                version.versionType.replaceFirstChar { it.uppercase() },
                                modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style      = MaterialTheme.typography.labelSmall,
                                color      = typeColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (isLatest) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "Latest",
                                    modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style      = MaterialTheme.typography.labelSmall,
                                    color      = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        mcVersions.take(3).forEach { gv ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    gv,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (mcVersions.size > 3) {
                            Text(
                                "+${mcVersions.size - 3}",
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                        version.loaders.take(2).forEach { loader ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    loader,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${formatDate(version.datePublished)}  •  ⬇ ${formatNumber(version.downloads)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                DownloadButton(state = state, progress = progress, onClick = onDownload)
            }

            if (state == DownloadState.DOWNLOADING || state == DownloadState.QUEUED) {
                Spacer(Modifier.height(8.dp))
                InlineProgressBar(
                    progress = if (state == DownloadState.QUEUED) -1 else progress,
                    color    = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(10.dp))

                    if (mcVersions.size > 3) {
                        Text(
                            "All MC versions:",
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        mcVersions.chunked(5).forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                row.forEach { gv ->
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    ) {
                                        Text(
                                            gv,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            style    = MaterialTheme.typography.labelSmall,
                                            color    = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        "Changelog",
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text       = if (version.changelog.isNullOrBlank()) "No changelog provided."
                        else version.changelog.trim(),
                        style      = MaterialTheme.typography.bodySmall,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        lineHeight = 18.sp
                    )
                }
            }

            Row(
                modifier              = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector        = if (expanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Show changelog",
                    modifier           = Modifier.size(16.dp),
                    tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
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
    downloadInfoMap: Map<String, VersionDownloadInfo>,
    context: Context,
    onStartDownload: (ModVersion) -> Unit
) {
    val settings = remember { AppSettings.get(context) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var pendingDownloadVersion by remember { mutableStateOf<ModVersion?>(null) }

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
                        Text(
                            "Don't warn me again",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onStartDownload(version)
                    snackbarMessage = "Downloading ${version.name}…"
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
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No versions available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier        = Modifier.fillMaxSize(),
                contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "${versions.size} version${if (versions.size != 1) "s" else ""} available",
                        style    = MaterialTheme.typography.labelMedium,
                        color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                itemsIndexed(versions) { index, version ->
                    val info = downloadInfoMap[version.id]
                    VersionCard(
                        version     = version,
                        isLatest    = index == 0,
                        downloadInfo = info,
                        onDownload  = {
                            val isDone = info?.state == DownloadState.DONE
                            if (isDone && settings.showDuplicateWarning) {
                                pendingDownloadVersion = version
                            } else {
                                onStartDownload(version)
                                snackbarMessage = if (projectType == "modpack")
                                    "Installing ${version.name}…"
                                else
                                    "Downloading ${version.name}…"
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
    downloadInfo: VersionDownloadInfo?,
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

    val state    = downloadInfo?.state ?: DownloadState.IDLE
    val progress = downloadInfo?.progress ?: 0

    Surface(
        shape          = RoundedCornerShape(12.dp),
        color          = when (state) {
            DownloadState.DONE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            else               -> MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (isLatest) 3.dp else 1.dp,
        modifier       = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            version.name,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        if (state == DownloadState.DONE) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(10.dp),
                                        tint     = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        "Downloaded",
                                        style  = MaterialTheme.typography.labelSmall,
                                        color  = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        version.versionNumber,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = typeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            version.versionType.replaceFirstChar { it.uppercase() },
                            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style      = MaterialTheme.typography.labelSmall,
                            color      = typeColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (isLatest) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Latest",
                                modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style      = MaterialTheme.typography.labelSmall,
                                color      = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (mcVersions.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(mcVersions.take(5)) { gv ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        ) {
                            Text(
                                gv,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (mcVersions.size > 5) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                            ) {
                                Text(
                                    "+${mcVersions.size - 5} more",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (version.loaders.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    version.loaders.take(3).forEach { loader ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                loader,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        formatDate(version.datePublished),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        "⬇ ${formatNumber(version.downloads)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                DownloadButton(state = state, progress = progress, onClick = onDownload)
            }

            if (state == DownloadState.DOWNLOADING || state == DownloadState.QUEUED) {
                Spacer(Modifier.height(8.dp))
                InlineProgressBar(
                    progress = if (state == DownloadState.QUEUED) -1 else progress,
                    color    = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(10.dp))

                    if (mcVersions.size > 5) {
                        Text(
                            "All MC versions:",
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        mcVersions.chunked(5).forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                row.forEach { gv ->
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    ) {
                                        Text(
                                            gv,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            style    = MaterialTheme.typography.labelSmall,
                                            color    = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        "Changelog",
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text       = if (version.changelog.isNullOrBlank()) "No changelog provided."
                        else version.changelog.trim(),
                        style      = MaterialTheme.typography.bodySmall,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        lineHeight = 18.sp
                    )
                }
            }

            Row(
                modifier              = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector        = if (expanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Show changelog",
                    modifier           = Modifier.size(16.dp),
                    tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

// ─── Shared Download Button ───────────────────────────────────────────────────

@Composable
fun DownloadButton(
    state: DownloadState,
    progress: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val green = MaterialTheme.colorScheme.primary

    Button(
        onClick        = onClick,
        shape          = RoundedCornerShape(8.dp),
        colors         = ButtonDefaults.buttonColors(
            containerColor = when (state) {
                DownloadState.DONE       -> green
                DownloadState.FAILED     -> MaterialTheme.colorScheme.error
                DownloadState.DOWNLOADING,
                DownloadState.QUEUED     -> green.copy(alpha = 0.6f)
                else                     -> green
            }
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier       = modifier
    ) {
        when (state) {
            DownloadState.QUEUED -> {
                CircularProgressIndicator(
                    modifier    = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color       = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(5.dp))
                Text("Queued", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            DownloadState.DOWNLOADING -> {
                CircularProgressIndicator(
                    progress    = { progress / 100f },
                    modifier    = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color       = MaterialTheme.colorScheme.onPrimary,
                    trackColor  = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                )
                Spacer(Modifier.width(5.dp))
                Text("$progress%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            DownloadState.DONE -> {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Done", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            DownloadState.FAILED -> {
                Text("✕", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Text("Failed", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            else -> {
                Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Download", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun InlineProgressBar(progress: Int, color: Color) {
    val animatedProgress by animateFloatAsState(
        targetValue   = if (progress < 0) 0f else progress / 100f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label         = "inlineProgress"
    )

    val shimmerTranslate = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by shimmerTranslate.animateFloat(
        initialValue  = -400f,
        targetValue   = 800f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label         = "shimmerX"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
    ) {
        if (progress < 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(120.dp)
                    .offset(x = (shimmerX / 3).dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                color.copy(alpha = 0.8f),
                                Color.Transparent
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
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
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                version,
                                modifier   = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style      = MaterialTheme.typography.labelSmall,
                                color      = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
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
            OutlinedButton(
                onClick   = { uriHandler.openUri(licenseUrl) },
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("View License", style = MaterialTheme.typography.labelMedium)
            }
        }
        Button(
            onClick  = { uriHandler.openUri("https://modrinth.com/${project.projectType}/${project.slug}") },
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(8.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                "View on Modrinth",
                color      = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape          = RoundedCornerShape(10.dp),
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier       = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), content = content)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            value,
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color      = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ─── Gallery Tab ──────────────────────────────────────────────────────────────

@Composable
private fun GalleryTab(images: List<String>) {
    var selectedImage by remember { mutableStateOf<String?>(null) }
    Column(
        modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        images.forEach { imageUrl ->
            Card(
                onClick   = { selectedImage = imageUrl },
                shape     = RoundedCornerShape(12.dp),
                modifier  = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                AsyncImage(
                    model              = imageUrl,
                    contentDescription = "Gallery image",
                    modifier           = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                    contentScale       = ContentScale.Crop
                )
            }
        }
    }
    if (selectedImage != null)
        FullscreenImageViewer(imageUrl = selectedImage!!, onDismiss = { selectedImage = null })
}

@Composable
private fun FullscreenImageViewer(imageUrl: String, onDismiss: () -> Unit) {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model              = imageUrl,
            contentDescription = "Full size image",
            modifier           = Modifier.fillMaxWidth().padding(16.dp),
            contentScale       = ContentScale.Fit
        )
        IconButton(
            onClick  = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
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
            shape    = RoundedCornerShape(8.dp),
            color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Row(
                modifier              = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
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
        Text(
            label,
            modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style      = MaterialTheme.typography.labelSmall,
            color      = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CategoryChip(label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun versionKey(v: String): Int {
    val parts = v.split(".").mapNotNull { it.toIntOrNull() }
    return parts.getOrElse(0) { 0 } * 10_000 +
            parts.getOrElse(1) { 0 } * 100 +
            parts.getOrElse(2) { 0 }
}

private fun formatDate(iso: String?): String {
    if (iso == null) return "Unknown"
    return try {
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
        formatter.format(Instant.parse(iso))
    } catch (e: Exception) { iso.take(10) }
}
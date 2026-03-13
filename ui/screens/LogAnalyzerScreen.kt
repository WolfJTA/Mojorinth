package com.example.modrinthforandroid.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.example.modrinthforandroid.data.InstanceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

// ── Data models ───────────────────────────────────────────────────────────────

private data class LogProblem(
    val message: String,
    val counter: Int = 1,
    val solutions: List<String> = emptyList(),  // solution messages from mclo.gs
    val logLine: String? = null                 // first log line that triggered it
)

private data class LogInfo(
    val label: String,
    val value: String
)

private data class AnalysisResult(
    val logId: String,
    val webUrl: String,
    val errorCount: Int,          // top-level "errors" field from upload response
    val title: String,            // e.g. "Fabric 0.15.11 Client Log"
    val problems: List<LogProblem>,
    val information: List<LogInfo>
)

private data class LogFileEntry(
    val uri: Uri,
    val name: String,
    val sizeKb: Long
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogAnalyzerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var logFiles      by remember { mutableStateOf<List<LogFileEntry>>(emptyList()) }
    var selectedFile  by remember { mutableStateOf<LogFileEntry?>(null) }
    var isLoading     by remember { mutableStateOf(false) }
    var errorMsg      by remember { mutableStateOf<String?>(null) }
    var result        by remember { mutableStateOf<AnalysisResult?>(null) }
    var expandedInsight   by remember { mutableStateOf<Int?>(null) }
    var problemsExpanded  by remember { mutableStateOf(false) }

    // SAF launcher — opens system folder picker filtered to active instance logs/
    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val df   = DocumentFile.fromSingleUri(context, uri)
        val name = df?.name ?: uri.lastPathSegment ?: "unknown.log"
        val size = df?.length()?.div(1024L) ?: 0L
        selectedFile = LogFileEntry(uri, name, size)
        logFiles     = emptyList()
        result       = null
        errorMsg     = null
    }

    // Scan the active instance's logs/ folder via SAF and list log files
    fun loadInstanceLogs() {
        val rootUri = InstanceManager.rootUri ?: return
        val instanceName = InstanceManager.activeInstanceName ?: return
        scope.launch(Dispatchers.IO) {
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return@launch
            val instanceDir = rootDoc.findFile(instanceName) ?: return@launch
            val logsDir = instanceDir.findFile("logs") ?: return@launch
            val entries = logsDir.listFiles()
                .filter { it.isFile && (it.name?.endsWith(".log") == true ||
                        it.name?.endsWith(".gz") == true ||
                        it.name?.endsWith(".txt") == true) }
                .sortedByDescending { it.lastModified() }
                .map { df ->
                    LogFileEntry(
                        uri    = df.uri,
                        name   = df.name ?: "unknown",
                        sizeKb = df.length() / 1024L
                    )
                }
            withContext(Dispatchers.Main) {
                logFiles    = entries
                selectedFile = null
                result      = null
                errorMsg    = null
            }
        }
    }

    // Upload selected file to mclo.gs and parse response
    fun analyze() {
        val file = selectedFile ?: return
        scope.launch {
            isLoading = true
            errorMsg  = null
            result    = null
            try {
                val text = withContext(Dispatchers.IO) {
                    readFileText(context, file.uri)
                }
                val analysisResult = withContext(Dispatchers.IO) {
                    uploadAndAnalyze(text)
                }
                result    = analysisResult
                isLoading = false
            } catch (e: Exception) {
                errorMsg  = e.message ?: "Unknown error"
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Log Analyzer",
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                        selectedFile?.let {
                            Text(
                                it.name,
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start  = 16.dp,
                end    = 16.dp,
                top    = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Pick source ──────────────────────────────────────────────
            item {
                Text(
                    "Pick a log file",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                val hasInstance = InstanceManager.activeInstanceName != null &&
                        InstanceManager.rootUri != null
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick  = { if (hasInstance) loadInstanceLogs() },
                        enabled  = hasInstance,
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor   = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Folder, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Instance Logs", style = MaterialTheme.typography.labelMedium)
                    }

                    // Generic file picker
                    OutlinedButton(
                        onClick  = {
                            safLauncher.launch(
                                arrayOf("text/plain", "application/octet-stream", "*/*")
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Browse Files", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (!hasInstance) {
                    Text(
                        "⚠ Select an instance on the home screen to browse its logs folder.",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // ── Instance log list ────────────────────────────────────────
            if (logFiles.isNotEmpty()) {
                item {
                    Text(
                        "Logs in instance  •  ${logFiles.size} files",
                        style  = MaterialTheme.typography.labelSmall,
                        color  = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(logFiles, key = { it.uri.toString() }) { entry ->
                    val isSelected = selectedFile?.uri == entry.uri
                    Surface(
                        shape    = RoundedCornerShape(10.dp),
                        color    = if (isSelected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedFile = entry
                                result       = null
                                errorMsg     = null
                            }
                    ) {
                        Row(
                            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(logIcon(entry.name), fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.name,
                                    style      = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines   = 1,
                                    overflow   = TextOverflow.Ellipsis,
                                    color      = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${entry.sizeKb} KB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle, null,
                                    tint     = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Selected file chip ───────────────────────────────────────
            selectedFile?.let { file ->
                if (logFiles.isEmpty()) { // only show chip if came from generic picker
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        ) {
                            Row(
                                modifier          = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(logIcon(file.name), fontSize = 20.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        file.name,
                                        fontWeight = FontWeight.SemiBold,
                                        style      = MaterialTheme.typography.bodySmall,
                                        maxLines   = 1,
                                        overflow   = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${file.sizeKb} KB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                                IconButton(
                                    onClick  = { selectedFile = null; result = null },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close, "Clear",
                                        tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Analyze button ───────────────────────────────────────────
            if (selectedFile != null && result == null && !isLoading) {
                item {
                    Button(
                        onClick  = { analyze() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.BugReport, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Analyze with mclo.gs")
                    }
                }
            }

            // ── Loading ──────────────────────────────────────────────────
            if (isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement   = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                "Uploading to mclo.gs…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // ── Error ────────────────────────────────────────────────────
            errorMsg?.let { msg ->
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier          = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error, null,
                                tint     = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // ── Results ──────────────────────────────────────────────────
            result?.let { analysis ->
                item {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Analysis Results",
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onBackground
                            )
                            if (analysis.title.isNotBlank()) {
                                Text(
                                    analysis.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                )
                            }
                        }
                        // (error count shown in the collapsible problems card below)
                    }
                }

                // mclo.gs link chip — tapping opens the log in the browser
                item {
                    Surface(
                        onClick  = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(analysis.webUrl)
                            )
                            context.startActivity(intent)
                        },
                        shape    = RoundedCornerShape(10.dp),
                        color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Link, null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "mclo.gs/${analysis.logId}",
                                    style      = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Tap to open in browser",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            Icon(
                                Icons.Default.OpenInBrowser, null,
                                tint     = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // ── Problems ─────────────────────────────────────────────
                if (analysis.problems.isEmpty()) {
                    item {
                        Surface(
                            shape    = RoundedCornerShape(10.dp),
                            color    = Color(0xFF1BD96A).copy(alpha = 0.10f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier          = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✅", fontSize = 20.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "No problems detected by mclo.gs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    // Collapsible problems container
                    item {
                        Surface(
                            onClick  = { problemsExpanded = !problemsExpanded },
                            shape    = RoundedCornerShape(10.dp),
                            color    = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                // Header row — always visible
                                Row(
                                    modifier          = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.BugReport, null,
                                        tint     = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${analysis.problems.size} error${if (analysis.problems.size != 1) "s" else ""} — tap to ${if (problemsExpanded) "collapse" else "expand"}",
                                        style      = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color      = MaterialTheme.colorScheme.error,
                                        modifier   = Modifier.weight(1f)
                                    )
                                    Icon(
                                        if (problemsExpanded) Icons.Default.ExpandLess
                                        else Icons.Default.ExpandMore,
                                        null,
                                        tint     = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Expanded problem list
                                if (problemsExpanded) {
                                    HorizontalDivider(
                                        color     = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                        modifier  = Modifier.padding(horizontal = 14.dp)
                                    )
                                    Column(
                                        modifier            = Modifier.padding(
                                            start  = 14.dp,
                                            end    = 14.dp,
                                            top    = 10.dp,
                                            bottom = 14.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        analysis.problems.forEachIndexed { idx, problem ->
                                            val isExpanded = expandedInsight == idx
                                            ProblemCard(
                                                problem    = problem,
                                                isExpanded = isExpanded,
                                                onClick    = {
                                                    expandedInsight = if (isExpanded) null else idx
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Information ──────────────────────────────────────────
                if (analysis.information.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "ℹ Log Information",
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                analysis.information.forEach { info ->
                                    Row {
                                        Text(
                                            info.label,
                                            style      = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            modifier   = Modifier.width(120.dp)
                                        )
                                        Text(
                                            info.value,
                                            style    = MaterialTheme.typography.labelSmall,
                                            color    = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Analyze another button
                item {
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick  = { result = null; selectedFile = null; logFiles = emptyList() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Analyze another log")
                    }
                }
            }
        }
    }
}

// ── ProblemCard ───────────────────────────────────────────────────────────────

@Composable
private fun ProblemCard(
    problem: LogProblem,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.BugReport, null,
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "PROBLEM",
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.error,
                    fontSize   = 9.sp
                )
                if (problem.counter > 1) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "×${problem.counter}",
                            modifier   = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style      = MaterialTheme.typography.labelSmall,
                            color      = MaterialTheme.colorScheme.error,
                            fontSize   = 9.sp
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint     = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.height(6.dp))

            // Problem message
            Text(
                text     = problem.message,
                style    = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color    = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = if (isExpanded) TextOverflow.Clip else TextOverflow.Ellipsis
            )

            // Expanded details
            if (isExpanded) {
                // Log line that triggered this
                problem.logLine?.let { line ->
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                    ) {
                        Text(
                            line,
                            modifier   = Modifier.padding(8.dp).fillMaxWidth(),
                            style      = MaterialTheme.typography.labelSmall,
                            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }

                // Solutions
                if (problem.solutions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "💡 Suggested fixes:",
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onErrorContainer
                    )
                    problem.solutions.forEach { solution ->
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                "•",
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(end = 6.dp, top = 1.dp)
                            )
                            Text(
                                solution,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun logIcon(name: String): String = when {
    name.contains("crash", ignoreCase = true) -> "💥"
    name.contains("debug", ignoreCase = true) -> "🔍"
    name.endsWith(".gz")                       -> "🗜"
    else                                       -> "📄"
}

private fun readFileText(context: Context, uri: Uri): String {
    val raw = context.contentResolver.openInputStream(uri)
        ?: throw Exception("Could not open the log file.")

    // Decompress .gz files before reading — mclo.gs expects plain text
    val fileName = uri.lastPathSegment ?: ""
    val stream = if (fileName.endsWith(".gz", ignoreCase = true)) {
        Log.d("LogAnalyzer", "Decompressing .gz log: $fileName")
        java.util.zip.GZIPInputStream(raw)
    } else {
        raw
    }

    val sb = StringBuilder()
    stream.use {
        BufferedReader(InputStreamReader(it)).forEachLine { line ->
            sb.appendLine(line)
        }
    }
    if (sb.isEmpty()) throw Exception("Log file is empty.")
    return sb.toString()
}

// Shared OkHttp client for mclo.gs — singleton object so it is only built once.
private object MclogsHttpClient {
    val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor { msg ->
            Log.d("LogAnalyzer/OkHttp", msg)
        }.apply { level = HttpLoggingInterceptor.Level.BODY }

        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "MojorinthApp/1.0")
                    .build()
                chain.proceed(req)
            }
            .build()
    }
}

private fun uploadAndAnalyze(logText: String): AnalysisResult {
    // ── Step 1: Upload the log ────────────────────────────────────────────────
    val uploadBody = FormBody.Builder()
        .add("content", logText.take(10_000_000))
        .build()

    val uploadRequest = Request.Builder()
        .url("https://api.mclo.gs/1/log")
        .post(uploadBody)
        .build()

    Log.d("LogAnalyzer", "Uploading log to mclo.gs (${logText.length} chars)…")

    val uploadJson = MclogsHttpClient.client.newCall(uploadRequest).execute().use { response ->
        val bodyStr = response.body?.string()
            ?: throw Exception("Empty response from mclo.gs")
        Log.d("LogAnalyzer", "Upload HTTP ${response.code}: $bodyStr")
        if (!response.isSuccessful) throw Exception("mclo.gs returned HTTP ${response.code}")
        JSONObject(bodyStr)
    }

    if (!uploadJson.optBoolean("success", false)) {
        throw Exception(uploadJson.optString("error", "mclo.gs rejected the log."))
    }

    val logId  = uploadJson.optString("id", "")
    val webUrl = "https://mclo.gs/$logId"
    val errorCount = uploadJson.optInt("errors", 0)
    Log.d("LogAnalyzer", "Upload OK — id=$logId  errors=$errorCount  url=$webUrl")

    // ── Step 2: Fetch insights via GET /1/log/{id}?insights=true ─────────────
    val insightsRequest = Request.Builder()
        .url("https://api.mclo.gs/1/log/$logId?insights=true")
        .get()
        .build()

    Log.d("LogAnalyzer", "Fetching insights for $logId…")

    val insightsJson = MclogsHttpClient.client.newCall(insightsRequest).execute().use { response ->
        val bodyStr = response.body?.string()
            ?: throw Exception("Empty insights response from mclo.gs")
        Log.d("LogAnalyzer", "Insights HTTP ${response.code}: $bodyStr")
        if (!response.isSuccessful) throw Exception("mclo.gs insights returned HTTP ${response.code}")
        JSONObject(bodyStr)
    }

    // Title comes from the insights response (e.g. "Fabric 0.15.11 Client Log")
    val title = insightsJson.optString("title", "")

    // ── Step 3: Parse problems + information ──────────────────────────────────
    val problems    = mutableListOf<LogProblem>()
    val information = mutableListOf<LogInfo>()

    val contentObj   = insightsJson.optJSONObject("content")
    val insightsObj  = contentObj?.optJSONObject("insights")

    val problemsArr  = insightsObj?.optJSONArray("problems")
    val infoArr      = insightsObj?.optJSONArray("information")

    if (problemsArr != null) {
        for (i in 0 until problemsArr.length()) {
            val obj      = problemsArr.getJSONObject(i)
            val solutions = mutableListOf<String>()
            val solArr   = obj.optJSONArray("solutions")
            if (solArr != null) {
                for (s in 0 until solArr.length()) {
                    solutions += solArr.getJSONObject(s).optString("message", "")
                }
            }
            // Grab the first log line that triggered this problem
            val entry    = obj.optJSONObject("entry")
            val lines    = entry?.optJSONArray("lines")
            val firstLine = if (lines != null && lines.length() > 0)
                lines.getJSONObject(0).optString("content", null)
            else null

            problems += LogProblem(
                message   = obj.optString("message", ""),
                counter   = obj.optInt("counter", 1),
                solutions = solutions,
                logLine   = firstLine
            )
        }
    }

    if (infoArr != null) {
        for (i in 0 until infoArr.length()) {
            val obj = infoArr.getJSONObject(i)
            val label = obj.optString("label", "")
            val value = obj.optString("value", "")
            if (label.isNotBlank() && value.isNotBlank()) {
                information += LogInfo(label = label, value = value)
            }
        }
    }

    Log.d("LogAnalyzer", "Parsed ${problems.size} problem(s), ${information.size} info item(s)")

    return AnalysisResult(
        logId       = logId,
        webUrl      = webUrl,
        errorCount  = errorCount,
        title       = title,
        problems    = problems,
        information = information
    )
}
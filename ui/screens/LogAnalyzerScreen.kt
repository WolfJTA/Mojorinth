package com.example.modrinthforandroid.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.example.modrinthforandroid.data.InstanceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

// ── mclo.gs API ───────────────────────────────────────────────────────────────

data class MclogsResult(
    val url: String,
    val id: String,
    val analysis: MclogsAnalysis?
)

data class MclogsAnalysis(
    val problems: List<McloProblem>,
    val information: List<McloInfo>
)

data class McloProblem(
    val message: String,
    val counter: Int,
    val entry: McloEntry?
)

data class McloInfo(
    val message: String,
    val counter: Int,
    val entry: McloEntry?
)

data class McloEntry(
    val type: String,
    val time: String?,
    val prefix: String?,
    val lines: McloLines?
)

data class McloLines(
    val from: Int,
    val to: Int
)

private suspend fun uploadToMclogs(logContent: String): MclogsResult? = withContext(Dispatchers.IO) {
    try {
        // Step 1: upload log
        val uploadUrl = URL("https://api.mclo.gs/1/log")
        val conn = uploadUrl.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.connectTimeout = 15_000
        conn.readTimeout    = 15_000

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write("content=" + java.net.URLEncoder.encode(logContent, "UTF-8"))
        }

        val responseCode = conn.responseCode
        if (responseCode != 200) return@withContext null

        val uploadJson = JSONObject(conn.inputStream.bufferedReader().readText())
        if (!uploadJson.optBoolean("success", false)) return@withContext null

        val logId  = uploadJson.getString("id")
        val logUrl = uploadJson.getString("url")

        // Step 2: fetch analysis
        val analysisUrl = URL("https://api.mclo.gs/1/insights/$logId")
        val aConn = analysisUrl.openConnection() as HttpURLConnection
        aConn.connectTimeout = 15_000
        aConn.readTimeout    = 15_000

        val analysis = try {
            val aJson = JSONObject(aConn.inputStream.bufferedReader().readText())
            val problems = mutableListOf<McloProblem>()
            val information = mutableListOf<McloInfo>()

            val pArr = aJson.optJSONArray("problems")
            if (pArr != null) {
                for (i in 0 until pArr.length()) {
                    val p = pArr.getJSONObject(i)
                    problems.add(McloProblem(
                        message = p.optString("message", "Unknown issue"),
                        counter = p.optInt("counter", 1),
                        entry   = parseEntry(p.optJSONObject("entry"))
                    ))
                }
            }

            val iArr = aJson.optJSONArray("information")
            if (iArr != null) {
                for (i in 0 until iArr.length()) {
                    val inf = iArr.getJSONObject(i)
                    information.add(McloInfo(
                        message = inf.optString("message", ""),
                        counter = inf.optInt("counter", 1),
                        entry   = parseEntry(inf.optJSONObject("entry"))
                    ))
                }
            }

            MclogsAnalysis(problems, information)
        } catch (_: Exception) { null }

        MclogsResult(url = logUrl, id = logId, analysis = analysis)
    } catch (_: Exception) { null }
}

private fun parseEntry(obj: org.json.JSONObject?): McloEntry? {
    obj ?: return null
    val lines = obj.optJSONObject("lines")?.let {
        McloLines(from = it.optInt("from", 0), to = it.optInt("to", 0))
    }
    return McloEntry(
        type   = obj.optString("type", ""),
        time   = obj.optString("time").takeIf { it.isNotBlank() },
        prefix = obj.optString("prefix").takeIf { it.isNotBlank() },
        lines  = lines
    )
}

// ── Read log via SAF ──────────────────────────────────────────────────────────

private fun findLatestLog(context: Context): Pair<Uri, String>? {
    val rootUri      = InstanceManager.rootUri ?: return null
    val instanceName = InstanceManager.activeInstanceName ?: return null
    return try {
        val rootDoc     = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        val instanceDoc = rootDoc.findFile(instanceName) ?: return null
        val logsDir     = instanceDoc.findFile("logs") ?: return null
        val latestLog   = logsDir.findFile("latest.log") ?: return null
        val content     = context.contentResolver.openInputStream(latestLog.uri)
            ?.bufferedReader()?.use { it.readText() } ?: return null
        Pair(latestLog.uri, content)
    } catch (_: Exception) { null }
}

private fun readLogFromUri(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)
            ?.bufferedReader()?.use { it.readText() }
    } catch (_: Exception) { null }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogAnalyzerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val green = MaterialTheme.colorScheme.primary

    var logContent    by remember { mutableStateOf<String?>(null) }
    var logSource     by remember { mutableStateOf<String?>(null) }
    var isUploading   by remember { mutableStateOf(false) }
    var result        by remember { mutableStateOf<MclogsResult?>(null) }
    var errorMsg      by remember { mutableStateOf<String?>(null) }
    var showRawLog    by remember { mutableStateOf(false) }

    val activeInstance = InstanceManager.activeInstanceName

    // Manual file picker
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val content = readLogFromUri(context, uri)
            if (content != null) {
                logContent = content
                logSource  = "Picked file"
                result     = null
                errorMsg   = null
            } else {
                errorMsg = "Couldn't read that file."
            }
        }
    }

    fun autoFindLog() {
        val found = findLatestLog(context)
        if (found != null) {
            logContent = found.second
            logSource  = "latest.log — ${activeInstance ?: "instance"}"
            result     = null
            errorMsg   = null
        } else {
            errorMsg = if (activeInstance == null)
                "No active instance selected. Pick one on the home screen first."
            else
                "Couldn't find logs/latest.log inside \"$activeInstance\". Launch Minecraft at least once to generate a log."
        }
    }

    fun uploadLog() {
        val content = logContent ?: return
        scope.launch {
            isUploading = true
            errorMsg    = null
            val res = uploadToMclogs(content)
            isUploading = false
            if (res != null) {
                result = res
            } else {
                errorMsg = "Upload failed. Check your internet connection and try again."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Log Analyzer", fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Intro card ────────────────────────────────────────────────
            Surface(
                shape          = RoundedCornerShape(14.dp),
                color          = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier       = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier          = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🪵", fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Crash Log Analyzer",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface)
                        Text("Upload your Minecraft log to mclo.gs to get a quick analysis of what went wrong.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }

            // ── Load log section ──────────────────────────────────────────
            Text("1. Load a Log", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = green)

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Auto-find button
                Button(
                    onClick  = { autoFindLog() },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = green)
                ) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Auto-find\nlatest.log", fontSize = 12.sp,
                        lineHeight = 15.sp, color = Color.Black)
                }

                // Manual pick button
                OutlinedButton(
                    onClick  = { filePicker.launch("*/*") },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = OutlinedButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pick a\nlog file", fontSize = 12.sp, lineHeight = 15.sp)
                }
            }

            // Active instance hint
            if (activeInstance != null) {
                Text("Active instance: $activeInstance — auto-find will look for logs/latest.log inside it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
            } else {
                Text("⚠️  No active instance selected. Auto-find won't work. You can still pick a file manually.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
            }

            // Log loaded state
            AnimatedVisibility(visible = logContent != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = green.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null,
                                tint = green, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Log loaded: $logSource",
                                style      = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color      = green)
                        }
                        logContent?.let { log ->
                            Spacer(Modifier.height(6.dp))
                            Text("${log.lines().size} lines  •  ${log.length / 1024} KB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = { showRawLog = !showRawLog },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(if (showRawLog) "Hide raw log ▲" else "Preview raw log ▼",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = green)
                            }
                        }
                    }
                }
            }

            // Raw log preview (last 40 lines)
            AnimatedVisibility(
                visible = showRawLog && logContent != null,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                val preview = logContent?.lines()?.takeLast(40)?.joinToString("\n") ?: ""
                Surface(
                    shape          = RoundedCornerShape(10.dp),
                    color          = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier       = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text     = preview,
                        modifier = Modifier.padding(10.dp),
                        style    = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        lineHeight = 17.sp
                    )
                }
            }

            // Error message
            AnimatedVisibility(visible = errorMsg != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier          = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, null,
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(errorMsg ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // ── Upload section ────────────────────────────────────────────
            if (logContent != null && result == null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Text("2. Analyze", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = green)

                Button(
                    onClick  = { uploadLog() },
                    enabled  = !isUploading,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = green)
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.Black
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Uploading to mclo.gs…", color = Color.Black)
                    } else {
                        Icon(Icons.Default.CloudUpload, null,
                            modifier = Modifier.size(16.dp), tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("Upload & Analyze", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Text("Your log will be uploaded to mclo.gs (a public Minecraft log sharing service). Logs are public.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }

            // ── Results ───────────────────────────────────────────────────
            result?.let { res ->
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Text("3. Results", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = green)

                // URL card
                Surface(
                    shape          = RoundedCornerShape(12.dp),
                    color          = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier       = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Uploaded to mclo.gs",
                            style      = MaterialTheme.typography.labelSmall,
                            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(Modifier.height(4.dp))
                        Text(res.url,
                            style      = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = green,
                            fontFamily = FontFamily.Monospace)
                    }
                }

                // Re-analyze button
                OutlinedButton(
                    onClick  = {
                        result   = null
                        errorMsg = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Load a different log")
                }

                // Analysis
                val analysis = res.analysis
                if (analysis == null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("No analysis data returned by mclo.gs.",
                            modifier = Modifier.padding(14.dp),
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                } else {
                    // Problems
                    if (analysis.problems.isNotEmpty()) {
                        Text("⚠️  Problems Found",
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.error)
                        analysis.problems.forEach { problem ->
                            ProblemCard(
                                emoji   = "🔴",
                                message = problem.message,
                                count   = problem.counter,
                                entry   = problem.entry,
                                color   = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = green.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier          = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✅", fontSize = 18.sp)
                                Spacer(Modifier.width(10.dp))
                                Text("No problems detected by mclo.gs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = green,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Information
                    if (analysis.information.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("ℹ️  Information",
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        analysis.information.forEach { info ->
                            ProblemCard(
                                emoji   = "🔵",
                                message = info.message,
                                count   = info.counter,
                                entry   = info.entry,
                                color   = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Problem / Info card ───────────────────────────────────────────────────────

@Composable
private fun ProblemCard(
    emoji: String,
    message: String,
    count: Int,
    entry: McloEntry?,
    color: Color
) {
    Surface(
        shape          = RoundedCornerShape(10.dp),
        color          = color.copy(alpha = 0.07f),
        modifier       = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(emoji, fontSize = 14.sp, modifier = Modifier.padding(top = 1.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    message,
                    style      = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    modifier   = Modifier.weight(1f)
                )
                if (count > 1) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = color.copy(alpha = 0.18f)
                    ) {
                        Text("×$count",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = color,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Optional entry details
            if (entry != null) {
                Spacer(Modifier.height(6.dp))
                val details = buildList {
                    entry.time?.let   { add("Time: $it") }
                    entry.prefix?.let { add("Thread: $it") }
                    entry.lines?.let  { add("Lines ${it.from}–${it.to}") }
                }
                if (details.isNotEmpty()) {
                    Text(details.joinToString("  •  "),
                        style      = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color      = color.copy(alpha = 0.6f))
                }
            }
        }
    }
}

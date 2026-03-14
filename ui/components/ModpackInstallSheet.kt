package com.example.modrinthforandroid.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modrinthforandroid.data.InstallProgress
import com.example.modrinthforandroid.data.InstallResult
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.data.MrpackInstaller
import kotlinx.coroutines.launch

private enum class InstallState { NAMING, INSTALLING, DONE, ERROR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModpackInstallSheet(
    modpackTitle: String,      // project title from Modrinth — used as default name
    mrpackUrl: String,         // direct download URL for the .mrpack file
    iconUrl: String? = null,   // modpack icon from Modrinth — saved as icon.webp
    onDismiss: () -> Unit
) {
    val context   = LocalContext.current
    val scope     = rememberCoroutineScope()
    val rootUri   = InstanceManager.rootUri

    var installState by remember { mutableStateOf(InstallState.NAMING) }
    var instanceName by remember { mutableStateOf(modpackTitle) }
    var progress     by remember { mutableStateOf(InstallProgress("")) }
    var result       by remember { mutableStateOf<InstallResult?>(null) }
    var errorMsg     by remember { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }
    val keyboard       = LocalSoftwareKeyboardController.current

    fun startInstall() {
        if (rootUri == null) {
            errorMsg     = "No profiles folder selected. Set one on the Home screen first."
            installState = InstallState.ERROR
            return
        }
        keyboard?.hide()
        installState = InstallState.INSTALLING
        scope.launch {
            try {
                val res = MrpackInstaller.install(
                    context      = context,
                    rootUri      = rootUri,
                    instanceName = instanceName.trim().ifBlank { modpackTitle },
                    mrpackUrl    = mrpackUrl,
                    iconUrl      = iconUrl,
                    onProgress   = { progress = it }
                )
                result       = res
                installState = InstallState.DONE
            } catch (e: Exception) {
                errorMsg     = e.message ?: "Installation failed"
                installState = InstallState.ERROR
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (installState != InstallState.INSTALLING) onDismiss() },
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Header ────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (installState) {
                        InstallState.NAMING     -> "Install Modpack"
                        InstallState.INSTALLING -> "Installing…"
                        InstallState.DONE       -> "Installed! 🎉"
                        InstallState.ERROR      -> "Installation Failed"
                    },
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        ".mrpack",
                        modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ── No root URI warning ───────────────────────────────────────
            if (rootUri == null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp))
                        Text(
                            "No profiles folder selected — go to Home and set one first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // ── NAMING state ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = installState == InstallState.NAMING,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "A new instance will be created in your profiles folder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    OutlinedTextField(
                        value         = instanceName,
                        onValueChange = { instanceName = it },
                        label         = { Text("Instance name") },
                        placeholder   = { Text(modpackTitle) },
                        singleLine    = true,
                        shape         = RoundedCornerShape(12.dp),
                        modifier      = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { startInstall() }),
                        trailingIcon  = {
                            if (instanceName.isNotBlank()) {
                                IconButton(onClick = { instanceName = "" }) {
                                    Icon(Icons.Default.Close, "Clear",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                }
                            }
                        }
                    )

                    // What gets installed note
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "What gets installed",
                                style      = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color      = MaterialTheme.colorScheme.primary
                            )
                            listOf(
                                "📁  New instance folder in your profiles",
                                "⚙️  mojo_instance.json (loader + MC version)",
                                "🧩  All mods listed in the .mrpack index",
                                "⚠️  Configs/resourcepacks not included"
                            ).forEach {
                                Text(it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick  = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancel") }

                        Button(
                            onClick  = { startInstall() },
                            enabled  = rootUri != null && instanceName.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Install")
                        }
                    }
                }
            }

            // ── INSTALLING state ──────────────────────────────────────────
            AnimatedVisibility(
                visible = installState == InstallState.INSTALLING,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                Column(
                    modifier            = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (progress.total > 0) {
                        CircularProgressIndicator(
                            progress  = { progress.done.toFloat() / progress.total },
                            color     = MaterialTheme.colorScheme.primary,
                            modifier  = Modifier.size(52.dp)
                        )
                        Text(
                            "${progress.done} / ${progress.total} mods",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    } else {
                        CircularProgressIndicator(
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    Text(
                        progress.step,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }
            }

            // ── DONE state ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = installState == InstallState.DONE,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                result?.let { res ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, null,
                                        tint     = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp))
                                    Text(
                                        "Instance created!",
                                        style      = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color      = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Instance name chip
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(
                                        modifier          = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("📦", fontSize = 14.sp)
                                        Text(
                                            res.instanceName,
                                            style      = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color      = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    ResultStat("Mods installed", "${res.modsInstalled}")
                                    ResultStat("Skipped",        "${res.modsFailed.size}")
                                }
                            }
                        }

                        if (res.modsFailed.isNotEmpty()) {
                            Text(
                                "⚠️ ${res.modsFailed.size} mod${if (res.modsFailed.size != 1) "s" else ""} failed to download:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    res.modsFailed.forEach { name ->
                                        Text("• $name",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                                    }
                                }
                            }
                        }

                        // LTW renderer safeguard notice
                        if (res.rendererPatched) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            ) {
                                Row(
                                    modifier          = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("🛡️", fontSize = 20.sp)
                                    Column {
                                        Text(
                                            "Renderer set to LTW (GL4ES)",
                                            style      = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color      = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            "Sodium / Iris detected — switched from Vulkan (Zink) to OpenGL ES 3 + LTW so the pack works correctly.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            "Both apps need a restart to see the new instance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        // Restart Mojo Launcher
                        Button(
                            onClick  = {
                                val pm = context.packageManager
                                val intent = pm.getLaunchIntentForPackage("git.artdeell.mojo")
                                    ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                if (intent != null) context.startActivity(intent)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Restart Mojo Launcher")
                        }

                        // Restart Mojorinth
                        OutlinedButton(
                            onClick = {
                                val pm = context.packageManager
                                val intent = pm.getLaunchIntentForPackage(context.packageName)
                                    ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                if (intent != null) context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Restart Mojorinth")
                        }

                        OutlinedButton(
                            onClick  = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Done") }
                    }
                }
            }

            // ── ERROR state ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = installState == InstallState.ERROR,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Error, null,
                                tint     = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp))
                            Text(
                                errorMsg ?: "Unknown error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick  = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancel") }
                        Button(
                            onClick  = {
                                installState = InstallState.NAMING
                                errorMsg     = null
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Try Again") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.primary)
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
    }
}
package com.example.modrinthforandroid

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Wraps the app content and ensures MANAGE_EXTERNAL_STORAGE is granted on
 * Android 11+ before the user tries to browse or download to instance folders.
 *
 * Key fix vs the previous version: we re-check the permission every time the
 * activity RESUMES (i.e. when the user comes back from the system settings
 * screen). LaunchedEffect(Unit) only fires once on composition, so it missed
 * the grant entirely.
 */
@Composable
fun StoragePermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current

    // Below Android 11, manifest READ/WRITE_EXTERNAL_STORAGE is sufficient
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        content()
        return
    }

    var hasPermission by remember {
        mutableStateOf(Environment.isExternalStorageManager())
    }
    var showRationale by remember { mutableStateOf(!hasPermission) }

    // ── The actual fix ────────────────────────────────────────────────────
    // Re-check on every ON_RESUME. This fires when the user comes back from
    // the system "Allow all files access" settings screen, which is the only
    // reliable way to catch the grant in Compose.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = Environment.isExternalStorageManager()
                if (!hasPermission) showRationale = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Always render the app content — no blank screens
    content()

    // Overlay the dialog on top if we still need the permission
    if (!hasPermission && showRationale) {
        AlertDialog(
            onDismissRequest = { /* non-dismissable — tap Skip if you want to proceed */ },
            title = { Text("Storage Access Needed") },
            text = {
                Text(
                    "Mojorinth needs access to all files so it can browse your " +
                            "MojoLauncher instances folder and save downloaded mods, " +
                            "shaders, and resource packs directly into the right place.\n\n" +
                            "On the next screen, enable \"Allow access to manage all files\" " +
                            "for Mojorinth."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showRationale = false
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                    ).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                    // Don't set showRationale = false permanently here —
                    // ON_RESUME will re-evaluate and re-show if they didn't grant it
                }) { Text("Open Settings") }
            },
            dismissButton = {
                // Skipping hides the dialog for this session only.
                // It reappears next launch / next resume if still not granted.
                TextButton(onClick = { showRationale = false }) { Text("Skip for now") }
            }
        )
    }
}
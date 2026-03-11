package com.example.modrinthforandroid

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.example.modrinthforandroid.data.AppSettings
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.ui.navigation.ModrinthNavGraph
import com.example.modrinthforandroid.ui.theme.ModrinthTheme

class MainActivity : ComponentActivity() {

    // Runtime permission launcher for POST_NOTIFICATIONS (Android 13+)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // granted or denied — either way we continue, notifications just won't show if denied
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settings = AppSettings.get(this)

        // Restore the instances folder URI and active instance from last session
        InstanceManager.init(this)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            var theme by remember { mutableStateOf(settings.theme) }
            ModrinthTheme(theme = theme) {
                ModrinthNavGraph(
                    onThemeChange = { newTheme ->
                        theme = newTheme
                        settings.theme = newTheme
                    }
                )
            }
        }
    }
}
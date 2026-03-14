package com.example.modrinthforandroid

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modrinthforandroid.data.AppSettings
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.ui.components.MojorinthLoadingSpinner
import com.example.modrinthforandroid.ui.navigation.ModrinthNavGraph
import com.example.modrinthforandroid.ui.theme.ModrinthTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settings = AppSettings.get(this)
        InstanceManager.init(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            var theme   by remember { mutableStateOf(settings.theme) }
            var ready   by remember { mutableStateOf(false) }

            // Show splash for a minimum of 1.2s so the spin is visible,
            // then flip ready → true to show the real app
            LaunchedEffect(Unit) {
                delay(1200)
                ready = true
            }

            ModrinthTheme(theme = theme) {
                if (!ready) {
                    AppSplash()
                } else {
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
}

// ─── Full-screen splash ───────────────────────────────────────────────────────

@Composable
private fun AppSplash() {
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val labelAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.4f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "labelAlpha"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                MojorinthLoadingSpinner(size = 96.dp)
                Text(
                    "Mojorinth",
                    fontWeight = FontWeight.Black,
                    fontSize   = 22.sp,
                    color      = MaterialTheme.colorScheme.onBackground.copy(alpha = labelAlpha)
                )
            }
        }
    }
}

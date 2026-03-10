package com.example.modrinthforandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.example.modrinthforandroid.data.AppSettings
import com.example.modrinthforandroid.ui.navigation.ModrinthNavGraph
import com.example.modrinthforandroid.ui.theme.ModrinthTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settings = AppSettings.get(this)

        setContent {
            // theme is held as state so changing it in Settings triggers recomposition here
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
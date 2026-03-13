package com.example.modrinthforandroid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.modrinthforandroid.ui.screens.*

@Composable
fun ModrinthNavGraph(
    navController: NavHostController = rememberNavController(),
    onThemeChange: (String) -> Unit = {}
) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onModClick          = { navController.navigate(Screen.ModDetail.createRoute(it)) },
                onSearchClick       = { navController.navigate(Screen.ProjectTypePicker.route) },
                onDownloadsClick    = { navController.navigate(Screen.Downloads.route) },
                onSettingsClick     = { navController.navigate(Screen.Settings.route) },
                onBrowseType        = { navController.navigate(Screen.Browse.createRoute(it)) },
                onManageInstance    = { navController.navigate(Screen.InstanceManager.route) }  // ← NEW
            )
        }

        composable(
            Screen.ModDetail.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            ModDetailScreen(projectId = projectId, onBack = { navController.popBackStack() })
        }

        composable(Screen.ProjectTypePicker.route) {
            ProjectTypePickerScreen(
                onTypeSelected = { navController.navigate(Screen.Browse.createRoute(it)) },
                onBack         = { navController.popBackStack() }
            )
        }

        composable(
            Screen.Browse.route,
            arguments = listOf(navArgument("projectType") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectType = backStackEntry.arguments?.getString("projectType") ?: "mod"
            BrowseScreen(
                projectType = projectType,
                onModClick  = { navController.navigate(Screen.ModDetail.createRoute(it)) },
                onBack      = { navController.popBackStack() }
            )
        }

        composable(Screen.Downloads.route) {
            DownloadsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack        = { navController.popBackStack() },
                onThemeChange = onThemeChange
            )
        }

        // ── Instance Manager ──────────────────────────────────────────────
        composable(Screen.InstanceManager.route) {
            InstanceManagerScreen(onBack = { navController.popBackStack() })
        }
    }
}
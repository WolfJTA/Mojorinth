package com.example.modrinthforandroid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.modrinthforandroid.ui.screens.*
import com.example.modrinthforandroid.viewmodel.StatsViewModel
import com.example.modrinthforandroid.viewmodel.StatsViewModelFactory

@Composable
fun ModrinthNavGraph(
    navController: NavHostController = rememberNavController(),
    onThemeChange: (String) -> Unit = {}
) {
    val context = LocalContext.current

    // Hoist StatsViewModel here so it's created when the nav graph first
    // composes (i.e. app open) and stays alive/cached for the lifetime of
    // the nav graph — not recreated every time the user navigates to Stats.
    val statsViewModel: StatsViewModel = viewModel(
        factory = StatsViewModelFactory(context.applicationContext)
    )

    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onModClick       = { navController.navigate(Screen.ModDetail.createRoute(it)) },
                onSearchClick    = { navController.navigate(Screen.ProjectTypePicker.route) },
                onDownloadsClick = { navController.navigate(Screen.Downloads.route) },
                onSettingsClick  = { navController.navigate(Screen.Settings.route) },
                onBrowseType     = { navController.navigate(Screen.Browse.createRoute(it)) },
                onManageInstance = { navController.navigate(Screen.InstanceManager.route) },
                onLogsClick      = { navController.navigate(Screen.LogAnalyzer.route) },
                onStatsClick     = { navController.navigate(Screen.Stats.route) }
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
            val projectType = backStackEntry.arguments?.getString("projectType")
                ?: return@composable
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

        composable(Screen.InstanceManager.route) {
            InstanceManagerScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.LogAnalyzer.route) {
            LogAnalyzerScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Stats.route) {
            StatsScreen(
                onBack    = { navController.popBackStack() },
                viewModel = statsViewModel
            )
        }
    }
}
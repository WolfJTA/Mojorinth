package com.example.modrinthforandroid.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.ui.screens.*
import kotlinx.coroutines.launch

// ─── Drawer item model ────────────────────────────────────────────────────────

private data class DrawerItem(
    val label: String,
    val emoji: String,
    val icon: ImageVector,
    val route: String
)

private val DRAWER_ITEMS = listOf(
    DrawerItem("Log Analyzer",    "🪵", Icons.Default.BugReport,     Screen.LogAnalyzer.route),
    DrawerItem("Instance Manager","🎮", Icons.Default.Folder,         Screen.InstanceManager.route),
    DrawerItem("Settings",        "⚙️",  Icons.Default.Settings,       Screen.Settings.route),
)

// ─── App Drawer content ───────────────────────────────────────────────────────

@Composable
private fun AppDrawerContent(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onThemeChange: (String) -> Unit
) {
    val green = MaterialTheme.colorScheme.primary

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.background
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp, 32.dp, 24.dp, 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(green),
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 22.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Mojorinth", fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color    = MaterialTheme.colorScheme.onBackground)
                    val instanceName = InstanceManager.activeInstanceName
                    if (instanceName != null) {
                        Text(instanceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = green)
                    } else {
                        Text("No instance selected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )

        Spacer(Modifier.height(8.dp))

        // Nav items
        DRAWER_ITEMS.forEach { item ->
            val selected = currentRoute == item.route
            NavigationDrawerItem(
                label = {
                    Text(item.label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                },
                icon  = {
                    Text(item.emoji, fontSize = 18.sp)
                },
                selected = selected,
                onClick  = { onNavigate(item.route) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                colors   = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor   = green.copy(alpha = 0.15f),
                    unselectedContainerColor = MaterialTheme.colorScheme.background,
                    selectedTextColor        = green,
                    selectedIconColor        = green
                )
            )
        }

        Spacer(Modifier.weight(1f))

        // Footer
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
        Text(
            "Swipe from left edge or tap ☰ to open",
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.padding(24.dp, 12.dp, 24.dp, 24.dp)
        )
    }
}

// ─── Nav graph ────────────────────────────────────────────────────────────────

@Composable
fun ModrinthNavGraph(
    navController: NavHostController = rememberNavController(),
    onThemeChange: (String) -> Unit = {}
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope       = rememberCoroutineScope()

    // Track current route for drawer highlight
    var currentRoute by remember { mutableStateOf<String?>(null) }

    fun openDrawer() = scope.launch { drawerState.open() }
    fun closeDrawer() = scope.launch { drawerState.close() }

    fun navigateFromDrawer(route: String) {
        closeDrawer()
        // Avoid duplicating the home destination
        if (route == Screen.Home.route) return
        navController.navigate(route) {
            launchSingleTop = true
        }
        currentRoute = route
    }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            AppDrawerContent(
                currentRoute  = currentRoute,
                onNavigate    = { navigateFromDrawer(it) },
                onThemeChange = onThemeChange
            )
        }
    ) {
        NavHost(navController = navController, startDestination = Screen.Home.route) {

            composable(Screen.Home.route) {
                currentRoute = Screen.Home.route
                HomeScreen(
                    onModClick       = { navController.navigate(Screen.ModDetail.createRoute(it)) },
                    onSearchClick    = { navController.navigate(Screen.ProjectTypePicker.route) },
                    onDownloadsClick = { navController.navigate(Screen.Downloads.route) },
                    onSettingsClick  = { navController.navigate(Screen.Settings.route) },
                    onBrowseType     = { navController.navigate(Screen.Browse.createRoute(it)) },
                    onManageInstance = { navController.navigate(Screen.InstanceManager.route) },
                    onOpenDrawer     = { openDrawer() }
                )
            }

            composable(
                Screen.ModDetail.route,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { backStackEntry ->
                currentRoute = Screen.ModDetail.route
                val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
                ModDetailScreen(projectId = projectId, onBack = { navController.popBackStack() })
            }

            composable(Screen.ProjectTypePicker.route) {
                currentRoute = Screen.ProjectTypePicker.route
                ProjectTypePickerScreen(
                    onTypeSelected = { navController.navigate(Screen.Browse.createRoute(it)) },
                    onBack         = { navController.popBackStack() }
                )
            }

            composable(
                Screen.Browse.route,
                arguments = listOf(navArgument("projectType") { type = NavType.StringType })
            ) { backStackEntry ->
                currentRoute = Screen.Browse.route
                val projectType = backStackEntry.arguments?.getString("projectType") ?: "mod"
                BrowseScreen(
                    projectType = projectType,
                    onModClick  = { navController.navigate(Screen.ModDetail.createRoute(it)) },
                    onBack      = { navController.popBackStack() }
                )
            }

            composable(Screen.Downloads.route) {
                currentRoute = Screen.Downloads.route
                DownloadsScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Settings.route) {
                currentRoute = Screen.Settings.route
                SettingsScreen(
                    onBack        = { navController.popBackStack() },
                    onThemeChange = onThemeChange
                )
            }

            composable(Screen.InstanceManager.route) {
                currentRoute = Screen.InstanceManager.route
                InstanceManagerScreen(onBack = { navController.popBackStack() })
            }

            // ── Log Analyzer ──────────────────────────────────────────────
            composable(Screen.LogAnalyzer.route) {
                currentRoute = Screen.LogAnalyzer.route
                LogAnalyzerScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
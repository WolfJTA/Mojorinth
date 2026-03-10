package com.example.modrinthforandroid.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object ProjectTypePicker : Screen("project_type_picker")
    object Browse : Screen("browse/{projectType}") {
        fun createRoute(projectType: String) = "browse/$projectType"
    }
    object ModDetail : Screen("mod_detail/{projectId}") {
        fun createRoute(projectId: String) = "mod_detail/$projectId"
    }
    object Downloads : Screen("downloads")
    object Settings : Screen("settings")
}
package com.arnoldcode.glassprompt.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ViewQuilt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.arnoldcode.glassprompt.R
import kotlin.reflect.KClass

/** Tabs shown in the bottom bar / navigation rail. */
enum class TopLevelDestination(
    val route: Route,
    val routeClass: KClass<out Route>,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME(Route.Home, Route.Home::class, R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    PROJECTS(Route.Projects, Route.Projects::class, R.string.nav_projects, Icons.Outlined.Folder, Icons.Filled.Folder),
    TEMPLATES(Route.Templates, Route.Templates::class, R.string.nav_templates, Icons.AutoMirrored.Outlined.ViewQuilt, Icons.AutoMirrored.Filled.ViewQuilt),
    SETTINGS(Route.Settings, Route.Settings::class, R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
    ;

    companion object {
        fun from(destination: NavDestination?): TopLevelDestination? =
            destination?.hierarchy?.firstNotNullOfOrNull { node ->
                entries.firstOrNull { node.hasRoute(it.routeClass) }
            }
    }
}

package com.arnoldcode.glassprompt.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassBottomBar
import com.arnoldcode.glassprompt.core.designsystem.component.GlassFloatingButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassNavItem
import com.arnoldcode.glassprompt.core.designsystem.component.GlassNavigationRail
import com.arnoldcode.glassprompt.core.designsystem.glass.GlassBackdrop
import com.arnoldcode.glassprompt.core.designsystem.glass.glassContentSource
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme

/** Extra padding screens must apply so content can scroll clear of the floating bottom bar. */
val LocalShellContentPadding = staticCompositionLocalOf { PaddingValues(0.dp) }

private val BottomBarHeight = 84.dp
private val ExpandedWidthBreakpoint = 600.dp

/**
 * App root: glass backdrop, adaptive navigation chrome (bottom bar on phones, rail on
 * tablets) and the navigation graph. Chrome is only shown on top-level tabs.
 */
@Composable
fun GlassApp(
    onboardingCompleted: Boolean,
    navController: NavHostController = rememberNavController(),
) {
    // Captured once: flipping the preference after onboarding must not rebuild the graph.
    val startDestination: Route = remember { if (onboardingCompleted) Route.Home else Route.Onboarding }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = TopLevelDestination.from(backStackEntry?.destination)
    val expanded = LocalWindowInfo.current.containerDpSize.width >= ExpandedWidthBreakpoint
    val navItems = TopLevelDestination.entries.map {
        GlassNavItem(key = it.name, label = stringResource(it.labelRes), icon = it.icon, selectedIcon = it.selectedIcon)
    }
    val onSelectTab: (GlassNavItem) -> Unit = { item -> navController.navigateToTab(TopLevelDestination.valueOf(item.key)) }
    val onRecord = { navController.navigate(Route.ProjectSetup()) }

    GlassBackdrop {
        Row(Modifier.fillMaxSize()) {
            if (expanded && currentTab != null) {
                GlassNavigationRail(
                    items = navItems,
                    selectedKey = currentTab.name,
                    onSelect = onSelectTab,
                    header = { RecordButton(onRecord) },
                )
            }
            Box(Modifier.weight(1f)) {
                val showBottomBar = !expanded && currentTab != null
                val shellPadding = if (showBottomBar) {
                    val nav = WindowInsets.navigationBars.asPaddingValues()
                    PaddingValues(bottom = BottomBarHeight + nav.calculateBottomPadding())
                } else {
                    PaddingValues(0.dp)
                }
                CompositionLocalProvider(LocalShellContentPadding provides shellPadding) {
                    GlassNavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.glassContentSource(),
                    )
                }
                if (showBottomBar) {
                    GlassBottomBar(
                        items = navItems,
                        selectedKey = currentTab.name,
                        onSelect = onSelectTab,
                        centerAction = { RecordButton(onRecord, size = 52) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordButton(onClick: () -> Unit, size: Int = 56) {
    GlassFloatingButton(
        icon = Icons.Filled.FiberManualRecord,
        contentDescription = stringResource(R.string.nav_record),
        onClick = onClick,
        size = size.dp,
        tint = GlassTheme.colors.recording,
    )
}

/** Standard tab navigation: single top, state saved/restored per tab, Home as the root. */
fun NavHostController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo<Route.Home> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

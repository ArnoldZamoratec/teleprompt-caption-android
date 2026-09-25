package com.arnoldcode.glassprompt.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassAnimations
import com.arnoldcode.glassprompt.feature.common.ComingSoonScreen
import com.arnoldcode.glassprompt.feature.home.HomeScreen
import com.arnoldcode.glassprompt.feature.onboarding.OnboardingScreen
import com.arnoldcode.glassprompt.feature.projects.ProjectsScreen
import com.arnoldcode.glassprompt.feature.settings.SettingsScreen
import com.arnoldcode.glassprompt.feature.templates.TemplatesScreen

private const val ENTER_MS = 260
private const val EXIT_MS = 160

@Composable
fun GlassNavHost(
    navController: NavHostController,
    startDestination: Route,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { glassEnter() },
        exitTransition = { glassExit() },
        popEnterTransition = { glassEnter() },
        popExitTransition = { glassExit() },
    ) {
        composable<Route.Onboarding> {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Route.Home) {
                        popUpTo<Route.Onboarding> { inclusive = true }
                    }
                },
            )
        }
        composable<Route.Home> {
            HomeScreen(
                onNewProject = { navController.navigate(Route.ProjectSetup()) },
                onImportScript = { navController.navigate(Route.ProjectSetup()) },
                onOpenTeleprompter = { navController.navigate(Route.ProjectSetup()) },
                onRecord = { navController.navigate(Route.ProjectSetup()) },
                onOpenProject = { id -> navController.navigate(Route.ScriptEditor(id)) },
                onOpenVideo = { id -> navController.navigate(Route.ExportResult(id)) },
                onSeeAllProjects = { navController.navigateToTab(TopLevelDestination.PROJECTS) },
                onOpenSettings = { navController.navigateToTab(TopLevelDestination.SETTINGS) },
            )
        }
        composable<Route.Projects> { ProjectsScreen() }
        composable<Route.Templates> { TemplatesScreen() }
        composable<Route.Settings> { SettingsScreen() }

        // Flow destinations implemented in later phases.
        composable<Route.ProjectSetup> { ComingSoonScreen(onBack = navController::popBackStack) }
        composable<Route.ScriptEditor> { ComingSoonScreen(onBack = navController::popBackStack) }
        composable<Route.ExportResult> { ComingSoonScreen(onBack = navController::popBackStack) }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.glassEnter(): EnterTransition =
    fadeIn(tween(ENTER_MS, easing = GlassAnimations.EmphasizedDecelerate)) +
        scaleIn(tween(ENTER_MS, easing = GlassAnimations.EmphasizedDecelerate), initialScale = 0.98f)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.glassExit(): ExitTransition =
    fadeOut(tween(EXIT_MS, easing = GlassAnimations.EmphasizedAccelerate)) +
        scaleOut(tween(EXIT_MS, easing = GlassAnimations.EmphasizedAccelerate), targetScale = 1.01f)

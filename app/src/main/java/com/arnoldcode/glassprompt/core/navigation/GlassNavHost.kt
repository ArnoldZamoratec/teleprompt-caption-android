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
import androidx.navigation.toRoute
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassAnimations
import com.arnoldcode.glassprompt.feature.camera.CameraScreen
import com.arnoldcode.glassprompt.feature.captions.CaptionEditorScreen
import com.arnoldcode.glassprompt.feature.export.ExportResultScreen
import com.arnoldcode.glassprompt.feature.export.ExportScreen
import com.arnoldcode.glassprompt.feature.takes.TakesScreen
import com.arnoldcode.glassprompt.feature.editor.ScriptEditorScreen
import com.arnoldcode.glassprompt.feature.home.HomeScreen
import com.arnoldcode.glassprompt.feature.onboarding.OnboardingScreen
import com.arnoldcode.glassprompt.feature.projectsetup.ProjectSetupScreen
import com.arnoldcode.glassprompt.feature.projects.ProjectsScreen
import com.arnoldcode.glassprompt.feature.settings.SettingsScreen
import com.arnoldcode.glassprompt.feature.teleprompter.TeleprompterScreen
import com.arnoldcode.glassprompt.feature.templates.TemplatesScreen
import com.arnoldcode.glassprompt.feature.video.VideoReviewScreen

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
                onImportScript = { navController.navigate(Route.ProjectSetup(importScript = true)) },
                onOpenTeleprompter = { navController.navigate(Route.ProjectSetup()) },
                onRecord = { navController.navigate(Route.ProjectSetup()) },
                onOpenProject = { id -> navController.navigate(Route.ScriptEditor(id)) },
                onOpenVideo = { id -> navController.navigate(Route.ExportResult(id)) },
                onSeeAllProjects = { navController.navigateToTab(TopLevelDestination.PROJECTS) },
                onOpenSettings = { navController.navigateToTab(TopLevelDestination.SETTINGS) },
            )
        }
        composable<Route.Projects> {
            ProjectsScreen(
                onNewProject = { navController.navigate(Route.ProjectSetup()) },
                onOpenProject = { id -> navController.navigate(Route.ScriptEditor(id)) },
                onEditProject = { id -> navController.navigate(Route.ProjectSetup(projectId = id)) },
            )
        }
        composable<Route.Templates> {
            TemplatesScreen(onUseTemplate = { id -> navController.navigate(Route.ProjectSetup(templateId = id)) })
        }
        composable<Route.Settings> { SettingsScreen() }

        composable<Route.ProjectSetup> { entry ->
            val route = entry.toRoute<Route.ProjectSetup>()
            ProjectSetupScreen(
                onBack = navController::popBackStack,
                // Setup is replaced by the editor so Back from the editor returns to where the flow began.
                onCreated = { id ->
                    navController.navigate(Route.ScriptEditor(id, launchImport = route.importScript)) {
                        // By this entry's destination id: popUpTo<Route.ProjectSetup> did not match the
                        // typed route (it has optional arguments) and left the form below the editor.
                        popUpTo(entry.destination.id) { inclusive = true }
                    }
                },
                onUpdated = navController::popBackStack,
            )
        }
        composable<Route.ScriptEditor> { entry ->
            val route = entry.toRoute<Route.ScriptEditor>()
            ScriptEditorScreen(
                launchImport = route.launchImport,
                onBack = navController::popBackStack,
                onEditProject = { navController.navigate(Route.ProjectSetup(projectId = route.projectId)) },
                onOpenTakes = { navController.navigate(Route.Takes(route.projectId)) },
                onRehearse = { navController.navigate(Route.Teleprompter(route.projectId)) },
                onRecord = { navController.navigate(Route.Camera(route.projectId)) },
            )
        }

        // Flow destinations implemented in later phases.
        composable<Route.Teleprompter> { TeleprompterScreen(onBack = navController::popBackStack) }
        composable<Route.Camera> {
            CameraScreen(
                onExit = navController::popBackStack,
                onTakeSaved = { takeId -> navController.navigate(Route.VideoReview(takeId)) },
            )
        }
        composable<Route.VideoReview> {
            VideoReviewScreen(
                onBack = navController::popBackStack,
                // The camera stays below the review, so "record again" is a plain back.
                onRecordAgain = navController::popBackStack,
                onCaptions = { takeId -> navController.navigate(Route.CaptionEditor(takeId)) },
                onExport = { takeId -> navController.navigate(Route.Export(takeId)) },
            )
        }
        composable<Route.CaptionEditor> {
            CaptionEditorScreen(
                onBack = navController::popBackStack,
                onExport = { takeId -> navController.navigate(Route.Export(takeId)) },
            )
        }
        composable<Route.Takes> { entry ->
            val route = entry.toRoute<Route.Takes>()
            TakesScreen(
                onBack = navController::popBackStack,
                onOpenTake = { takeId -> navController.navigate(Route.VideoReview(takeId)) },
                onRecord = { navController.navigate(Route.Camera(route.projectId)) },
            )
        }
        composable<Route.Export> {
            ExportScreen(
                onBack = navController::popBackStack,
                // The export screen is replaced by its result, so Back returns to the editor/review.
                onExported = { exportId ->
                    navController.navigate(Route.ExportResult(exportId)) {
                        popUpTo<Route.Export> { inclusive = true }
                    }
                },
            )
        }
        composable<Route.ExportResult> {
            ExportResultScreen(
                onBack = navController::popBackStack,
                // Done: back to the project's editor if we came from it, otherwise home.
                onDone = {
                    if (!navController.popBackStack<Route.ScriptEditor>(inclusive = false)) {
                        navController.navigate(Route.Home) { popUpTo<Route.Home> { inclusive = true } }
                    }
                },
            )
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.glassEnter(): EnterTransition =
    fadeIn(tween(ENTER_MS, easing = GlassAnimations.EmphasizedDecelerate)) +
        scaleIn(tween(ENTER_MS, easing = GlassAnimations.EmphasizedDecelerate), initialScale = 0.98f)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.glassExit(): ExitTransition =
    fadeOut(tween(EXIT_MS, easing = GlassAnimations.EmphasizedAccelerate)) +
        scaleOut(tween(EXIT_MS, easing = GlassAnimations.EmphasizedAccelerate), targetScale = 1.01f)

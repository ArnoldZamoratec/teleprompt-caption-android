package com.arnoldcode.glassprompt

import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.core.navigation.GlassApp
import com.arnoldcode.glassprompt.domain.model.ThemeMode
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the system splash until preferences decide between onboarding and home,
        // so the user never sees a flash of the wrong screen.
        splash.setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val ready = state as? MainUiState.Ready ?: return@setContent
            val darkTheme = when (ready.preferences.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            SystemBarsEffect(darkTheme)
            // Honour both the in-app toggle and the system "Remove animations" accessibility setting.
            val reduceEffects = ready.preferences.reduceEffects || systemAnimationsDisabled()
            GlassTheme(darkTheme = darkTheme, reduceEffects = reduceEffects) {
                GlassApp(onboardingCompleted = ready.preferences.onboardingCompleted)
            }
        }
    }

    private fun systemAnimationsDisabled(): Boolean =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    /** Edge-to-edge with status/navigation bar icons that contrast with the app theme. */
    @Composable
    private fun SystemBarsEffect(darkTheme: Boolean) {
        DisposableEffect(darkTheme) {
            val style = if (darkTheme) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            onDispose { }
        }
    }
}

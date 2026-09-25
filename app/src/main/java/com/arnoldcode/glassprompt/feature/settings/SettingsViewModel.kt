package com.arnoldcode.glassprompt.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arnoldcode.glassprompt.domain.model.ThemeMode
import com.arnoldcode.glassprompt.domain.model.UserPreferences
import com.arnoldcode.glassprompt.domain.usecase.ObserveUserPreferencesUseCase
import com.arnoldcode.glassprompt.domain.usecase.UpdateAppearanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observePreferences: ObserveUserPreferencesUseCase,
    private val updateAppearance: UpdateAppearanceUseCase,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = observePreferences()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch { updateAppearance.setThemeMode(mode) }
    }

    fun onReduceEffectsChanged(enabled: Boolean) {
        viewModelScope.launch { updateAppearance.setReduceEffects(enabled) }
    }
}

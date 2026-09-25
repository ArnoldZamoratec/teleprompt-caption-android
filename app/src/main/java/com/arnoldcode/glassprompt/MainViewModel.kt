package com.arnoldcode.glassprompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arnoldcode.glassprompt.domain.model.UserPreferences
import com.arnoldcode.glassprompt.domain.usecase.ObserveUserPreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Ready(val preferences: UserPreferences) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    observePreferences: ObserveUserPreferencesUseCase,
) : ViewModel() {

    // Eagerly: the splash-screen condition polls this value before any UI subscribes to it,
    // so loading must not depend on a collector being present.
    val uiState: StateFlow<MainUiState> = observePreferences()
        .map<UserPreferences, MainUiState> { MainUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)
}

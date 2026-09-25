package com.arnoldcode.glassprompt.domain.usecase

import com.arnoldcode.glassprompt.domain.model.ThemeMode
import com.arnoldcode.glassprompt.domain.model.UserPreferences
import com.arnoldcode.glassprompt.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveUserPreferencesUseCase @Inject constructor(
    private val repository: UserPreferencesRepository,
) {
    operator fun invoke(): Flow<UserPreferences> = repository.preferences
}

class CompleteOnboardingUseCase @Inject constructor(
    private val repository: UserPreferencesRepository,
) {
    suspend operator fun invoke() = repository.setOnboardingCompleted(true)
}

class UpdateAppearanceUseCase @Inject constructor(
    private val repository: UserPreferencesRepository,
) {
    suspend fun setThemeMode(mode: ThemeMode) = repository.setThemeMode(mode)
    suspend fun setReduceEffects(enabled: Boolean) = repository.setReduceEffects(enabled)
}

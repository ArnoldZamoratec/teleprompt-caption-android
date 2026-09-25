package com.arnoldcode.glassprompt.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arnoldcode.glassprompt.core.common.Logger
import com.arnoldcode.glassprompt.domain.usecase.CompleteOnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val logger: Logger,
) : ViewModel() {

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    fun onStart() {
        if (_finished.value) return
        viewModelScope.launch {
            try {
                completeOnboarding()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not fatal: the user simply sees onboarding again next launch.
                logger.e(TAG, "Could not persist onboarding completion", e)
            }
            _finished.value = true
        }
    }

    private companion object {
        const val TAG = "Onboarding"
    }
}

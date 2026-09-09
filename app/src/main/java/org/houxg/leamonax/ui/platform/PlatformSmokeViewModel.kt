package org.houxg.leamonax.ui.platform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlatformSmokeUiState(
    val status: String = "Android 平台基线已就绪",
    val checks: Int = 0,
)

sealed interface PlatformSmokeEvent {
    data object RunCheck : PlatformSmokeEvent
}

@HiltViewModel
class PlatformSmokeViewModel @Inject constructor() : ViewModel() {
    private val mutableUiState = MutableStateFlow(PlatformSmokeUiState())
    val uiState: StateFlow<PlatformSmokeUiState> = mutableUiState.asStateFlow()

    fun onEvent(event: PlatformSmokeEvent) {
        when (event) {
            PlatformSmokeEvent.RunCheck -> viewModelScope.launch {
                mutableUiState.update { state -> state.copy(checks = state.checks + 1) }
            }
        }
    }
}

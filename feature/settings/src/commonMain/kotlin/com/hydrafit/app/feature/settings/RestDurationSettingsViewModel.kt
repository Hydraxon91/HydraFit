package com.hydrafit.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.settings.MAX_REST_SECONDS
import com.hydrafit.app.core.domain.settings.ObserveGlobalRestDurationUseCase
import com.hydrafit.app.core.domain.settings.SetGlobalRestDurationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RestDurationSettingsUiState(val seconds: String = "120") {
    val canSave: Boolean
        get() = seconds.toLongOrNull()?.let { it in 1L..MAX_REST_SECONDS } == true
}

class RestDurationSettingsViewModel(
    private val observeGlobalRestDuration: ObserveGlobalRestDurationUseCase,
    private val setGlobalRestDuration: SetGlobalRestDurationUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(RestDurationSettingsUiState())
    val state: StateFlow<RestDurationSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeGlobalRestDuration().collectLatest { seconds ->
                _state.update { it.copy(seconds = seconds.toString()) }
            }
        }
    }

    fun onSecondsChanged(value: String) {
        _state.update { it.copy(seconds = value.filter(Char::isDigit)) }
    }

    fun save() {
        val seconds = _state.value.seconds.toLongOrNull()
            ?.takeIf { it in 1L..MAX_REST_SECONDS }
            ?: return
        viewModelScope.launch { setGlobalRestDuration(seconds) }
    }
}

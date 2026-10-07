package com.hydrafit.app.feature.settings

import androidx.lifecycle.ViewModel
import com.hydrafit.app.core.userdata.settings.AppVersionProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AcknowledgmentsViewModel(appVersionProvider: AppVersionProvider) : ViewModel() {

    private val _state = MutableStateFlow(
        AcknowledgmentsUiState(versionName = appVersionProvider.versionName)
    )
    val state: StateFlow<AcknowledgmentsUiState> = _state.asStateFlow()
}

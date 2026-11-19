package com.hydrafit.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.userdata.settings.AppColorMode
import com.hydrafit.app.core.userdata.settings.AppThemeId
import com.hydrafit.app.core.userdata.settings.AppThemePreference
import com.hydrafit.app.core.userdata.settings.AppThemePreferenceStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppearanceSettingsViewModel(private val store: AppThemePreferenceStore) : ViewModel() {
    /** The live choice; the app shell reads the same flow, so a pick re-themes the app at once. */
    val preference: StateFlow<AppThemePreference> = store.preference

    fun onThemeSelected(theme: AppThemeId) {
        viewModelScope.launch { store.setTheme(theme) }
    }

    fun onColorModeSelected(mode: AppColorMode) {
        viewModelScope.launch { store.setColorMode(mode) }
    }
}

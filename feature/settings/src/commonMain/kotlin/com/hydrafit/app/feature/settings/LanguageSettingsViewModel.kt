package com.hydrafit.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.userdata.settings.AppLanguage
import com.hydrafit.app.core.userdata.settings.AppLanguageStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LanguageSettingsViewModel(private val store: AppLanguageStore) : ViewModel() {
    /** The live choice; the platform shell watches the same stored value and applies it. */
    val language: StateFlow<AppLanguage> = store.language

    fun onLanguageSelected(language: AppLanguage) {
        viewModelScope.launch { store.setLanguage(language) }
    }
}

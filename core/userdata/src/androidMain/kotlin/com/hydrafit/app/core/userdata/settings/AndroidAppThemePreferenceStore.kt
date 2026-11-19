package com.hydrafit.app.core.userdata.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Device-local theme choice in its own small preferences file. The value is read once at
 * construction, so the app is never drawn in the default look and then switched. An unknown or
 * missing key reads as the default, which also covers a value written by a newer build.
 */
class AndroidAppThemePreferenceStore(context: Context) : AppThemePreferenceStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    override val preference: StateFlow<AppThemePreference> = state.asStateFlow()

    override suspend fun setTheme(theme: AppThemeId) {
        write { putString(KEY_THEME, theme.name) }
        state.value = state.value.copy(theme = theme)
    }

    override suspend fun setColorMode(mode: AppColorMode) {
        write { putString(KEY_COLOR_MODE, mode.name) }
        state.value = state.value.copy(colorMode = mode)
    }

    private suspend fun write(change: android.content.SharedPreferences.Editor.() -> Unit) {
        withContext(Dispatchers.IO) { preferences.edit().apply(change).commit() }
    }

    private fun read(): AppThemePreference = AppThemePreference(
        theme = AppThemeId.entries.firstOrNull { it.name == preferences.getString(KEY_THEME, null) }
            ?: AppThemeId.QUIET_STRENGTH,
        colorMode = AppColorMode.entries
            .firstOrNull { it.name == preferences.getString(KEY_COLOR_MODE, null) }
            ?: AppColorMode.SYSTEM
    )

    private companion object {
        const val PREFS_NAME = "hydrafit_appearance"
        const val KEY_THEME = "theme"
        const val KEY_COLOR_MODE = "color_mode"
    }
}

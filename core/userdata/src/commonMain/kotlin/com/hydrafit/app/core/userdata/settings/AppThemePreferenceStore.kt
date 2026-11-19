package com.hydrafit.app.core.userdata.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The selectable colour themes. [QUIET_STRENGTH] is the original look and the default; the stored
 * [name] is the persisted key, so entries are only ever added, never renamed.
 */
enum class AppThemeId {
    QUIET_STRENGTH,
    MATA_FIRE,
    MATA_WATER,
    MATA_AIR,
    MATA_EARTH,
    MATA_STONE,
    MATA_ICE,
    MAKUTA,
    GOLD_NUVA
}

/** Whether a theme follows the device's light or dark setting or is pinned to one of them. */
enum class AppColorMode { SYSTEM, LIGHT, DARK }

data class AppThemePreference(
    val theme: AppThemeId = AppThemeId.QUIET_STRENGTH,
    val colorMode: AppColorMode = AppColorMode.SYSTEM
)

/**
 * The device-local appearance choice. It is a per-install display preference, deliberately kept
 * out of the database and backup payload so it needs no schema change; a restored backup starts
 * on the default look. [preference] always has a value, so the first frame is already themed.
 */
interface AppThemePreferenceStore {
    val preference: StateFlow<AppThemePreference>

    suspend fun setTheme(theme: AppThemeId)

    suspend fun setColorMode(mode: AppColorMode)
}

/** Holds the choice in memory only: used where no platform store exists and in tests. */
class InMemoryAppThemePreferenceStore(initial: AppThemePreference = AppThemePreference()) :
    AppThemePreferenceStore {
    private val state = MutableStateFlow(initial)
    override val preference: StateFlow<AppThemePreference> = state.asStateFlow()

    override suspend fun setTheme(theme: AppThemeId) {
        state.update { it.copy(theme = theme) }
    }

    override suspend fun setColorMode(mode: AppColorMode) {
        state.update { it.copy(colorMode = mode) }
    }
}

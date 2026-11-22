package com.hydrafit.app.core.userdata.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The languages the app is translated into. [SYSTEM] follows the device; every other entry pins
 * the app to one language whatever the device uses. The stored [name] is the persisted key, so
 * entries are only ever added, never renamed. [tag] is the BCP 47 language tag, null for [SYSTEM].
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    GERMAN("de"),
    CZECH("cs"),
    HUNGARIAN("hu");

    companion object {
        /** An unknown or missing key reads as [SYSTEM], which also covers a value from a newer build. */
        fun fromKey(key: String?): AppLanguage = entries.firstOrNull { it.name == key } ?: SYSTEM
    }
}

/**
 * The device-local language choice. Like the theme it is a per-install display preference, kept
 * out of the database and backup payload, so a restored backup never changes the language.
 * [language] always has a value, so the first frame is already in the chosen language.
 */
interface AppLanguageStore {
    val language: StateFlow<AppLanguage>

    suspend fun setLanguage(language: AppLanguage)
}

/** Holds the choice in memory only: used where no platform store exists and in tests. */
class InMemoryAppLanguageStore(initial: AppLanguage = AppLanguage.SYSTEM) : AppLanguageStore {
    private val state = MutableStateFlow(initial)
    override val language: StateFlow<AppLanguage> = state.asStateFlow()

    override suspend fun setLanguage(language: AppLanguage) {
        state.value = language
    }
}

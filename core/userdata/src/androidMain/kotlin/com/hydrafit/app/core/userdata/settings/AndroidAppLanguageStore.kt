package com.hydrafit.app.core.userdata.settings

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Device-local language choice in its own small preferences file. It is read once at construction
 * and, through [AndroidAppLanguage], synchronously before the first activity is drawn, so the app
 * is never shown in the device language and then switched.
 */
class AndroidAppLanguageStore(context: Context) : AppLanguageStore {
    private val preferences = AndroidAppLanguage.preferences(context)
    private val state = MutableStateFlow(AndroidAppLanguage.read(preferences))
    override val language: StateFlow<AppLanguage> = state.asStateFlow()

    override suspend fun setLanguage(language: AppLanguage) {
        withContext(Dispatchers.IO) {
            preferences.edit().putString(AndroidAppLanguage.KEY_LANGUAGE, language.name).commit()
        }
        state.value = language
    }
}

/**
 * Applies the saved choice to an activity's context. Android reads the locale from the context's
 * configuration, and [Locale.getDefault] feeds number formatting, so both are set together.
 */
object AndroidAppLanguage {
    internal const val KEY_LANGUAGE = "language"
    private const val PREFS_NAME = "hydrafit_language"

    internal fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    internal fun read(preferences: SharedPreferences): AppLanguage =
        AppLanguage.fromKey(preferences.getString(KEY_LANGUAGE, null))

    /**
     * Returns [base] unchanged for [AppLanguage.SYSTEM], and a context in the saved language
     * otherwise. Call it from `attachBaseContext`, where [base] still carries the device locale.
     */
    fun wrap(base: Context): Context {
        val saved = read(preferences(base))
        val deviceLocale = base.resources.configuration.locales[0]
        val tag = saved.tag
        if (tag == null) {
            // A pinned language sets the process default; going back to the device language has to
            // restore it, or number formats would keep the old language until the process dies.
            Locale.setDefault(deviceLocale)
            return base
        }
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = android.content.res.Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        return base.createConfigurationContext(configuration)
    }

    /**
     * Calls [onChange] whenever the saved language changes. Keep the returned listener's
     * [AutoCloseable] and close it with the activity.
     */
    fun observe(context: Context, onChange: () -> Unit): AutoCloseable {
        val preferences = preferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LANGUAGE) onChange()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return AutoCloseable { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

package com.hydrafit.app

import android.content.Context
import com.hydrafit.app.core.userdata.settings.AndroidAppLanguage

/**
 * The activity's hook into the language choice. `androidApp` does not depend on `core:userdata`,
 * so these two calls are the whole surface it needs.
 */
object AppLanguageSupport {
    /** For `attachBaseContext`: the context in the saved language, or [base] on the device language. */
    fun wrap(base: Context): Context = AndroidAppLanguage.wrap(base)

    /** Calls [onChange] after the saved language changes; close the result when the activity ends. */
    fun observe(context: Context, onChange: () -> Unit): AutoCloseable =
        AndroidAppLanguage.observe(context, onChange)
}

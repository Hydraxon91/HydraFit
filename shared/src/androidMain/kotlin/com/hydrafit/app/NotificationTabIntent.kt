package com.hydrafit.app

import android.content.Intent
import com.hydrafit.app.core.navigation.OPEN_TAB_EXTRA

/**
 * The top-level tab name a notification tap requested, or null for an ordinary launch.
 *
 * The extra is removed as it is read, so a recreated activity or a repeated delivery of the same
 * intent cannot request the tab again; the shell keeps the screen and saved state it already has.
 */
fun Intent.consumeNotificationTabName(): String? {
    val tabName = getStringExtra(OPEN_TAB_EXTRA) ?: return null
    removeExtra(OPEN_TAB_EXTRA)
    return tabName
}

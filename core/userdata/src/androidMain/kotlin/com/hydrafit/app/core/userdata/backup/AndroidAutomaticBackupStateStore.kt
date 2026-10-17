package com.hydrafit.app.core.userdata.backup

import android.content.Context
import com.hydrafit.app.core.domain.backup.AutomaticBackupState
import com.hydrafit.app.core.domain.backup.AutomaticBackupStateJson
import com.hydrafit.app.core.domain.backup.AutomaticBackupStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Device-local automatic-backup state in its own preferences file: this install's folder grant,
 * snapshot ledger and last outcome. It is deliberately not part of any backup payload, and a
 * corrupt or unknown-version document reads as the disabled default (fail-safe: nothing is ever
 * deleted from a ledger that cannot be read).
 */
class AndroidAutomaticBackupStateStore(context: Context) : AutomaticBackupStateStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun load(): AutomaticBackupState = withContext(Dispatchers.IO) {
        preferences.getString(KEY_STATE, null)
            ?.let { AutomaticBackupStateJson.decodeOrNull(it) }
            ?: AutomaticBackupState()
    }

    override suspend fun save(state: AutomaticBackupState) {
        withContext(Dispatchers.IO) {
            preferences.edit()
                .putString(KEY_STATE, AutomaticBackupStateJson.encode(state))
                .apply()
        }
    }

    private companion object {
        const val PREFS_NAME = "hydrafit_automatic_backup"
        const val KEY_STATE = "automatic_backup_state"
    }
}

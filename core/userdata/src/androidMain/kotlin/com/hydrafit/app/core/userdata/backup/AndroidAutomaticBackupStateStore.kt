package com.hydrafit.app.core.userdata.backup

import android.content.Context
import com.hydrafit.app.core.domain.backup.AutomaticBackupState
import com.hydrafit.app.core.domain.backup.AutomaticBackupStateJson
import com.hydrafit.app.core.domain.backup.AutomaticBackupStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Device-local automatic-backup state in its own preferences file: this install's folder grant,
 * snapshot ledger and last outcome. It is deliberately not part of any backup payload, and a
 * corrupt or unknown-version document reads as the disabled default (fail-safe: nothing is ever
 * deleted from a ledger that cannot be read).
 *
 * A process-lifetime lock serializes overlapping writers (the scheduled run and the settings
 * screen), and each update is committed synchronously so a ledger record survives a process kill —
 * the pending write verification depends on that record being on disk before the read-back.
 */
class AndroidAutomaticBackupStateStore(context: Context) : AutomaticBackupStateStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Mutex()

    override suspend fun load(): AutomaticBackupState = withContext(Dispatchers.IO) {
        read()
    }

    override suspend fun update(
        change: (AutomaticBackupState) -> AutomaticBackupState
    ): AutomaticBackupState = lock.withLock {
        withContext(Dispatchers.IO) {
            val next = change(read())
            preferences.edit()
                .putString(KEY_STATE, AutomaticBackupStateJson.encode(next))
                .commit()
            next
        }
    }

    private fun read(): AutomaticBackupState = preferences.getString(KEY_STATE, null)
        ?.let { AutomaticBackupStateJson.decodeOrNull(it) }
        ?: AutomaticBackupState()

    private companion object {
        const val PREFS_NAME = "hydrafit_automatic_backup"
        const val KEY_STATE = "automatic_backup_state"
    }
}

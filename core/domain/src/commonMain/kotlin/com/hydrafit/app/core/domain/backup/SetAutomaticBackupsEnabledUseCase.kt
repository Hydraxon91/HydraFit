package com.hydrafit.app.core.domain.backup

/**
 * Turns automatic backups on or off and keeps the scheduled job in line with the stored state.
 * Enabling requires a currently usable folder, so automation cannot be switched on against a
 * provider whose retention contract cannot be honored. Disabling stops future runs, cancels the
 * scheduled job and keeps every existing snapshot in place.
 */
class SetAutomaticBackupsEnabledUseCase(
    private val folderStore: BackupFolderStore,
    private val stateStore: AutomaticBackupStateStore,
    private val scheduler: AutomaticBackupScheduler
) {
    suspend operator fun invoke(enabled: Boolean): AutomaticBackupEnableResult {
        val state = stateStore.load()
        if (!enabled) {
            if (state.enabled) stateStore.save(state.copy(enabled = false))
            scheduler.cancel()
            return AutomaticBackupEnableResult.Disabled
        }
        val handle = state.folderHandle ?: return AutomaticBackupEnableResult.FolderNotConfigured
        val capabilities = folderStore.capabilities(handle)
        if (capabilities is FolderCapabilities.Unavailable) {
            return AutomaticBackupEnableResult.FolderUnavailable(capabilities.reason)
        }
        if (!state.enabled) stateStore.save(state.copy(enabled = true))
        scheduler.schedule()
        return AutomaticBackupEnableResult.Enabled
    }
}

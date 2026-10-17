package com.hydrafit.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.backup.AutomaticBackupEnableResult
import com.hydrafit.app.core.domain.backup.AutomaticBackupOutcome
import com.hydrafit.app.core.domain.backup.AutomaticBackupStateStore
import com.hydrafit.app.core.domain.backup.BackupFolderStore
import com.hydrafit.app.core.domain.backup.ConfigureAutomaticBackupFolderUseCase
import com.hydrafit.app.core.domain.backup.FolderCapabilities
import com.hydrafit.app.core.domain.backup.RunAutomaticBackupUseCase
import com.hydrafit.app.core.domain.backup.SetAutomaticBackupsEnabledUseCase
import com.hydrafit.app.core.userdata.settings.AppVersionProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the automatic-backup settings surface: folder choice, the enable switch and an explicit
 * "Back up now". Every action re-reads the stored state afterwards so the screen reflects what was
 * actually persisted, and a busy action ignores a second request. Cancellation is rethrown, never
 * reported as a failure. Folder and enable refusals are ordinary results, not exceptions; an
 * unexpected throwable clears the busy flag without claiming an outcome.
 */
class AutomaticBackupSettingsViewModel(
    private val stateStore: AutomaticBackupStateStore,
    private val folderStore: BackupFolderStore,
    private val configureFolder: ConfigureAutomaticBackupFolderUseCase,
    private val enableBackups: SetAutomaticBackupsEnabledUseCase,
    private val runBackup: RunAutomaticBackupUseCase,
    private val appVersionProvider: AppVersionProvider
) : ViewModel() {

    private val _state = MutableStateFlow(
        AutomaticBackupUiState(isSupported = folderStore.isSupported)
    )
    val state: StateFlow<AutomaticBackupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { applyStoredState(status = _state.value.status) }
    }

    /** Re-reads the stored state, keeping the current status line. */
    fun refresh() {
        viewModelScope.launch { applyStoredState(status = _state.value.status) }
    }

    /**
     * Stores the folder the platform picker returned. A usable folder clears the status; an unusable
     * one reports its reason. Configuration decides usability, so a refused persistable grant
     * surfaces here rather than succeeding silently.
     */
    fun chooseFolder(handle: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, status = null) }
        viewModelScope.launch {
            try {
                val capabilities = configureFolder(handle)
                val status = (capabilities as? FolderCapabilities.Unavailable)?.let {
                    AutomaticBackupStatus.FolderUnavailable(it.reason)
                }
                applyStoredState(status)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** Turns automation on or off and reports the enable result, which a refusal leaves unapplied. */
    fun setEnabled(enabled: Boolean) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, status = null) }
        viewModelScope.launch {
            try {
                val result = enableBackups(enabled)
                applyStoredState(result.toStatus())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** Runs one forced snapshot attempt and always refreshes the stored state afterwards. */
    fun runNow() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, status = null) }
        viewModelScope.launch {
            try {
                val outcome = runBackup(appVersion = appVersionProvider.versionName, force = true)
                val status = when (outcome) {
                    is AutomaticBackupOutcome.Created -> AutomaticBackupStatus.RunSucceeded
                    is AutomaticBackupOutcome.Failed ->
                        AutomaticBackupStatus.RunFailed(outcome.kind)

                    AutomaticBackupOutcome.Disabled,
                    AutomaticBackupOutcome.NotDue -> null
                }
                applyStoredState(status)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun dismissStatus() {
        _state.update { it.copy(status = null) }
    }

    private suspend fun applyStoredState(status: AutomaticBackupStatus?) {
        val stored = stateStore.load()
        _state.update {
            it.copy(
                enabled = stored.enabled,
                folderConfigured = stored.folderHandle != null,
                hasSucceededBefore = stored.lastSuccessMillis != null,
                lastFailure = stored.lastFailure?.kind,
                keptDocuments = stored.documents.count { document -> document.verified },
                busy = false,
                status = status
            )
        }
    }

    private fun AutomaticBackupEnableResult.toStatus(): AutomaticBackupStatus = when (this) {
        AutomaticBackupEnableResult.Enabled -> AutomaticBackupStatus.Enabled
        AutomaticBackupEnableResult.Disabled -> AutomaticBackupStatus.Disabled
        AutomaticBackupEnableResult.FolderNotConfigured ->
            AutomaticBackupStatus.FolderNotConfigured

        is AutomaticBackupEnableResult.FolderUnavailable ->
            AutomaticBackupStatus.FolderUnavailable(reason)
    }
}

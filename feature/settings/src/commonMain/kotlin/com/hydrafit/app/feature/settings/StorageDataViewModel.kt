package com.hydrafit.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.backup.AutomaticBackupFailureKind
import com.hydrafit.app.core.domain.backup.AutomaticBackupOutcome
import com.hydrafit.app.core.domain.backup.BackupException
import com.hydrafit.app.core.domain.backup.BackupFailure
import com.hydrafit.app.core.domain.backup.ClearAutomaticBackupFilesUseCase
import com.hydrafit.app.core.domain.backup.RunAutomaticBackupUseCase
import com.hydrafit.app.core.domain.storage.ClearDataCategoryUseCase
import com.hydrafit.app.core.domain.storage.DataCategory
import com.hydrafit.app.core.domain.storage.ExerciseDataClearException
import com.hydrafit.app.core.domain.storage.StorageUsage
import com.hydrafit.app.core.domain.storage.StorageUsageUseCase
import com.hydrafit.app.core.userdata.settings.AppVersionProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the Settings storage surface: it loads the reported usage, opens a confirmation for a
 * requested removal and only executes that removal on [confirm]. A database category goes through
 * [ClearDataCategoryUseCase] and the recorded snapshots through
 * [ClearAutomaticBackupFilesUseCase]. Every finished removal reloads the usage, so the counts on
 * screen reflect what is actually stored. Cancellation is rethrown, never reported as a failure.
 */
class StorageDataViewModel(
    private val storageUsage: StorageUsageUseCase,
    private val clearCategory: ClearDataCategoryUseCase,
    private val clearBackupFiles: ClearAutomaticBackupFilesUseCase,
    private val runBackup: RunAutomaticBackupUseCase,
    private val appVersionProvider: AppVersionProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow(StorageDataUiState())
    val state: StateFlow<StorageDataUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Reloads the reported usage and the model-file state without touching any pending request. */
    fun refresh() {
        viewModelScope.launch { loadUsage() }
    }

    /** Opens the confirmation for [category]; nothing is removed until [confirm] runs. */
    fun request(category: DataCategory) {
        if (_state.value.inProgress) return
        _state.update {
            it.copy(
                pending = category,
                typedConfirmation = "",
                backupStatus = null,
                status = null
            )
        }
    }

    /** Records the text typed into the ALL_DATA confirmation field. */
    fun typedConfirmation(value: String) {
        _state.update { it.copy(typedConfirmation = value) }
    }

    /** Closes the confirmation without removing anything. */
    fun cancel() {
        _state.update { it.copy(pending = null, typedConfirmation = "", backupStatus = null) }
    }

    /**
     * Executes the confirmed removal. The ALL_DATA category additionally requires the typed word,
     * so an empty or incorrect entry leaves everything untouched. A finished removal closes the
     * dialog, reports its outcome and reloads the usage.
     */
    fun confirm() {
        val category = _state.value.pending ?: return
        if (_state.value.inProgress) return
        if (category == DataCategory.ALL_DATA && !isAllDataConfirmed()) return
        _state.update { it.copy(inProgress = true, status = null) }
        viewModelScope.launch {
            try {
                val status = withContext(dispatcher) { remove(category) }
                _state.update {
                    it.copy(
                        pending = null,
                        typedConfirmation = "",
                        backupStatus = null,
                        inProgress = false,
                        status = status
                    )
                }
                loadUsage()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (refusal: ExerciseDataClearException) {
                _state.update {
                    it.copy(
                        pending = null,
                        typedConfirmation = "",
                        inProgress = false,
                        status = StorageDataStatus.CategoryBlocked(refusal.failure)
                    )
                }
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        pending = null,
                        typedConfirmation = "",
                        inProgress = false,
                        status = StorageDataStatus.Failed(throwable.toFailure())
                    )
                }
            }
        }
    }

    /**
     * Runs an explicit "Back up now" from a removal dialog and keeps the dialog open, so the user
     * sees the outcome before deciding whether to remove anything.
     */
    fun backUpNow() {
        if (_state.value.inProgress) return
        _state.update { it.copy(inProgress = true, backupStatus = null) }
        viewModelScope.launch {
            try {
                val outcome = runBackup(appVersion = appVersionProvider.versionName, force = true)
                val status = when (outcome) {
                    is AutomaticBackupOutcome.Created -> StorageBackupStatus.Succeeded
                    is AutomaticBackupOutcome.Failed -> StorageBackupStatus.Failed(outcome.kind)
                    AutomaticBackupOutcome.Disabled -> StorageBackupStatus.Disabled
                    AutomaticBackupOutcome.NotDue -> StorageBackupStatus.Succeeded
                }
                _state.update { it.copy(inProgress = false, backupStatus = status) }
                loadUsage()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        inProgress = false,
                        backupStatus = StorageBackupStatus.Failed(
                            AutomaticBackupFailureKind.UNKNOWN
                        )
                    )
                }
            }
        }
    }

    private suspend fun remove(category: DataCategory): StorageDataStatus = when (category) {
        DataCategory.BACKUP_FILES ->
            StorageDataStatus.BackupFilesRemoved(clearBackupFiles())

        else -> {
            clearCategory(category)
            StorageDataStatus.CategoryRemoved(category)
        }
    }

    private suspend fun loadUsage() {
        try {
            val usage: StorageUsage = withContext(dispatcher) { storageUsage() }
            _state.update { it.copy(usage = usage) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            _state.update { it.copy(usage = null) }
        }
    }

    private fun isAllDataConfirmed(): Boolean =
        _state.value.typedConfirmation.trim().equals(ALL_DATA_CONFIRMATION, ignoreCase = true)

    private fun Throwable.toFailure(): BackupFailure? = (this as? BackupException)?.failure

    companion object {
        /**
         * The word the user must type to confirm removing all app data. It mirrors the display
         * string `settings_storage_confirm_word`, which exists because a ViewModel cannot read
         * Compose resources.
         */
        const val ALL_DATA_CONFIRMATION: String = "REMOVE"
    }
}

package com.hydrafit.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.backup.BackupEncryption
import com.hydrafit.app.core.domain.backup.BackupException
import com.hydrafit.app.core.domain.backup.BackupFailure
import com.hydrafit.app.core.domain.backup.BackupFile
import com.hydrafit.app.core.domain.backup.ExportBackupUseCase
import com.hydrafit.app.core.domain.backup.PreviewBackupUseCase
import com.hydrafit.app.core.domain.backup.PreviewMergeUseCase
import com.hydrafit.app.core.domain.backup.RestoreBackupUseCase
import com.hydrafit.app.core.domain.cardio.CardioCsvWriter
import com.hydrafit.app.core.domain.cardio.CardioRepository
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.transfer.ExportWorkoutHistoryCsvUseCase
import com.hydrafit.app.core.userdata.backup.BackupFileStore
import com.hydrafit.app.core.userdata.settings.AppVersionProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Settings backup flow: export writes a snapshot to a chosen document, import reads and
 * validates a file into a preview, and restore stages the validated file for the next process start.
 * A valid file is previewed for both a whole-file replacement and an add-only merge, so the user can
 * choose either action before anything is staged. An export may seal the payload with a user
 * password, and an import of an encrypted envelope holds the ciphertext until the password opens it.
 * A failure from a previous startup apply is surfaced once when the screen opens. Cancellation is
 * rethrown, never reported as an I/O failure.
 */
class BackupViewModel(
    private val exportBackup: ExportBackupUseCase,
    private val previewBackup: PreviewBackupUseCase,
    private val previewMerge: PreviewMergeUseCase,
    private val restoreBackup: RestoreBackupUseCase,
    private val fileStore: BackupFileStore,
    private val appVersionProvider: AppVersionProvider,
    private val timeProvider: TimeProvider,
    private val exportWorkoutHistoryCsv: ExportWorkoutHistoryCsvUseCase,
    private val cardioRepository: CardioRepository,
    private val encryption: BackupEncryption
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState(isSupported = fileStore.isSupported))
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** The imported ciphertext awaiting its password; never placed in the (observable) UI state. */
    private var pendingEncryptedText: String? = null

    init {
        viewModelScope.launch {
            restoreBackup.consumePendingApplyError()?.let { failure ->
                _state.update { it.copy(status = BackupStatus.Failed(failure)) }
            }
        }
    }

    /**
     * Exports a snapshot to the chosen document. Plaintext is the default; [encrypted] seals the
     * payload with [password] instead, and an encrypted request without a password fails rather than
     * silently writing readable plaintext.
     */
    fun export(handle: String, encrypted: Boolean = false, password: String? = null) {
        if (_state.value.inProgress) return
        _state.update { it.copy(inProgress = true, status = null) }
        viewModelScope.launch {
            try {
                val text = exportBackup(appVersionProvider.versionName, timeProvider.nowMillis())
                val payload = if (encrypted) {
                    val secret = password
                        ?: throw BackupException(BackupFailure.ENCRYPTION_FAILED, "no password")
                    encryption.sealWithPassword(text, secret.toCharArray())
                } else {
                    text
                }
                fileStore.write(handle, payload)
                _state.update { it.copy(inProgress = false, status = BackupStatus.Exported) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(inProgress = false, status = BackupStatus.Failed(throwable.toFailure()))
                }
            }
        }
    }

    /**
     * Writes the recorded training history as an analysis CSV to the chosen document. This is not a
     * backup: the file is not restorable, so success is reported through [BackupStatus.HistoryCsvExported]
     * rather than [BackupStatus.Exported]. A failure before the write leaves the document untouched.
     */
    fun exportCsv(handle: String) {
        if (_state.value.inProgress) return
        _state.update { it.copy(inProgress = true, status = null) }
        viewModelScope.launch {
            try {
                val text = exportWorkoutHistoryCsv()
                fileStore.write(handle, text)
                _state.update {
                    it.copy(inProgress = false, status = BackupStatus.HistoryCsvExported)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(inProgress = false, status = BackupStatus.Failed(throwable.toFailure()))
                }
            }
        }
    }

    /** Exports cardio separately from resistance-set history; this file is not a backup. */
    fun exportCardioCsv(handle: String) {
        if (_state.value.inProgress) return
        _state.update { it.copy(inProgress = true, status = null) }
        viewModelScope.launch {
            try {
                val text = CardioCsvWriter.write(cardioRepository.all())
                fileStore.write(handle, text)
                _state.update {
                    it.copy(inProgress = false, status = BackupStatus.CardioCsvExported)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(inProgress = false, status = BackupStatus.Failed(throwable.toFailure()))
                }
            }
        }
    }

    /**
     * Reads a file and either previews it or, when it is an encrypted envelope, holds it until
     * [unlockImport] supplies the password. The ciphertext is never exposed in the UI state.
     */
    fun `import`(handle: String) {
        if (_state.value.inProgress) return
        _state.update {
            it.copy(inProgress = true, status = null, preview = null, mergePreview = null)
        }
        viewModelScope.launch {
            try {
                val text = fileStore.read(handle)
                if (encryption.looksEncrypted(text)) {
                    pendingEncryptedText = text
                    _state.update { it.copy(inProgress = false, unlocking = true) }
                } else {
                    val preview = previewBackup(text).toPreview(text)
                    val mergePreview = previewMerge(text).toPreviewUi()
                    _state.update {
                        it.copy(inProgress = false, preview = preview, mergePreview = mergePreview)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(inProgress = false, status = BackupStatus.Failed(throwable.toFailure()))
                }
            }
        }
    }

    /**
     * Opens the held ciphertext with [password] and previews the decoded backup. A wrong password or
     * damaged envelope clears the held text and reports it; nothing is staged.
     */
    fun unlockImport(password: String) {
        val text = pendingEncryptedText ?: return
        if (_state.value.inProgress) return
        _state.update { it.copy(inProgress = true, status = null) }
        viewModelScope.launch {
            try {
                val plain = encryption.unsealWithPassword(text, password.toCharArray())
                pendingEncryptedText = null
                val preview = previewBackup(plain).toPreview(plain)
                val mergePreview = previewMerge(plain).toPreviewUi()
                _state.update {
                    it.copy(
                        inProgress = false,
                        unlocking = false,
                        preview = preview,
                        mergePreview = mergePreview
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                pendingEncryptedText = null
                _state.update {
                    it.copy(
                        inProgress = false,
                        unlocking = false,
                        preview = null,
                        mergePreview = null,
                        status = BackupStatus.Failed(throwable.toFailure())
                    )
                }
            }
        }
    }

    /** Discards the held ciphertext and closes the unlock prompt without staging anything. */
    fun cancelUnlock() {
        pendingEncryptedText = null
        _state.update { it.copy(unlocking = false) }
    }

    fun confirmRestore() {
        val preview = _state.value.preview ?: return
        if (_state.value.inProgress) return
        _state.update { it.copy(inProgress = true, status = null) }
        viewModelScope.launch {
            try {
                restoreBackup(
                    text = preview.pendingText,
                    appVersion = appVersionProvider.versionName,
                    stagedAtMillis = timeProvider.nowMillis()
                )
                _state.update {
                    it.copy(
                        inProgress = false,
                        preview = null,
                        mergePreview = null,
                        status = BackupStatus.RestoreStaged
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(inProgress = false, status = BackupStatus.Failed(throwable.toFailure()))
                }
            }
        }
    }

    /**
     * Stages the previewed file as an add-only merge of its records. A selection that would add
     * nothing is never staged; the dialog disables this action and reports it as empty.
     */
    fun confirmMerge() {
        val preview = _state.value.preview ?: return
        val mergePreview = _state.value.mergePreview ?: return
        if (_state.value.inProgress || mergePreview.isEmpty) return
        _state.update { it.copy(inProgress = true, status = null) }
        viewModelScope.launch {
            try {
                restoreBackup.stageMerge(
                    text = preview.pendingText,
                    appVersion = appVersionProvider.versionName,
                    stagedAtMillis = timeProvider.nowMillis()
                )
                _state.update {
                    it.copy(
                        inProgress = false,
                        preview = null,
                        mergePreview = null,
                        status = BackupStatus.MergeStaged
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(inProgress = false, status = BackupStatus.Failed(throwable.toFailure()))
                }
            }
        }
    }

    fun cancelPreview() {
        _state.update { it.copy(preview = null, mergePreview = null) }
    }

    fun dismissStatus() {
        _state.update { it.copy(status = null) }
    }

    private fun Throwable.toFailure(): BackupFailure =
        (this as? BackupException)?.failure ?: BackupFailure.IO

    private fun BackupFile.toPreview(text: String): BackupPreviewUi {
        val records = customExercises.size + exerciseOverrides.size + equipment.size +
            selectedEquipment.size + workoutSets.size + workoutSessions.size + plans.size +
            planDays.size + planEntries.size + volumeExplanations.size +
            volumeExplanationStates.size + planTargetExplanations.size +
            planTargetExplanationStates.size + entryTargetExplanations.size + routines.size +
            routineWorkouts.size +
            routineEntries.size + activations.size + activationWorkouts.size +
            activationEntries.size + occurrences.size + occurrenceEntries.size +
            personalRecords.size + preferences.size + exclusions.size
        return BackupPreviewUi(
            appVersion = appVersion,
            workoutSets = workoutSets.size,
            customExercises = customExercises.size,
            totalRecords = records,
            pendingText = text
        )
    }
}

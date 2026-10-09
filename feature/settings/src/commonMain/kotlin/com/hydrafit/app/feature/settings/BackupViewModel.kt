package com.hydrafit.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.backup.BackupException
import com.hydrafit.app.core.domain.backup.BackupFailure
import com.hydrafit.app.core.domain.backup.BackupFile
import com.hydrafit.app.core.domain.backup.ExportBackupUseCase
import com.hydrafit.app.core.domain.backup.PreviewBackupUseCase
import com.hydrafit.app.core.domain.backup.RestoreBackupUseCase
import com.hydrafit.app.core.domain.time.TimeProvider
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
 * A failure from a previous startup apply is surfaced once when the screen opens. Cancellation is
 * rethrown, never reported as an I/O failure.
 */
class BackupViewModel(
    private val exportBackup: ExportBackupUseCase,
    private val previewBackup: PreviewBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase,
    private val fileStore: BackupFileStore,
    private val appVersionProvider: AppVersionProvider,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState(isSupported = fileStore.isSupported))
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            restoreBackup.consumePendingApplyError()?.let { failure ->
                _state.update { it.copy(status = BackupStatus.Failed(failure)) }
            }
        }
    }

    fun export(handle: String) {
        if (_state.value.inProgress) return
        _state.update { it.copy(inProgress = true, status = null) }
        viewModelScope.launch {
            try {
                val text = exportBackup(appVersionProvider.versionName, timeProvider.nowMillis())
                fileStore.write(handle, text)
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

    fun `import`(handle: String) {
        if (_state.value.inProgress) return
        _state.update { it.copy(inProgress = true, status = null, preview = null) }
        viewModelScope.launch {
            try {
                val text = fileStore.read(handle)
                _state.update {
                    it.copy(inProgress = false, preview = previewBackup(text).toPreview(text))
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
                    it.copy(inProgress = false, preview = null, status = BackupStatus.RestoreStaged)
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
        _state.update { it.copy(preview = null) }
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
            volumeExplanationStates.size + routines.size + routineWorkouts.size +
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

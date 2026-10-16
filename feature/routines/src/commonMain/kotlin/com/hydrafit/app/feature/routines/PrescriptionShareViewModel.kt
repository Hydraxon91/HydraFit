package com.hydrafit.app.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.backup.BackupException
import com.hydrafit.app.core.domain.backup.BackupFailure
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.transfer.ExportPrescriptionsUseCase
import com.hydrafit.app.core.domain.transfer.PrescriptionDocument
import com.hydrafit.app.core.domain.transfer.PrescriptionException
import com.hydrafit.app.core.domain.transfer.PrescriptionFailure
import com.hydrafit.app.core.domain.transfer.PrescriptionIdentityChoice
import com.hydrafit.app.core.domain.transfer.PrescriptionImportChoices
import com.hydrafit.app.core.domain.transfer.PrescriptionImportCommitFailure
import com.hydrafit.app.core.domain.transfer.PrescriptionImportException
import com.hydrafit.app.core.domain.transfer.PrescriptionImportRepository
import com.hydrafit.app.core.domain.transfer.PrescriptionJson
import com.hydrafit.app.core.domain.transfer.PrescriptionValidator
import com.hydrafit.app.core.domain.transfer.PreviewPrescriptionImport
import com.hydrafit.app.core.userdata.backup.BackupFileStore
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import com.hydrafit.app.core.userdata.settings.AppVersionProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Routines prescription transfer: export one routine through [BackupFileStore] to a chosen
 * document, and import a document through a resolved review whose choices are explicit. The preview
 * is rebuilt from the cached decoded document after every choice so it is never stale, and no write
 * happens until the user confirms an importable preview. Nothing is activated or scheduled.
 * Cancellation is rethrown, never reported as a read, write or commit failure.
 */
class PrescriptionShareViewModel(
    private val exportPrescriptions: ExportPrescriptionsUseCase,
    private val importRepository: PrescriptionImportRepository,
    private val fileStore: BackupFileStore,
    private val exerciseCatalog: ExerciseCatalog,
    private val equipmentSelection: EquipmentSelectionRepository,
    private val appVersionProvider: AppVersionProvider,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _state = MutableStateFlow(
        PrescriptionShareUiState(isSupported = fileStore.isSupported)
    )
    val state: StateFlow<PrescriptionShareUiState> = _state.asStateFlow()

    private var catalog: List<Exercise> = emptyList()
    private var selectedEquipment: Set<EquipmentTag> = emptySet()
    private var document: PrescriptionDocument? = null
    private val choices = mutableMapOf<String, PrescriptionIdentityChoice>()
    private val acceptedEquipmentGaps = mutableSetOf<String>()

    // --- Export ---

    /** Opens the per-routine export dialog with weights off, the documented default. */
    fun onExportRequested(routine: RoutineTemplate) {
        if (!_state.value.isSupported) return
        _state.update { it.copy(export = RoutineExportState(routine.id, routine.name)) }
    }

    fun onExportWeightsChanged(includeWeights: Boolean) {
        _state.update { current ->
            current.export?.let { current.copy(export = it.copy(includeWeights = includeWeights)) }
                ?: current
        }
    }

    fun exportRoutine(handle: String) {
        val export = _state.value.export ?: return
        if (!_state.value.isSupported || export.inProgress) return
        _state.update { it.copy(export = export.copy(inProgress = true, status = null)) }
        viewModelScope.launch {
            try {
                val prescriptions = exportPrescriptions(
                    routineIds = listOf(export.routineId),
                    includeWeights = export.includeWeights,
                    appVersion = appVersionProvider.versionName,
                    exportedAtMillis = timeProvider.nowMillis()
                )
                fileStore.write(handle, PrescriptionJson.encode(prescriptions))
                _state.update { current ->
                    current.export?.copy(
                        inProgress = false,
                        status = PrescriptionExportStatus.Saved
                    )?.let { current.copy(export = it) } ?: current
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _state.update { current ->
                    current.export?.copy(
                        inProgress = false,
                        status = PrescriptionExportStatus.Failed(throwable.toExportFailure())
                    )?.let { current.copy(export = it) } ?: current
                }
            }
        }
    }

    /** Closes the export dialog and clears its one-shot result. */
    fun onExportDismissed() {
        _state.update { it.copy(export = null) }
    }

    // --- Import ---

    /**
     * Reads and decodes the chosen document into a review. A typed document failure is surfaced as-is;
     * anything else (including an unreadable file) is a generic read failure.
     */
    fun onImportChosen(handle: String) {
        if (!_state.value.isSupported || _state.value.importInProgress) return
        _state.update {
            it.copy(importInProgress = true, importPhase = PrescriptionImportPhase.Idle)
        }
        viewModelScope.launch {
            try {
                val text = fileStore.read(handle)
                val decoded = PrescriptionJson.decode(text)
                PrescriptionValidator.validate(decoded)
                val selected = equipmentSelection.selected()
                val catalogExercises = exerciseCatalog.all()
                catalog = catalogExercises
                selectedEquipment = selected
                document = decoded
                choices.clear()
                acceptedEquipmentGaps.clear()
                _state.update {
                    it.copy(
                        importInProgress = false,
                        exercises = catalogExercises.toOptions(),
                        importPhase = PrescriptionImportPhase.Reviewing(rebuildReview(decoded))
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: PrescriptionException) {
                _state.update {
                    it.copy(
                        importInProgress = false,
                        importPhase = PrescriptionImportPhase.Failed(failure.failure)
                    )
                }
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        importInProgress = false,
                        importPhase = PrescriptionImportPhase.Failed(null)
                    )
                }
            }
        }
    }

    /**
     * Records the user's resolution for one referenced identity, or clears it. `null` returns the
     * identity to undecided, which keeps the import blocked rather than resolving it silently.
     */
    fun onIdentityChoiceChanged(sourceExerciseId: String, choice: PrescriptionIdentityChoice?) {
        if (_state.value.importInProgress) return
        val decoded = document ?: return
        if (choice == null) {
            choices.remove(sourceExerciseId)
        } else {
            choices[sourceExerciseId] = choice
        }
        _state.update {
            it.copy(importPhase = PrescriptionImportPhase.Reviewing(rebuildReview(decoded)))
        }
    }

    /**
     * Records whether the user accepted the missing equipment for one resolved identity. This is
     * independent of the identity choice, so a definition can be created *and* its equipment gap
     * accepted in the same import.
     */
    fun onEquipmentGapChanged(sourceExerciseId: String, accepted: Boolean) {
        if (_state.value.importInProgress) return
        val decoded = document ?: return
        if (accepted) {
            acceptedEquipmentGaps += sourceExerciseId
        } else {
            acceptedEquipmentGaps -= sourceExerciseId
        }
        _state.update {
            it.copy(importPhase = PrescriptionImportPhase.Reviewing(rebuildReview(decoded)))
        }
    }

    /** Imports the approved preview in one transaction; on refusal the review is kept and flagged. */
    fun onImportConfirmed() {
        val review = currentReview() ?: return
        if (!review.preview.canImport || _state.value.importInProgress) return
        val preview = review.preview
        _state.update { it.copy(importInProgress = true) }
        viewModelScope.launch {
            try {
                val result = importRepository.importPrescriptions(preview)
                document = null
                choices.clear()
                acceptedEquipmentGaps.clear()
                _state.update {
                    it.copy(
                        importInProgress = false,
                        importPhase = PrescriptionImportPhase.Imported(
                            importedRoutines = result.importedRoutines,
                            createdExercises = result.createdExercises
                        )
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: PrescriptionImportException) {
                val decoded = document
                if (decoded == null) {
                    _state.update {
                        it.copy(
                            importInProgress = false,
                            importPhase = PrescriptionImportPhase.Idle
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            importInProgress = false,
                            importPhase = PrescriptionImportPhase.Reviewing(
                                rebuildReview(decoded, commitFailure = failure.failure)
                            )
                        )
                    }
                }
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        importInProgress = false,
                        importPhase = PrescriptionImportPhase.Failed(null)
                    )
                }
            }
        }
    }

    /** Discards the review or the finished-import summary. */
    fun onImportDismissed() {
        document = null
        choices.clear()
        acceptedEquipmentGaps.clear()
        _state.update {
            it.copy(importInProgress = false, importPhase = PrescriptionImportPhase.Idle)
        }
    }

    private fun currentReview(): PrescriptionImportReview? =
        (_state.value.importPhase as? PrescriptionImportPhase.Reviewing)?.review

    private fun rebuildReview(
        document: PrescriptionDocument,
        commitFailure: PrescriptionImportCommitFailure? = null
    ): PrescriptionImportReview = PrescriptionImportReview(
        preview = PreviewPrescriptionImport.build(
            document = document,
            choices = PrescriptionImportChoices(
                identities = choices.toMap(),
                acceptedEquipmentGaps = acceptedEquipmentGaps.toSet()
            ),
            catalog = catalog,
            selectedEquipment = selectedEquipment
        ),
        commitFailure = commitFailure
    )

    private fun List<Exercise>.toOptions(): List<PrescriptionExerciseOption> =
        map { PrescriptionExerciseOption(it.id, it.name) }.sortedBy { it.name }

    private fun Throwable.toExportFailure(): PrescriptionExportFailure = when {
        this is PrescriptionException && failure == PrescriptionFailure.TOO_LARGE ->
            PrescriptionExportFailure.TOO_LARGE
        this is PrescriptionException && failure == PrescriptionFailure.OVER_LIMIT ->
            PrescriptionExportFailure.OVER_LIMIT
        this is BackupException && failure == BackupFailure.TOO_LARGE ->
            PrescriptionExportFailure.TOO_LARGE
        else -> PrescriptionExportFailure.IO
    }
}

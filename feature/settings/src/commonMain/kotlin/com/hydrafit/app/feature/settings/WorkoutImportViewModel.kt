package com.hydrafit.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.CatalogExerciseProfile
import com.hydrafit.app.core.domain.equipment.CatalogProfileMatch
import com.hydrafit.app.core.domain.equipment.CatalogProfileMatcher
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.transfer.ParsedWorkoutCsv
import com.hydrafit.app.core.domain.transfer.PreviewWorkoutImport
import com.hydrafit.app.core.domain.transfer.SourceCivilDate
import com.hydrafit.app.core.domain.transfer.WorkoutCsvException
import com.hydrafit.app.core.domain.transfer.WorkoutCsvParser
import com.hydrafit.app.core.domain.transfer.WorkoutCsvSource
import com.hydrafit.app.core.domain.transfer.WorkoutImportChoices
import com.hydrafit.app.core.domain.transfer.WorkoutImportCommitFailure
import com.hydrafit.app.core.domain.transfer.WorkoutImportException
import com.hydrafit.app.core.domain.transfer.WorkoutImportExerciseChoice
import com.hydrafit.app.core.domain.transfer.WorkoutImportPreview
import com.hydrafit.app.core.domain.transfer.WorkoutImportProvenance
import com.hydrafit.app.core.domain.transfer.WorkoutImportRepository
import com.hydrafit.app.core.domain.transfer.WorkoutImportSessionChoice
import com.hydrafit.app.core.domain.transfer.sourceCandidateKey
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSessionRepository
import com.hydrafit.app.core.userdata.backup.BackupFileStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Settings workout-CSV import: read a chosen file through [BackupFileStore], parse it with
 * the named provider, and derive a review from explicit per-exercise and per-session choices. The
 * preview is recomputed from the cached parsed rows after every choice so it is never stale, and no
 * write happens until the user applies an importable preview. Cancellation is rethrown, never
 * reported as a read or commit failure.
 */
class WorkoutImportViewModel(
    private val fileStore: BackupFileStore,
    private val importRepository: WorkoutImportRepository,
    private val exerciseCatalog: ExerciseCatalog,
    private val sessionRepository: WorkoutSessionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutImportUiState(isSupported = fileStore.isSupported))
    val state: StateFlow<WorkoutImportUiState> = _state.asStateFlow()

    private var parsed: ParsedWorkoutCsv? = null
    private var catalog: List<Exercise> = emptyList()
    private var profileCandidates: List<CatalogExerciseProfile> = emptyList()
    private var provenance: List<WorkoutImportProvenance> = emptyList()
    private var existingSessions: List<WorkoutSession> = emptyList()
    private val mappingEdits = mutableMapOf<String, MappingEdit>()
    private val sessionEdits = mutableMapOf<SessionKey, SessionEdit>()
    private val excludedRows = mutableSetOf<Int>()
    private var acknowledgedUnsupportedFields = false
    private var acknowledgedHistoryEffects = false
    private var commitFailure: WorkoutImportCommitFailure? = null

    /** Invalidates any in-flight read so a late result cannot overwrite newer state. */
    private var revision = 0

    fun selectProvider(source: WorkoutCsvSource) {
        if (_state.value.inProgress) return
        _state.update { it.copy(provider = source) }
        // A review parsed with the previous provider is stale, so drop it rather than mislabel it.
        clearReview()
    }

    fun chooseFile(handle: String) {
        if (!_state.value.isSupported || _state.value.inProgress) return
        val source = _state.value.provider
        clearReview()
        val token = revision
        _state.update { it.copy(inProgress = true) }
        viewModelScope.launch {
            try {
                val text = fileStore.read(handle)
                val parsedCsv = WorkoutCsvParser.parse(source, text)
                val all = exerciseCatalog.all()
                val candidates = exerciseCatalog.profileCandidates()
                val keys = parsedCsv.rows.map { sourceCandidateKey(parsedCsv.source, it) }.toSet()
                val provenanceResult = importRepository.provenance(parsedCsv.source, keys)
                val sessions = sessionRepository.all()
                if (token != revision) return@launch
                parsed = parsedCsv
                catalog = all
                profileCandidates = candidates
                provenance = provenanceResult
                existingSessions = sessions
                seedMappings(parsedCsv)
                _state.update {
                    it.copy(
                        inProgress = false,
                        phase = WorkoutImportPhase.Reviewing(buildReview())
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (csv: WorkoutCsvException) {
                if (token != revision) return@launch
                _state.update {
                    it.copy(
                        inProgress = false,
                        phase = WorkoutImportPhase.Failed(
                            WorkoutImportFailure.Csv(csv.failure)
                        )
                    )
                }
            } catch (throwable: Throwable) {
                if (token != revision) return@launch
                _state.update {
                    it.copy(
                        inProgress = false,
                        phase = WorkoutImportPhase.Failed(WorkoutImportFailure.Unknown)
                    )
                }
            }
        }
    }

    fun setMapping(exerciseName: String, exerciseId: String?) {
        if (!isReviewing()) return
        commitFailure = null
        if (exerciseId == null) {
            mappingEdits.remove(exerciseName)
        } else {
            val existing = mappingEdits[exerciseName]
            mappingEdits[exerciseName] = existing?.copy(exerciseId = exerciseId)
                ?: MappingEdit(
                    exerciseId = exerciseId,
                    loadKind = LoadKind.EXTERNAL,
                    useSourceWeight = true,
                    confirmedLoadMeaning = false,
                    unknownWarmupIsWorking = false
                )
        }
        refreshReview()
    }

    fun setLoadKind(exerciseName: String, loadKind: LoadKind) {
        val edit = mappingEdits[exerciseName] ?: return
        if (!isReviewing()) return
        commitFailure = null
        mappingEdits[exerciseName] = edit.copy(loadKind = loadKind, confirmedLoadMeaning = false)
        refreshReview()
    }

    fun setUseSourceWeight(exerciseName: String, useSourceWeight: Boolean) {
        val edit = mappingEdits[exerciseName] ?: return
        if (!isReviewing()) return
        commitFailure = null
        mappingEdits[exerciseName] = edit.copy(
            useSourceWeight = useSourceWeight,
            confirmedLoadMeaning = false
        )
        refreshReview()
    }

    fun confirmLoadMeaning(exerciseName: String) {
        val edit = mappingEdits[exerciseName] ?: return
        if (!isReviewing()) return
        commitFailure = null
        mappingEdits[exerciseName] = edit.copy(confirmedLoadMeaning = true)
        refreshReview()
    }

    fun setUnknownWarmupIsWorking(exerciseName: String, unknownWarmupIsWorking: Boolean) {
        val edit = mappingEdits[exerciseName] ?: return
        if (!isReviewing()) return
        commitFailure = null
        mappingEdits[exerciseName] = edit.copy(unknownWarmupIsWorking = unknownWarmupIsWorking)
        refreshReview()
    }

    fun setSessionOffset(key: SessionKey, text: String) {
        if (!isReviewing()) return
        commitFailure = null
        val edit = sessionEdit(key)
        sessionEdits[key] = edit.copy(
            utcOffsetText = text,
            utcOffsetMinutes = parseUtcOffset(text) ?: edit.utcOffsetMinutes
        )
        refreshReview()
    }

    fun setSessionTime(key: SessionKey, secondOfDay: Int?) {
        if (!isReviewing()) return
        commitFailure = null
        sessionEdits[key] = sessionEdit(key).copy(dateOnlySecondOfDay = secondOfDay)
        refreshReview()
    }

    fun confirmSession(key: SessionKey, confirmed: Boolean) {
        if (!isReviewing()) return
        commitFailure = null
        sessionEdits[key] = sessionEdit(key).copy(
            confirmedSession = confirmed,
            confirmedInferredSetTimes = confirmed
        )
        refreshReview()
    }

    fun toggleRowIncluded(rowIndex: Int) {
        if (!isReviewing()) return
        commitFailure = null
        if (!excludedRows.remove(rowIndex)) {
            excludedRows.add(rowIndex)
        }
        refreshReview()
    }

    fun acknowledgeUnsupportedFields(acknowledged: Boolean) {
        if (!isReviewing()) return
        commitFailure = null
        acknowledgedUnsupportedFields = acknowledged
        refreshReview()
    }

    fun acknowledgeHistoryEffects(acknowledged: Boolean) {
        if (!isReviewing()) return
        commitFailure = null
        acknowledgedHistoryEffects = acknowledged
        refreshReview()
    }

    fun apply() {
        if (!_state.value.isSupported || _state.value.inProgress) return
        val review = (_state.value.phase as? WorkoutImportPhase.Reviewing)?.review ?: return
        if (!review.canImport) return
        val parsedCsv = parsed ?: return
        val preview = currentPreview(parsedCsv)
        if (!preview.canImport) return
        _state.update { it.copy(inProgress = true) }
        viewModelScope.launch {
            try {
                val result = importRepository.commit(preview)
                clearCache()
                _state.update {
                    it.copy(inProgress = false, phase = WorkoutImportPhase.Imported(result))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (importFailure: WorkoutImportException) {
                commitFailure = importFailure.failure
                _state.update {
                    it.copy(
                        inProgress = false,
                        phase = WorkoutImportPhase.Reviewing(buildReview())
                    )
                }
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        inProgress = false,
                        phase = WorkoutImportPhase.Failed(WorkoutImportFailure.Unknown)
                    )
                }
            }
        }
    }

    fun reset() {
        clearReview()
    }

    private fun isReviewing(): Boolean = _state.value.phase is WorkoutImportPhase.Reviewing

    private fun refreshReview() {
        _state.update { it.copy(phase = WorkoutImportPhase.Reviewing(buildReview())) }
    }

    private fun clearReview() {
        revision++
        clearCache()
        _state.update { it.copy(phase = WorkoutImportPhase.Idle, inProgress = false) }
    }

    private fun clearCache() {
        parsed = null
        catalog = emptyList()
        profileCandidates = emptyList()
        provenance = emptyList()
        existingSessions = emptyList()
        mappingEdits.clear()
        sessionEdits.clear()
        excludedRows.clear()
        acknowledgedUnsupportedFields = false
        acknowledgedHistoryEffects = false
        commitFailure = null
    }

    private fun seedMappings(parsedCsv: ParsedWorkoutCsv) {
        mappingEdits.clear()
        sessionEdits.clear()
        excludedRows.clear()
        parsedCsv.rows.map { it.exerciseName }.distinct().forEach { name ->
            when (val match = CatalogProfileMatcher.match(name, profileCandidates)) {
                is CatalogProfileMatch.Unique -> mappingEdits[name] = MappingEdit(
                    exerciseId = match.candidate.catalogId,
                    loadKind = LoadKind.EXTERNAL,
                    useSourceWeight = true,
                    confirmedLoadMeaning = false,
                    unknownWarmupIsWorking = false
                )

                is CatalogProfileMatch.Ambiguous, CatalogProfileMatch.Unknown -> Unit
            }
        }
    }

    private fun sessionEdit(key: SessionKey): SessionEdit = sessionEdits[key] ?: SessionEdit()

    private fun currentPreview(parsedCsv: ParsedWorkoutCsv): WorkoutImportPreview =
        PreviewWorkoutImport.build(
            parsed = parsedCsv,
            choices = buildChoices(parsedCsv),
            catalog = catalog,
            provenance = provenance,
            existingSessions = existingSessions
        )

    private fun buildReview(): WorkoutImportReview {
        val parsedCsv = parsed ?: return emptyReview()
        return projectReview(parsedCsv, currentPreview(parsedCsv))
    }

    private fun buildChoices(parsedCsv: ParsedWorkoutCsv): WorkoutImportChoices {
        val included = parsedCsv.rows.filterNot { it.sourceRowIndex in excludedRows }
        val exercises = mappingEdits.mapValues { (_, edit) ->
            WorkoutImportExerciseChoice(
                exerciseId = edit.exerciseId,
                loadKind = edit.loadKind,
                useSourceWeight = edit.useSourceWeight,
                confirmedLoadMeaning = edit.confirmedLoadMeaning,
                unknownWarmupIsWorking = edit.unknownWarmupIsWorking
            )
        }
        val sessions = included.groupBy { SessionKey(it.day, it.workoutTitle) }
            .entries.map { (key, groupRows) ->
                val edit = sessionEdits[key] ?: SessionEdit()
                WorkoutImportSessionChoice(
                    sourceRows = groupRows.map { it.sourceRowIndex },
                    utcOffsetMinutes = edit.utcOffsetMinutes,
                    confirmedSession = edit.confirmedSession,
                    confirmedInferredSetTimes = edit.confirmedInferredSetTimes,
                    dateOnlySecondOfDay = edit.dateOnlySecondOfDay
                )
            }
        return WorkoutImportChoices(
            exercises = exercises,
            sessions = sessions,
            excludedRows = excludedRows.toSet(),
            acknowledgedUnsupportedFields = acknowledgedUnsupportedFields,
            acknowledgedHistoryEffects = acknowledgedHistoryEffects
        )
    }

    private fun projectReview(
        parsedCsv: ParsedWorkoutCsv,
        preview: WorkoutImportPreview
    ): WorkoutImportReview {
        val includedRows = parsedCsv.rows.filterNot { it.sourceRowIndex in excludedRows }
        val rows = parsedCsv.rows.map { row ->
            WorkoutImportRowUi(
                sourceRowIndex = row.sourceRowIndex,
                exerciseName = row.exerciseName,
                dayLabel = formatDay(row.day),
                timeText = row.timeOfDaySeconds?.let(::formatClockSeconds),
                weightText = row.weight?.let(::formatWeight),
                unitLabel = row.unit.label,
                reps = row.reps,
                warmup = row.warmup,
                diagnostics = row.diagnostics,
                included = row.sourceRowIndex !in excludedRows
            )
        }
        val rejected = parsedCsv.rejectedRows.map { row ->
            RejectedImportRowUi(
                sourceRowIndex = row.sourceRowIndex,
                diagnostics = row.diagnostics,
                excluded = row.sourceRowIndex in excludedRows
            )
        }
        val mappingNames = includedRows.map { it.exerciseName }.distinct()
        val mappings = mappingNames.map { name ->
            mappingUi(name, includedRows.count { it.exerciseName == name })
        }
        val sessions = includedRows.groupBy { SessionKey(it.day, it.workoutTitle) }
            .entries.map { (key, groupRows) ->
                val edit = sessionEdits[key] ?: SessionEdit()
                SessionDraftUi(
                    key = key,
                    dayLabel = formatDay(key.day),
                    workoutTitle = key.workoutTitle,
                    rowIndexes = groupRows.map { it.sourceRowIndex },
                    utcOffsetText = edit.utcOffsetText,
                    utcOffsetMinutes = edit.utcOffsetMinutes,
                    dateOnlySecondOfDay = edit.dateOnlySecondOfDay,
                    confirmedSession = edit.confirmedSession,
                    confirmedInferredSetTimes = edit.confirmedInferredSetTimes
                )
            }
        val offsetProblems = sessions.mapNotNull { session ->
            if (parseUtcOffset(session.utcOffsetText) == null) {
                ReviewProblem.InvalidOffset(session.key, session.rowIndexes.firstOrNull())
            } else {
                null
            }
        }
        return WorkoutImportReview(
            rows = rows,
            mappings = mappings,
            sessions = sessions,
            rejectedRows = rejected,
            excludedRows = excludedRows.toSet(),
            catalogOptions = catalog.map { MappingCandidateUi(it.id, it.name) },
            acknowledgedUnsupportedFields = acknowledgedUnsupportedFields,
            acknowledgedHistoryEffects = acknowledgedHistoryEffects,
            problems = (preview.problems.map { ReviewProblem.Domain(it) } + offsetProblems)
                .distinct(),
            notices = preview.notices,
            canImport = preview.canImport && offsetProblems.isEmpty(),
            commitFailure = commitFailure
        )
    }

    private fun mappingUi(name: String, occurrenceCount: Int): MappingUi {
        val edit = mappingEdits[name]
        val state = when (val match = CatalogProfileMatcher.match(name, profileCandidates)) {
            is CatalogProfileMatch.Unique ->
                MappingState.Suggested(match.candidate.catalogId, match.candidate.displayName)

            is CatalogProfileMatch.Ambiguous ->
                MappingState.Ambiguous(match.candidates.map { it.displayName })

            CatalogProfileMatch.Unknown -> MappingState.Unmatched
        }
        return MappingUi(
            exerciseName = name,
            occurrenceCount = occurrenceCount,
            state = state,
            chosenExerciseId = edit?.exerciseId,
            loadKind = edit?.loadKind ?: LoadKind.EXTERNAL,
            useSourceWeight = edit?.useSourceWeight ?: true,
            confirmedLoadMeaning = edit?.confirmedLoadMeaning ?: false,
            unknownWarmupIsWorking = edit?.unknownWarmupIsWorking ?: false
        )
    }

    private fun emptyReview(): WorkoutImportReview = WorkoutImportReview(
        rows = emptyList(),
        mappings = emptyList(),
        sessions = emptyList(),
        rejectedRows = emptyList(),
        excludedRows = emptySet(),
        catalogOptions = emptyList(),
        acknowledgedUnsupportedFields = false,
        acknowledgedHistoryEffects = false,
        problems = emptyList(),
        notices = emptyList(),
        canImport = false
    )

    private data class MappingEdit(
        val exerciseId: String,
        val loadKind: LoadKind,
        val useSourceWeight: Boolean,
        val confirmedLoadMeaning: Boolean,
        val unknownWarmupIsWorking: Boolean
    )

    private data class SessionEdit(
        val utcOffsetText: String = "0",
        val utcOffsetMinutes: Int = 0,
        val confirmedSession: Boolean = false,
        val confirmedInferredSetTimes: Boolean = false,
        val dateOnlySecondOfDay: Int? = null
    )
}

/** Empty text means 0; otherwise a whole number of minutes in -1080..1080, or null when invalid. */
private fun parseUtcOffset(text: String): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return 0
    val value = trimmed.toIntOrNull() ?: return null
    return if (value in -1080..1080) value else null
}

private fun formatDay(day: SourceCivilDate): String = "${day.year.toString().padStart(4, '0')}-" +
    "${day.month.toString().padStart(2, '0')}-" +
    day.day.toString().padStart(2, '0')

private fun formatClockSeconds(secondsOfDay: Int): String {
    val hour = secondsOfDay / 3600
    val minute = (secondsOfDay % 3600) / 60
    return hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
}

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
import com.hydrafit.app.core.domain.transfer.WorkoutCsvRow
import com.hydrafit.app.core.domain.transfer.WorkoutCsvSource
import com.hydrafit.app.core.domain.transfer.WorkoutImportBucket
import com.hydrafit.app.core.domain.transfer.WorkoutImportChoices
import com.hydrafit.app.core.domain.transfer.WorkoutImportCommitFailure
import com.hydrafit.app.core.domain.transfer.WorkoutImportException
import com.hydrafit.app.core.domain.transfer.WorkoutImportExerciseChoice
import com.hydrafit.app.core.domain.transfer.WorkoutImportPartition
import com.hydrafit.app.core.domain.transfer.WorkoutImportPreview
import com.hydrafit.app.core.domain.transfer.WorkoutImportProvenance
import com.hydrafit.app.core.domain.transfer.WorkoutImportRepository
import com.hydrafit.app.core.domain.transfer.WorkoutImportSessionChoice
import com.hydrafit.app.core.domain.transfer.canonicalBucketContent
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
    private var buckets: List<WorkoutImportBucket> = emptyList()
    private var existingSessions: List<WorkoutSession> = emptyList()
    private val mappingEdits = mutableMapOf<String, MappingEdit>()
    private val sessionEdits = mutableMapOf<Int, SessionEdit>()
    private val draftBuckets = mutableMapOf<Int, SessionKey>()
    private val rowDrafts = mutableMapOf<Int, Int>()
    private var nextDraftId = 0
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
                val bucketResult = importRepository.buckets(parsedCsv.source, keys)
                val sessions = sessionRepository.all()
                if (token != revision) return@launch
                parsed = parsedCsv
                catalog = all
                profileCandidates = candidates
                provenance = provenanceResult
                buckets = bucketResult
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

    fun setSessionOffset(draftId: Int, text: String) {
        if (!isReviewing()) return
        commitFailure = null
        val edit = sessionEdit(draftId)
        sessionEdits[draftId] = edit.copy(
            utcOffsetText = text,
            utcOffsetMinutes = parseUtcOffset(text) ?: edit.utcOffsetMinutes
        )
        refreshReview()
    }

    fun setSessionTime(draftId: Int, secondOfDay: Int?) {
        if (!isReviewing()) return
        commitFailure = null
        sessionEdits[draftId] = sessionEdit(draftId).copy(dateOnlySecondOfDay = secondOfDay)
        refreshReview()
    }

    fun confirmSession(draftId: Int, confirmed: Boolean) {
        if (!isReviewing()) return
        commitFailure = null
        sessionEdits[draftId] = sessionEdit(draftId).copy(
            confirmedSession = confirmed,
            confirmedInferredSetTimes = confirmed
        )
        refreshReview()
    }

    /**
     * Moves an included row into [draftId], or into a fresh session of its bucket when null. Every
     * position stays assigned exactly once, so a bucket can be split and the parts rejoined.
     */
    fun setRowSession(rowIndex: Int, draftId: Int?) {
        if (!isReviewing()) return
        val row = parsed?.rows?.firstOrNull { it.sourceRowIndex == rowIndex } ?: return
        if (rowIndex in excludedRows) return
        commitFailure = null
        val bucket = SessionKey(row.day, row.workoutTitle)
        val previous = rowDrafts[rowIndex]
        val target = when {
            draftId == null -> createDraft(bucket)
            draftBuckets[draftId] == bucket -> draftId
            else -> return
        }
        rowDrafts[rowIndex] = target
        if (previous != null && previous != target) pruneDraft(previous)
        refreshReview()
    }

    fun toggleRowIncluded(rowIndex: Int) {
        if (!isReviewing()) return
        commitFailure = null
        if (!excludedRows.remove(rowIndex)) {
            excludedRows.add(rowIndex)
            rowDrafts.remove(rowIndex)?.let(::pruneDraft)
        } else {
            // Re-including a row returns it to the first session of its bucket (or a new one).
            val row = parsed?.rows?.firstOrNull { it.sourceRowIndex == rowIndex } ?: run {
                refreshReview()
                return
            }
            val bucket = SessionKey(row.day, row.workoutTitle)
            val existing = draftBuckets.entries.filter { it.value == bucket }.minByOrNull { it.key }
            rowDrafts[rowIndex] = existing?.key ?: createDraft(bucket)
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
                if (importFailure.failure == WorkoutImportCommitFailure.SESSION_OVERLAP) {
                    // Local history changed under the review, so refresh it: the rebuilt review then
                    // shows the overlap as a normal blocking problem before the user retries.
                    existingSessions = sessionRepository.all()
                }
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
        buckets = emptyList()
        existingSessions = emptyList()
        mappingEdits.clear()
        sessionEdits.clear()
        draftBuckets.clear()
        rowDrafts.clear()
        nextDraftId = 0
        excludedRows.clear()
        acknowledgedUnsupportedFields = false
        acknowledgedHistoryEffects = false
        commitFailure = null
    }

    private fun seedMappings(parsedCsv: ParsedWorkoutCsv) {
        mappingEdits.clear()
        sessionEdits.clear()
        draftBuckets.clear()
        rowDrafts.clear()
        nextDraftId = 0
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
        // One editable session draft per source bucket; the user may split a bucket further. When the
        // file is unchanged since its last import, the drafts are rebuilt from the *stored* approved
        // partition instead of the merged default. Without that, a bucket the user split would come
        // back as one merged draft, differ from its stored partition, and be refused as a changed
        // source — permanently blocking a re-import of byte-identical content.
        parsedCsv.rows.groupBy { SessionKey(it.day, it.workoutTitle) }.forEach { (bucket, rows) ->
            val ordered = rows.sortedBy { it.sourceRowIndex }
            if (!seedStoredPartition(parsedCsv, bucket, ordered)) {
                val draftId = createDraft(bucket)
                ordered.forEach { rowDrafts[it.sourceRowIndex] = draftId }
            }
        }
    }

    /**
     * Rebuilds this bucket's drafts from the partition approved at its last import when the file still
     * carries identical content, including the exclusions that were chosen then. Returns false when
     * there is no usable stored partition for these rows, so the caller falls back to one merged draft
     * and the preview reports the bucket on its own terms.
     */
    private fun seedStoredPartition(
        parsedCsv: ParsedWorkoutCsv,
        bucket: SessionKey,
        rows: List<WorkoutCsvRow>
    ): Boolean {
        if (rows.isEmpty()) return false
        val key = sourceCandidateKey(parsedCsv.source, rows.first())
        val stored = buckets.singleOrNull {
            it.source == parsedCsv.source && it.candidateKey == key
        } ?: return false
        if (stored.content != canonicalBucketContent(rows)) return false
        val partition = WorkoutImportPartition.decode(stored.partition) ?: return false
        if (partition.rowCount != rows.size) return false
        partition.sessions.forEach { positions ->
            val draftId = createDraft(bucket)
            positions.map { rows[it] }.forEach { rowDrafts[it.sourceRowIndex] = draftId }
        }
        partition.excluded.map { rows[it] }.forEach { excludedRows += it.sourceRowIndex }
        return true
    }

    private fun createDraft(bucket: SessionKey): Int {
        val id = nextDraftId++
        draftBuckets[id] = bucket
        return id
    }

    private fun pruneDraft(draftId: Int) {
        if (rowDrafts.values.none { it == draftId }) {
            draftBuckets.remove(draftId)
            sessionEdits.remove(draftId)
        }
    }

    private fun rowsInDraft(draftId: Int): List<Int> = parsed?.rows.orEmpty()
        .filter { rowDrafts[it.sourceRowIndex] == draftId }
        .map { it.sourceRowIndex }
        .sorted()

    /** 1-based position of a draft among the drafts of the same bucket, in creation order. */
    private fun ordinalFor(draftId: Int): Int {
        val bucket = draftBuckets[draftId] ?: return 1
        return draftBuckets.keys.sorted()
            .count { draftBuckets[it] == bucket && it <= draftId }
    }

    private fun sessionEdit(draftId: Int): SessionEdit = sessionEdits[draftId] ?: SessionEdit()

    private fun currentPreview(parsedCsv: ParsedWorkoutCsv): WorkoutImportPreview =
        PreviewWorkoutImport.build(
            parsed = parsedCsv,
            choices = buildChoices(),
            catalog = catalog,
            provenance = provenance,
            existingSessions = existingSessions,
            buckets = buckets
        )

    private fun buildReview(): WorkoutImportReview {
        val parsedCsv = parsed ?: return emptyReview()
        return projectReview(parsedCsv, currentPreview(parsedCsv))
    }

    private fun buildChoices(): WorkoutImportChoices {
        val exercises = mappingEdits.mapValues { (_, edit) ->
            WorkoutImportExerciseChoice(
                exerciseId = edit.exerciseId,
                loadKind = edit.loadKind,
                useSourceWeight = edit.useSourceWeight,
                confirmedLoadMeaning = edit.confirmedLoadMeaning,
                unknownWarmupIsWorking = edit.unknownWarmupIsWorking
            )
        }
        val sessions = draftBuckets.keys.sorted().map { draftId ->
            val edit = sessionEdits[draftId] ?: SessionEdit()
            WorkoutImportSessionChoice(
                sourceRows = rowsInDraft(draftId),
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
        val rowBuckets = parsedCsv.rows.associate { row ->
            row.sourceRowIndex to SessionKey(row.day, row.workoutTitle)
        }
        val draftIds = draftBuckets.keys.sorted()
        val ordinals = draftIds.associateWith { draftId -> ordinalFor(draftId) }
        val rows = parsedCsv.rows.map { row ->
            val bucket = rowBuckets.getValue(row.sourceRowIndex)
            val bucketDrafts = draftIds.filter { draftBuckets[it] == bucket }
            WorkoutImportRowUi(
                sourceRowIndex = row.sourceRowIndex,
                exerciseName = row.exerciseName,
                dayLabel = formatDay(row.day),
                timeText = row.timeOfDaySeconds?.let(::formatClockSeconds),
                weightText = row.weight?.let(::formatWeight),
                unitLabel = row.unit.label,
                reps = row.reps,
                durationSeconds = row.durationSeconds,
                distanceMeters = row.distanceMeters,
                warmup = row.warmup,
                diagnostics = row.diagnostics,
                included = row.sourceRowIndex !in excludedRows,
                sessionDraftId = rowDrafts[row.sourceRowIndex],
                sessionOptions = bucketDrafts.map { SessionOptionUi(it, ordinals.getValue(it)) }
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
        val sessions = draftIds.map { draftId ->
            val bucket = draftBuckets.getValue(draftId)
            val edit = sessionEdits[draftId] ?: SessionEdit()
            SessionDraftUi(
                draftId = draftId,
                ordinal = ordinals.getValue(draftId),
                dayLabel = formatDay(bucket.day),
                workoutTitle = bucket.workoutTitle,
                split = draftIds.count { draftBuckets[it] == bucket } > 1,
                rowIndexes = rowsInDraft(draftId),
                utcOffsetText = edit.utcOffsetText,
                utcOffsetMinutes = edit.utcOffsetMinutes,
                dateOnlySecondOfDay = edit.dateOnlySecondOfDay,
                confirmedSession = edit.confirmedSession,
                confirmedInferredSetTimes = edit.confirmedInferredSetTimes
            )
        }
        val offsetProblems = sessions.mapNotNull { session ->
            if (parseUtcOffset(session.utcOffsetText) == null) {
                ReviewProblem.InvalidOffset(session.draftId, session.rowIndexes.firstOrNull())
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

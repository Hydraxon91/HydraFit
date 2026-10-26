package com.hydrafit.app.core.domain.transfer

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicyJson
import com.hydrafit.app.core.domain.engine.SlotSetTargetsJson
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.RoutineTemplateRepository

/**
 * Reads the chosen routines into a portable prescription document.
 *
 * Only prescriptions travel: the routines' ordered workouts and slots, plus the custom exercise
 * definitions those slots reference. History, records, notes, credentials, equipment inventories,
 * schedule state and explanations are never read, so they cannot leak. Prescribed weights are
 * omitted unless `includeWeights` is true; the caller decides that from an explicit user choice.
 */
class ExportPrescriptionsUseCase(
    private val routines: RoutineTemplateRepository,
    private val catalog: ExerciseCatalog
) {
    suspend operator fun invoke(
        routineIds: List<Long>,
        includeWeights: Boolean,
        appVersion: String,
        exportedAtMillis: Long
    ): PrescriptionDocument = build(
        templates = routineIds.distinct().mapNotNull { routines.get(it) },
        includeWeights = includeWeights,
        appVersion = appVersion,
        exportedAtMillis = exportedAtMillis
    )

    /**
     * Exports an in-memory routine (for example the plan currently on screen) without saving it.
     * Nothing is persisted and no identity is minted, so sharing a displayed plan cannot create or
     * modify a stored routine.
     */
    suspend fun document(
        templates: List<RoutineTemplate>,
        includeWeights: Boolean,
        appVersion: String,
        exportedAtMillis: Long
    ): PrescriptionDocument = build(templates, includeWeights, appVersion, exportedAtMillis)

    private suspend fun build(
        templates: List<RoutineTemplate>,
        includeWeights: Boolean,
        appVersion: String,
        exportedAtMillis: Long
    ): PrescriptionDocument {
        val exercises = catalog.all().associateBy { it.id }
        val referenced = templates.flatMap { template ->
            template.workouts.flatMap { workout -> workout.entries.map { it.exerciseId } }
        }.toSet()
        val document = PrescriptionDocument(
            format = PRESCRIPTION_FORMAT,
            formatVersion = PRESCRIPTION_FORMAT_VERSION,
            appVersion = appVersion,
            exportedAtMillis = exportedAtMillis,
            includesWeights = includeWeights,
            routines = templates.map { it.toRecord(includeWeights, exercises) },
            customExercises = referenced
                .mapNotNull { exercises[it] }
                .filter { it.isCustom }
                .sortedBy { it.id }
                .map { it.toRecord() }
        )
        // The produced document must be one this app would accept on read: a chosen routine deleted
        // while the export dialog was open would otherwise be written as a file its own import
        // refuses. Both the interchange and the printable path share this check.
        PrescriptionValidator.validate(document)
        return document
    }
}

private fun RoutineTemplate.toRecord(
    includeWeights: Boolean,
    exercises: Map<String, Exercise>
): PrescriptionRoutineRecord = PrescriptionRoutineRecord(
    name = name,
    workouts = workouts
        .sortedBy { it.position }
        .map { workout ->
            PrescriptionWorkoutRecord(
                name = workout.name,
                focus = workout.focus?.name,
                entries = workout.entries.sortedBy { it.position }.map { entry ->
                    val exercise = exercises[entry.exerciseId]
                    PrescriptionEntryRecord(
                        exerciseId = entry.exerciseId,
                        // A deleted custom identity keeps its stored id as the only name available.
                        exerciseName = exercise?.name ?: entry.exerciseId,
                        sets = entry.sets,
                        reps = entry.reps,
                        durationSeconds = entry.durationSeconds,
                        distanceMeters = entry.distanceMeters,
                        forceNewtons = entry.forceNewtons,
                        weightKg = entry.weightKg.takeIf { includeWeights },
                        loadKind = entry.loadKind.name,
                        movementPattern = exercise?.movementPattern?.name,
                        loadCapability = exercise?.loadCapability?.name,
                        progressionPolicy = entry.progressionPolicy
                            ?.let(SlotProgressionPolicyJson::encode),
                        setTargets = entry.setTargets?.let(SlotSetTargetsJson::encode),
                        superset = entry.superset?.let(
                            com.hydrafit.app.core.domain.routine.SupersetJson::encode
                        )
                    )
                }
            )
        }
)

/**
 * A custom definition carries its equipment, pattern, capability and involvements as resolved values,
 * so re-importing it preserves the definition instead of re-deriving one from a display name.
 */
private fun Exercise.toRecord() = PrescriptionExerciseRecord(
    id = id,
    name = name,
    requiredEquipment = requiredEquipment.map { it.id }.sorted(),
    movementPattern = movementPattern.name,
    isUnilateral = isUnilateral,
    loadCapability = loadCapability.name,
    involvements = effectiveInvolvements.entries
        .associate { it.key.name to it.value }
)

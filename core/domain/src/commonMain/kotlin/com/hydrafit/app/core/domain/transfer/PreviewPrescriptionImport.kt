package com.hydrafit.app.core.domain.transfer

import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.routine.RoutineEntry
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.RoutineTemplateValidator
import com.hydrafit.app.core.domain.routine.RoutineWorkout
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy

/**
 * Resolves a prescription document into an approved, importable plan without writing anything.
 *
 * Every referenced identity is reported with what the receiver already has, what would be created and
 * what equipment is missing; nothing is decided for the user. Names are compared with one normalizer
 * so a create cannot silently collide with an existing exercise, and no existing row is ever
 * overwritten by id or name. A resolved prescription is snapped onto the exercise's current load
 * capability, so an imported routine can never hold a load its exercise cannot carry.
 */
object PreviewPrescriptionImport {
    fun build(
        document: PrescriptionDocument,
        choices: PrescriptionImportChoices,
        catalog: List<Exercise>,
        selectedEquipment: Set<EquipmentTag>
    ): PrescriptionImportPreview {
        val problems = mutableListOf<PrescriptionImportProblem>()
        fun problem(issue: PrescriptionImportIssue, exerciseId: String? = null) {
            problems += PrescriptionImportProblem(issue, exerciseId)
        }
        if (document.routines.isEmpty()) problem(PrescriptionImportIssue.NO_ROUTINES)

        val localById = catalog.associateBy { it.id }
        val localByName = catalog.associateBy { normalizeName(it.name) }
        val definitions = document.customExercises.associateBy { it.id }
        val referenced = linkedMapOf<String, SourceExercise>()
        document.routines.forEach { routine ->
            routine.workouts.forEach { workout ->
                workout.entries.forEach { entry ->
                    val existing = referenced[entry.exerciseId]
                    referenced[entry.exerciseId] = SourceExercise(
                        id = entry.exerciseId,
                        name = entry.exerciseName,
                        occurrences = (existing?.occurrences ?: 0) + 1
                    )
                }
            }
        }

        val identities = referenced.values.map { source ->
            val resolution = when {
                localById[source.id] != null -> PrescriptionResolution.Existing(
                    localById.getValue(source.id)
                )
                definitions[source.id] == null -> PrescriptionResolution.Unknown
                else -> {
                    val definition = definitions.getValue(source.id)
                    val local = localByName[normalizeName(definition.name)]
                    if (local != null) {
                        PrescriptionResolution.NameCollision(local, definition)
                    } else {
                        PrescriptionResolution.Create(definition)
                    }
                }
            }
            val chosen = choices.identities[source.id]
            val resolved = chosenExercise(resolution, chosen, localById)
            if (resolved == null) {
                // Report the specific reason rather than a generic "undecided".
                val unknownTarget = (chosen as? PrescriptionIdentityChoice.MapTo)
                    ?.takeIf { it.localExerciseId !in localById }
                when {
                    unknownTarget != null -> problem(
                        PrescriptionImportIssue.INVALID_CHOICE,
                        source.id
                    )
                    resolution is PrescriptionResolution.Unknown -> problem(
                        PrescriptionImportIssue.UNKNOWN_IDENTITY,
                        source.id
                    )
                    resolution is PrescriptionResolution.NameCollision -> problem(
                        PrescriptionImportIssue.NAME_COLLISION,
                        source.id
                    )
                    resolution is PrescriptionResolution.Create -> problem(
                        PrescriptionImportIssue.UNDECIDED_IDENTITY,
                        source.id
                    )
                    else -> problem(PrescriptionImportIssue.INVALID_CHOICE, source.id)
                }
            }
            val missing = resolved?.let { missingEquipment(it, selectedEquipment) }.orEmpty()
            if (resolved != null &&
                missing.isNotEmpty() &&
                chosen != PrescriptionIdentityChoice.AcceptEquipmentGap
            ) {
                problem(PrescriptionImportIssue.EQUIPMENT_GAP, source.id)
            }
            PrescriptionIdentityOption(
                sourceExerciseId = source.id,
                sourceName = source.name,
                resolution = resolution,
                usedByRoutineCount = source.occurrences,
                missingEquipment = missing,
                choice = chosen
            )
        }

        val toCreate = identities.mapNotNull { option ->
            val resolution = option.resolution
            if (option.choice == PrescriptionIdentityChoice.Create &&
                resolution is PrescriptionResolution.Create
            ) {
                resolution.definition.toExercise()
            } else {
                null
            }
        }
        val resolvedById = identities.associate { option ->
            option.sourceExerciseId to
                chosenExercise(option.resolution, option.choice, localById)
        }
        val routines = document.routines.mapNotNull { routine ->
            val workouts = routine.workouts.mapNotNull { workout ->
                val entries = workout.entries.mapNotNull { entry ->
                    val exercise = resolvedById[entry.exerciseId] ?: return@mapNotNull null
                    val (kind, weight) = WorkoutLoadPolicy.reconcile(
                        exercise.loadCapability,
                        entry.loadKind.toLoadKind(),
                        entry.weightKg
                    )
                    RoutineEntry(
                        exerciseId = exercise.id,
                        sets = entry.sets,
                        reps = entry.reps,
                        weightKg = weight,
                        loadKind = kind
                    )
                }
                // A slot whose identity is unresolved would be dropped; that blocks import above.
                if (entries.size != workout.entries.size) return@mapNotNull null
                RoutineWorkout(
                    position = 0,
                    name = workout.name,
                    focus = workout.focus?.let { SplitFocus.valueOf(it) },
                    entries = entries
                )
            }
            if (workouts.size != routine.workouts.size) return@mapNotNull null
            val draft = RoutineTemplate(
                name = routine.name,
                workouts = workouts.mapIndexed { index, workout -> workout.copy(position = index) }
            )
            runCatching { RoutineTemplateValidator.validate(draft) }.getOrNull()
        }
        if (routines.size != document.routines.size) {
            problem(PrescriptionImportIssue.UNDECIDED_IDENTITY)
        }
        return PrescriptionImportPreview(
            document = document,
            identities = identities,
            routines = routines,
            customExercisesToCreate = toCreate,
            problems = problems.distinct()
        )
    }

    /** The exercise the choice actually points at, or null when the choice is missing or unusable. */
    private fun chosenExercise(
        resolution: PrescriptionResolution,
        choice: PrescriptionIdentityChoice?,
        localById: Map<String, Exercise>
    ): Exercise? = when (resolution) {
        is PrescriptionResolution.Existing ->
            (choice as? PrescriptionIdentityChoice.MapTo)?.let { localById[it.localExerciseId] }
                ?: resolution.exercise
        is PrescriptionResolution.Create -> when (choice) {
            PrescriptionIdentityChoice.Create -> resolution.definition.toExercise()
            is PrescriptionIdentityChoice.MapTo -> localById[choice.localExerciseId]
            else -> null
        }
        is PrescriptionResolution.NameCollision ->
            (choice as? PrescriptionIdentityChoice.MapTo)?.let { localById[it.localExerciseId] }
        is PrescriptionResolution.Unknown ->
            (choice as? PrescriptionIdentityChoice.MapTo)?.let { localById[it.localExerciseId] }
    }

    private fun missingEquipment(
        exercise: Exercise,
        selected: Set<EquipmentTag>
    ): Set<EquipmentTag> = exercise.requiredEquipment
        .filterNot { it == EquipmentTag.BODYWEIGHT }
        .filterNot { it in selected }
        .toSet()

    private data class SourceExercise(val id: String, val name: String, val occurrences: Int)
}

/** Trims, collapses whitespace and lowercases, matching the catalog's own collision rule. */
private fun normalizeName(name: String): String =
    name.trim().replace(Regex("\\s+"), " ").lowercase()

private fun PrescriptionExerciseRecord.toExercise(): Exercise = Exercise(
    id = id,
    name = name,
    requiredEquipment = requiredEquipment.map { EquipmentTag(it) }.toSet(),
    primaryMuscles = emptySet(),
    movementPattern = MovementPattern.valueOf(movementPattern),
    isCustom = true,
    isUnilateral = isUnilateral,
    loadCapability = ExerciseLoadCapability.valueOf(loadCapability),
    involvements = involvements.orEmpty().entries.associate { (muscle, weight) ->
        MuscleGroup.valueOf(muscle) to weight
    }
)

private fun String.toLoadKind(): LoadKind =
    runCatching { LoadKind.valueOf(this) }.getOrDefault(LoadKind.LEGACY_UNSPECIFIED)

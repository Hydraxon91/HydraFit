package com.hydrafit.app.core.exercisecontent

import hydrafit.core.exercisecontent.generated.resources.Res
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_back_squat_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_back_squat_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_barbell_bench_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_barbell_bench_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_bench_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_bench_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_curl_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_curl_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_row_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_row_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_goblet_squat_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_goblet_squat_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_hanging_leg_raise_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_hanging_leg_raise_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_lat_pulldown_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_lat_pulldown_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_lateral_raise_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_lateral_raise_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_leg_curl_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_leg_curl_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_overhead_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_overhead_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_push_up_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_push_up_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_romanian_deadlift_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_romanian_deadlift_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_seated_cable_row_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_seated_cable_row_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_standing_calf_raise_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_standing_calf_raise_machine_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_standing_calf_raise_machine_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_standing_calf_raise_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_triceps_pushdown_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_triceps_pushdown_setup
import org.jetbrains.compose.resources.StringResource

/**
 * The resource keys for one authored exercise instruction. Kept apart from the resolved text so the
 * catalog can be enumerated and validated without a resource environment.
 */
internal data class ExerciseInstructionKeys(
    val setup: StringResource,
    val execution: StringResource
)

/**
 * Bundled offline instruction text for seeded exercises, keyed by the catalog's stable exercise ID.
 *
 * This is app-owned catalog content, not user data: it ships with the app, is never written to the
 * database and is not part of a backup, a shared plan or a frozen prescription. Exercises with no
 * bundled entry — custom exercises and seeded exercises outside the authored batches — resolve to
 * null so a caller shows the explicit missing-content state instead of invented guidance. Lookup is
 * by ID only; a name never re-derives an identity.
 */
internal object ExerciseInstructionSource {

    /**
     * The approved 0.7.4 first batch. Membership means the exercise is in scope for authoring, not
     * that text exists yet: only a [keysByExerciseId] entry resolves to an instruction.
     */
    val approvedBatch: Set<String> = setOf(
        "barbell-bench-press",
        "dumbbell-bench-press",
        "push-up",
        "overhead-press",
        "dumbbell-row",
        "lat-pulldown",
        "seated-cable-row",
        "lateral-raise",
        "back-squat",
        "goblet-squat",
        "romanian-deadlift",
        "leg-curl",
        "dumbbell-curl",
        "triceps-pushdown",
        "standing-calf-raise",
        "standing-calf-raise-machine",
        "hanging-leg-raise"
    )

    /**
     * Authored instructions, added batch by batch. Every entry must belong to [approvedBatch]; an
     * exercise outside the approved batch must not gain bundled text.
     */
    val keysByExerciseId: Map<String, ExerciseInstructionKeys> = mapOf(
        "barbell-bench-press" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_barbell_bench_press_setup,
            execution = Res.string.exercise_instruction_barbell_bench_press_execution
        ),
        "dumbbell-bench-press" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_dumbbell_bench_press_setup,
            execution = Res.string.exercise_instruction_dumbbell_bench_press_execution
        ),
        "push-up" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_push_up_setup,
            execution = Res.string.exercise_instruction_push_up_execution
        ),
        "overhead-press" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_overhead_press_setup,
            execution = Res.string.exercise_instruction_overhead_press_execution
        ),
        "dumbbell-row" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_dumbbell_row_setup,
            execution = Res.string.exercise_instruction_dumbbell_row_execution
        ),
        "lat-pulldown" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_lat_pulldown_setup,
            execution = Res.string.exercise_instruction_lat_pulldown_execution
        ),
        "seated-cable-row" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_seated_cable_row_setup,
            execution = Res.string.exercise_instruction_seated_cable_row_execution
        ),
        "lateral-raise" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_lateral_raise_setup,
            execution = Res.string.exercise_instruction_lateral_raise_execution
        ),
        "back-squat" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_back_squat_setup,
            execution = Res.string.exercise_instruction_back_squat_execution
        ),
        "goblet-squat" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_goblet_squat_setup,
            execution = Res.string.exercise_instruction_goblet_squat_execution
        ),
        "romanian-deadlift" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_romanian_deadlift_setup,
            execution = Res.string.exercise_instruction_romanian_deadlift_execution
        ),
        "leg-curl" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_leg_curl_setup,
            execution = Res.string.exercise_instruction_leg_curl_execution
        ),
        "dumbbell-curl" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_dumbbell_curl_setup,
            execution = Res.string.exercise_instruction_dumbbell_curl_execution
        ),
        "triceps-pushdown" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_triceps_pushdown_setup,
            execution = Res.string.exercise_instruction_triceps_pushdown_execution
        ),
        "standing-calf-raise" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_standing_calf_raise_setup,
            execution = Res.string.exercise_instruction_standing_calf_raise_execution
        ),
        "standing-calf-raise-machine" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_standing_calf_raise_machine_setup,
            execution = Res.string.exercise_instruction_standing_calf_raise_machine_execution
        ),
        "hanging-leg-raise" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_hanging_leg_raise_setup,
            execution = Res.string.exercise_instruction_hanging_leg_raise_execution
        )
    )

    /** Keys for [exerciseId], or null when there is no authored instruction (including blank IDs). */
    fun keysFor(exerciseId: String?): ExerciseInstructionKeys? = exerciseId
        ?.takeIf { it.isNotBlank() }
        ?.let(keysByExerciseId::get)
}

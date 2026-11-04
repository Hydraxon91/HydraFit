package com.hydrafit.app.core.exercisecontent

import hydrafit.core.exercisecontent.generated.resources.Res
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_ab_roll_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_ab_roll_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_back_squat_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_back_squat_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_band_chest_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_band_chest_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_band_curl_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_band_curl_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_band_pull_apart_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_band_pull_apart_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_band_row_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_band_row_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_barbell_bench_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_barbell_bench_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_barbell_curl_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_barbell_curl_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_barbell_row_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_barbell_row_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_barbell_shoulder_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_barbell_shoulder_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_bench_press_with_chains_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_bench_press_with_chains_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_bodyweight_calf_raise_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_bodyweight_calf_raise_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_bodyweight_squat_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_bodyweight_squat_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_bulgarian_split_squat_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_bulgarian_split_squat_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_cable_chest_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_cable_chest_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_cable_crunch_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_cable_crunch_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_cable_fly_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_cable_fly_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_cable_lateral_raise_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_cable_lateral_raise_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_cable_shoulder_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_cable_shoulder_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_chin_up_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_chin_up_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_close_grip_bench_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_close_grip_bench_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_close_grip_pulldown_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_close_grip_pulldown_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_conventional_deadlift_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_conventional_deadlift_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_decline_barbell_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_decline_barbell_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dip_machine_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dip_machine_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dips_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dips_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_double_kettlebell_push_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_double_kettlebell_push_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_bench_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_bench_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_curl_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_curl_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_front_to_lateral_raise_combo_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_front_to_lateral_raise_combo_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_lunge_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_lunge_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_rdl_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_rdl_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_row_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_row_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_shoulder_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_shoulder_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_triceps_overhead_extension_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_dumbbell_triceps_overhead_extension_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_ez_bar_upright_row_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_ez_bar_upright_row_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_face_pull_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_face_pull_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_front_squat_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_front_squat_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_glute_bridge_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_glute_bridge_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_goblet_squat_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_goblet_squat_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_hammer_curl_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_hammer_curl_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_handstand_push_ups_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_handstand_push_ups_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_hanging_leg_raise_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_hanging_leg_raise_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_hip_thrust_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_hip_thrust_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_incline_barbell_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_incline_barbell_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_incline_dumbbell_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_incline_dumbbell_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_incline_push_up_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_incline_push_up_medium_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_incline_push_up_medium_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_incline_push_up_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_incline_push_up_wide_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_incline_push_up_wide_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_kettlebell_swing_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_kettlebell_swing_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_lat_pulldown_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_lat_pulldown_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_lateral_raise_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_lateral_raise_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_leg_curl_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_leg_curl_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_leg_extension_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_leg_extension_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_leg_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_leg_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_overhead_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_overhead_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_overhead_triceps_extension_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_overhead_triceps_extension_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_plank_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_plank_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_preacher_curl_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_preacher_curl_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_pull_up_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_pull_up_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_push_press_behind_the_neck_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_push_press_behind_the_neck_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_push_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_push_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_push_up_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_push_up_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_push_up_to_side_plank_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_push_up_to_side_plank_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_push_up_wide_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_push_up_wide_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_romanian_deadlift_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_romanian_deadlift_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_seated_cable_row_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_seated_cable_row_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_seated_cable_shoulder_press_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_seated_cable_shoulder_press_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_seated_ez_bar_curl_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_seated_ez_bar_curl_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_single_arm_push_up_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_single_arm_push_up_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_standing_calf_raise_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_standing_calf_raise_machine_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_standing_calf_raise_machine_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_standing_calf_raise_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_standing_dumbbell_side_bend_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_standing_dumbbell_side_bend_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_supinated_curl_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_supinated_curl_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_trap_bar_deadlift_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_trap_bar_deadlift_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_triceps_pushdown_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_triceps_pushdown_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_wide_grip_pulldown_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_wide_grip_pulldown_setup
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
        "hanging-leg-raise",
        "incline-dumbbell-press",
        "incline-push-up",
        "cable-fly",
        "band-chest-press",
        "dips",
        "barbell-row",
        "pull-up",
        "chin-up",
        "band-row",
        "band-pull-apart",
        "dumbbell-shoulder-press",
        "cable-lateral-raise",
        "face-pull",
        "barbell-curl",
        "band-curl",
        "overhead-triceps-extension",
        "close-grip-bench-press",
        "front-squat",
        "bodyweight-squat",
        "bulgarian-split-squat",
        "leg-press",
        "dumbbell-rdl",
        "conventional-deadlift",
        "hip-thrust",
        "glute-bridge",
        "kettlebell-swing",
        "bodyweight-calf-raise",
        "plank",
        "cable-crunch",
        "hammer-curl",
        "supinated-curl",
        "preacher-curl",
        "close-grip-pulldown",
        "wide-grip-pulldown",
        "incline-barbell-press",
        "decline-barbell-press",
        "ez-bar-upright-row",
        "seated-ez-bar-curl",
        "trap-bar-deadlift",
        "dumbbell-lunge",
        "barbell-shoulder-press",
        "ab-roll",
        "leg-extension",
        "standing-dumbbell-side-bend",
        "dumbbell-triceps-overhead-extension",
        "dumbbell-front-to-lateral-raise-combo",
        "bench-press-with-chains",
        "cable-chest-press",
        "handstand-push-ups",
        "incline-push-up-medium",
        "incline-push-up-wide",
        "push-up-to-side-plank",
        "push-up-wide",
        "single-arm-push-up",
        "cable-shoulder-press",
        "dip-machine",
        "double-kettlebell-push-press",
        "push-press",
        "push-press-behind-the-neck",
        "seated-cable-shoulder-press"
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
        ),
        "incline-dumbbell-press" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_incline_dumbbell_press_setup,
            execution = Res.string.exercise_instruction_incline_dumbbell_press_execution
        ),
        "incline-push-up" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_incline_push_up_setup,
            execution = Res.string.exercise_instruction_incline_push_up_execution
        ),
        "cable-fly" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_cable_fly_setup,
            execution = Res.string.exercise_instruction_cable_fly_execution
        ),
        "band-chest-press" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_band_chest_press_setup,
            execution = Res.string.exercise_instruction_band_chest_press_execution
        ),
        "dips" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_dips_setup,
            execution = Res.string.exercise_instruction_dips_execution
        ),
        "barbell-row" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_barbell_row_setup,
            execution = Res.string.exercise_instruction_barbell_row_execution
        ),
        "pull-up" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_pull_up_setup,
            execution = Res.string.exercise_instruction_pull_up_execution
        ),
        "chin-up" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_chin_up_setup,
            execution = Res.string.exercise_instruction_chin_up_execution
        ),
        "band-row" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_band_row_setup,
            execution = Res.string.exercise_instruction_band_row_execution
        ),
        "band-pull-apart" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_band_pull_apart_setup,
            execution = Res.string.exercise_instruction_band_pull_apart_execution
        ),
        "dumbbell-shoulder-press" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_dumbbell_shoulder_press_setup,
            execution = Res.string.exercise_instruction_dumbbell_shoulder_press_execution
        ),
        "cable-lateral-raise" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_cable_lateral_raise_setup,
            execution = Res.string.exercise_instruction_cable_lateral_raise_execution
        ),
        "face-pull" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_face_pull_setup,
            execution = Res.string.exercise_instruction_face_pull_execution
        ),
        "barbell-curl" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_barbell_curl_setup,
            execution = Res.string.exercise_instruction_barbell_curl_execution
        ),
        "band-curl" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_band_curl_setup,
            execution = Res.string.exercise_instruction_band_curl_execution
        ),
        "overhead-triceps-extension" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_overhead_triceps_extension_setup,
            execution = Res.string.exercise_instruction_overhead_triceps_extension_execution
        ),
        "close-grip-bench-press" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_close_grip_bench_press_setup,
            execution = Res.string.exercise_instruction_close_grip_bench_press_execution
        ),
        "front-squat" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_front_squat_setup,
            execution = Res.string.exercise_instruction_front_squat_execution
        ),
        "bodyweight-squat" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_bodyweight_squat_setup,
            execution = Res.string.exercise_instruction_bodyweight_squat_execution
        ),
        "bulgarian-split-squat" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_bulgarian_split_squat_setup,
            execution = Res.string.exercise_instruction_bulgarian_split_squat_execution
        ),
        "leg-press" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_leg_press_setup,
            execution = Res.string.exercise_instruction_leg_press_execution
        ),
        "dumbbell-rdl" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_dumbbell_rdl_setup,
            execution = Res.string.exercise_instruction_dumbbell_rdl_execution
        ),
        "conventional-deadlift" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_conventional_deadlift_setup,
            execution = Res.string.exercise_instruction_conventional_deadlift_execution
        ),
        "hip-thrust" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_hip_thrust_setup,
            execution = Res.string.exercise_instruction_hip_thrust_execution
        ),
        "glute-bridge" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_glute_bridge_setup,
            execution = Res.string.exercise_instruction_glute_bridge_execution
        ),
        "kettlebell-swing" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_kettlebell_swing_setup,
            execution = Res.string.exercise_instruction_kettlebell_swing_execution
        ),
        "bodyweight-calf-raise" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_bodyweight_calf_raise_setup,
            execution = Res.string.exercise_instruction_bodyweight_calf_raise_execution
        ),
        "plank" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_plank_setup,
            execution = Res.string.exercise_instruction_plank_execution
        ),
        "cable-crunch" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_cable_crunch_setup,
            execution = Res.string.exercise_instruction_cable_crunch_execution
        ),
        "hammer-curl" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_hammer_curl_setup,
            execution = Res.string.exercise_instruction_hammer_curl_execution
        ),
        "supinated-curl" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_supinated_curl_setup,
            execution = Res.string.exercise_instruction_supinated_curl_execution
        ),
        "preacher-curl" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_preacher_curl_setup,
            execution = Res.string.exercise_instruction_preacher_curl_execution
        ),
        "close-grip-pulldown" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_close_grip_pulldown_setup,
            execution = Res.string.exercise_instruction_close_grip_pulldown_execution
        ),
        "wide-grip-pulldown" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_wide_grip_pulldown_setup,
            execution = Res.string.exercise_instruction_wide_grip_pulldown_execution
        ),
        "incline-barbell-press" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_incline_barbell_press_setup,
            execution = Res.string.exercise_instruction_incline_barbell_press_execution
        ),
        "decline-barbell-press" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_decline_barbell_press_setup,
            execution = Res.string.exercise_instruction_decline_barbell_press_execution
        ),
        "ez-bar-upright-row" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_ez_bar_upright_row_setup,
            execution = Res.string.exercise_instruction_ez_bar_upright_row_execution
        ),
        "seated-ez-bar-curl" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_seated_ez_bar_curl_setup,
            execution = Res.string.exercise_instruction_seated_ez_bar_curl_execution
        ),
        "trap-bar-deadlift" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_trap_bar_deadlift_setup,
            execution = Res.string.exercise_instruction_trap_bar_deadlift_execution
        ),
        "dumbbell-lunge" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_dumbbell_lunge_setup,
            execution = Res.string.exercise_instruction_dumbbell_lunge_execution
        ),
        "barbell-shoulder-press" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_barbell_shoulder_press_setup,
            execution = Res.string.exercise_instruction_barbell_shoulder_press_execution
        ),
        "ab-roll" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_ab_roll_setup,
            execution = Res.string.exercise_instruction_ab_roll_execution
        ),
        "leg-extension" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_leg_extension_setup,
            execution = Res.string.exercise_instruction_leg_extension_execution
        ),
        "standing-dumbbell-side-bend" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_standing_dumbbell_side_bend_setup,
            execution = Res.string.exercise_instruction_standing_dumbbell_side_bend_execution
        ),
        "dumbbell-triceps-overhead-extension" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_dumbbell_triceps_overhead_extension_setup,
            execution = Res.string
                .exercise_instruction_dumbbell_triceps_overhead_extension_execution
        ),
        "dumbbell-front-to-lateral-raise-combo" to ExerciseInstructionKeys(
            setup = Res.string
                .exercise_instruction_dumbbell_front_to_lateral_raise_combo_setup,
            execution = Res.string
                .exercise_instruction_dumbbell_front_to_lateral_raise_combo_execution
        ),
        "bench-press-with-chains" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_bench_press_with_chains_setup,
            execution = Res.string.exercise_instruction_bench_press_with_chains_execution
        ),
        "cable-chest-press" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_cable_chest_press_setup,
            execution = Res.string.exercise_instruction_cable_chest_press_execution
        ),
        "handstand-push-ups" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_handstand_push_ups_setup,
            execution = Res.string.exercise_instruction_handstand_push_ups_execution
        ),
        "incline-push-up-medium" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_incline_push_up_medium_setup,
            execution = Res.string.exercise_instruction_incline_push_up_medium_execution
        ),
        "incline-push-up-wide" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_incline_push_up_wide_setup,
            execution = Res.string.exercise_instruction_incline_push_up_wide_execution
        ),
        "push-up-to-side-plank" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_push_up_to_side_plank_setup,
            execution = Res.string.exercise_instruction_push_up_to_side_plank_execution
        ),
        "push-up-wide" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_push_up_wide_setup,
            execution = Res.string.exercise_instruction_push_up_wide_execution
        ),
        "single-arm-push-up" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_single_arm_push_up_setup,
            execution = Res.string.exercise_instruction_single_arm_push_up_execution
        ),
        "cable-shoulder-press" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_cable_shoulder_press_setup,
            execution = Res.string.exercise_instruction_cable_shoulder_press_execution
        ),
        "dip-machine" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_dip_machine_setup,
            execution = Res.string.exercise_instruction_dip_machine_execution
        ),
        "double-kettlebell-push-press" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_double_kettlebell_push_press_setup,
            execution = Res.string.exercise_instruction_double_kettlebell_push_press_execution
        ),
        "push-press" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_push_press_setup,
            execution = Res.string.exercise_instruction_push_press_execution
        ),
        "push-press-behind-the-neck" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_push_press_behind_the_neck_setup,
            execution = Res.string.exercise_instruction_push_press_behind_the_neck_execution
        ),
        "seated-cable-shoulder-press" to ExerciseInstructionKeys(
            setup = Res.string.exercise_instruction_seated_cable_shoulder_press_setup,
            execution = Res.string.exercise_instruction_seated_cable_shoulder_press_execution
        )
    )

    /** Keys for [exerciseId], or null when there is no authored instruction (including blank IDs). */
    fun keysFor(exerciseId: String?): ExerciseInstructionKeys? = exerciseId
        ?.takeIf { it.isNotBlank() }
        ?.let(keysByExerciseId::get)
}

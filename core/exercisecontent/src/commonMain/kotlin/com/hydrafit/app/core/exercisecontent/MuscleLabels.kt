package com.hydrafit.app.core.exercisecontent

import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import hydrafit.core.exercisecontent.generated.resources.Res
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_abs
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_adductors
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_biceps
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_calves
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_chest_lower
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_chest_upper
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_forearms
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_front_delts
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_glutes
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_hamstrings
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_hip_abductors
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_hip_flexors
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_lats
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_lower_back
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_neck
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_obliques
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_quads
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_rear_delts
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_serratus_anterior
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_side_delts
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_tibialis_anterior
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_traps
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_triceps
import hydrafit.core.exercisecontent.generated.resources.exercise_muscle_upper_back
import org.jetbrains.compose.resources.StringResource

/** The display name of a muscle group, shared by every screen that names muscles. */
fun MuscleGroup.labelResource(): StringResource = when (this) {
    MuscleGroup.CHEST_UPPER -> Res.string.exercise_muscle_chest_upper
    MuscleGroup.CHEST_LOWER -> Res.string.exercise_muscle_chest_lower
    MuscleGroup.LATS -> Res.string.exercise_muscle_lats
    MuscleGroup.UPPER_BACK -> Res.string.exercise_muscle_upper_back
    MuscleGroup.LOWER_BACK -> Res.string.exercise_muscle_lower_back
    MuscleGroup.FRONT_DELTS -> Res.string.exercise_muscle_front_delts
    MuscleGroup.SIDE_DELTS -> Res.string.exercise_muscle_side_delts
    MuscleGroup.REAR_DELTS -> Res.string.exercise_muscle_rear_delts
    MuscleGroup.BICEPS -> Res.string.exercise_muscle_biceps
    MuscleGroup.TRICEPS -> Res.string.exercise_muscle_triceps
    MuscleGroup.FOREARMS -> Res.string.exercise_muscle_forearms
    MuscleGroup.ABS -> Res.string.exercise_muscle_abs
    MuscleGroup.OBLIQUES -> Res.string.exercise_muscle_obliques
    MuscleGroup.QUADS -> Res.string.exercise_muscle_quads
    MuscleGroup.HAMSTRINGS -> Res.string.exercise_muscle_hamstrings
    MuscleGroup.GLUTES -> Res.string.exercise_muscle_glutes
    MuscleGroup.CALVES -> Res.string.exercise_muscle_calves
    MuscleGroup.ADDUCTORS -> Res.string.exercise_muscle_adductors
    MuscleGroup.HIP_ABDUCTORS -> Res.string.exercise_muscle_hip_abductors
    MuscleGroup.TRAPS -> Res.string.exercise_muscle_traps
    MuscleGroup.NECK -> Res.string.exercise_muscle_neck
    MuscleGroup.TIBIALIS_ANTERIOR -> Res.string.exercise_muscle_tibialis_anterior
    MuscleGroup.SERRATUS_ANTERIOR -> Res.string.exercise_muscle_serratus_anterior
    MuscleGroup.HIP_FLEXORS -> Res.string.exercise_muscle_hip_flexors
}

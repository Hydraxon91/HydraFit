package com.hydrafit.app.feature.logger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.ExercisePerformance
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.exercisecontent.ExerciseDetailsContent
import com.hydrafit.app.core.exercisecontent.ExerciseDetailsHistory
import com.hydrafit.app.core.exercisecontent.ExerciseInstructionDialog
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.logger_history_set_duration_line
import hydrafit.feature.logger.generated.resources.logger_history_set_line
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The instructions dialog for an exercise on Train, with its muscles and what was done last time.
 * It reads history but writes nothing, so dismissing it leaves the draft, timer and form alone.
 */
@Composable
internal fun LoggerExerciseDetailsDialog(
    instructions: LoggerExerciseInstructions,
    weightUnit: WeightUnit,
    utcOffsetMillis: Long,
    onDismiss: () -> Unit
) {
    val viewModel = koinViewModel<ExerciseDetailsViewModel>()
    LaunchedEffect(instructions.exerciseId) { viewModel.load(instructions.exerciseId) }
    val loaded by viewModel.details.collectAsState()
    val details = loaded?.let { exerciseDetails ->
        ExerciseDetailsContent(
            primaryMuscles = exerciseDetails.primaryMuscles,
            secondaryMuscles = exerciseDetails.secondaryMuscles,
            history = historyFor(exerciseDetails.performance, weightUnit, utcOffsetMillis)
        )
    }
    ExerciseInstructionDialog(
        exerciseName = instructions.exerciseName,
        exerciseId = instructions.exerciseId,
        onDismiss = onDismiss,
        details = details
    )
}

@Composable
private fun historyFor(
    performance: ExercisePerformance,
    weightUnit: WeightUnit,
    utcOffsetMillis: Long
): ExerciseDetailsHistory {
    val at = performance.lastPerformedAtMillis ?: return ExerciseDetailsHistory.None
    return ExerciseDetailsHistory.Logged(
        dateLabel = localDateLabel(localDateTimeParts(at, utcOffsetMillis)),
        setLines = performance.lastSessionSets.map { setLine(it, weightUnit) },
        heaviestLine = performance.heaviestSet?.let { setLine(it, weightUnit) }
    )
}

@Composable
private fun setLine(set: WorkoutSet, weightUnit: WeightUnit): String {
    val weight = loadWeightText(set.loadKind, set.weightKg, weightUnit)
    return when {
        set.reps != null -> stringResource(Res.string.logger_history_set_line, set.reps!!, weight)
        set.durationSeconds != null ->
            stringResource(
                Res.string.logger_history_set_duration_line,
                set.durationSeconds!!,
                weight
            )
        else -> weight
    }
}

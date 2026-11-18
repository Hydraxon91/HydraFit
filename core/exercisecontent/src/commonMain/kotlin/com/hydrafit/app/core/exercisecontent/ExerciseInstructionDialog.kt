package com.hydrafit.app.core.exercisecontent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import hydrafit.core.exercisecontent.generated.resources.Res
import hydrafit.core.exercisecontent.generated.resources.exercise_details_heaviest
import hydrafit.core.exercisecontent.generated.resources.exercise_details_last_time
import hydrafit.core.exercisecontent.generated.resources.exercise_details_no_history
import hydrafit.core.exercisecontent.generated.resources.exercise_details_primary_muscles
import hydrafit.core.exercisecontent.generated.resources.exercise_details_secondary_muscles
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_close
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_demo
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_technique_considerations
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * The read-only offline instructions for one exercise, selected by its stable catalog ID.
 *
 * [exerciseName] is the name the owning feature already displays, so the dialog never re-derives an
 * identity from a name. An exercise without bundled instructions shows the explicit unavailable
 * state rather than generic guidance. Bundled technique considerations, when the ID has them, are
 * appended in their own section and are independent of the instruction text: an exercise can carry
 * considerations with no authored instructions. The dialog presents content only: it holds no state
 * and writes nothing, so an owning feature's unsaved draft is unaffected by opening or dismissing it.
 */
@Composable
fun ExerciseInstructionDialog(
    exerciseName: String,
    exerciseId: String?,
    onDismiss: () -> Unit,
    details: ExerciseDetailsContent? = null
) {
    val keys = ExerciseInstructionSource.keysFor(exerciseId)
    val caution = ExerciseCautionSource.cautionFor(exerciseId)
    val uriHandler = LocalUriHandler.current
    val demoUrl = ExerciseDemoLink.urlFor(exerciseId, exerciseName)
    val curated = ExerciseDemoLink.curatedUrl(exerciseId) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.exercise_instruction_close))
            }
        },
        dismissButton = demoUrl?.let { url ->
            {
                val label = stringResource(Res.string.exercise_instruction_demo)
                if (curated) {
                    // A hand-picked video is the point of the dialog for this exercise, so it is
                    // the prominent action; a plain search stays a quiet text button.
                    FilledTonalButton(onClick = { uriHandler.openDemo(url) }) { Text(label) }
                } else {
                    TextButton(onClick = { uriHandler.openDemo(url) }) { Text(label) }
                }
            }
        },
        title = { Text(exerciseName) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (details != null) ExerciseDetailsSection(details)
                if (keys == null && caution == null) {
                    Text(stringResource(Res.string.exercise_instruction_unavailable))
                } else {
                    if (keys == null) {
                        Text(stringResource(Res.string.exercise_instruction_unavailable))
                    } else {
                        ContentSection(
                            heading = stringResource(Res.string.exercise_instruction_setup),
                            body = stringResource(keys.setup)
                        )
                        ContentSection(
                            heading = stringResource(Res.string.exercise_instruction_execution),
                            body = stringResource(keys.execution)
                        )
                    }
                    if (caution != null) {
                        ContentSection(
                            heading = stringResource(
                                Res.string.exercise_instruction_technique_considerations
                            ),
                            body = stringResource(caution)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun muscleNames(muscles: List<MuscleGroup>): String {
    val labels = mutableListOf<String>()
    muscles.forEach { labels += stringResource(it.labelResource()) }
    return labels.joinToString(", ")
}

@Composable
private fun ExerciseDetailsSection(details: ExerciseDetailsContent) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (details.primaryMuscles.isNotEmpty()) {
            Text(
                text = stringResource(
                    Res.string.exercise_details_primary_muscles,
                    muscleNames(details.primaryMuscles)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (details.secondaryMuscles.isNotEmpty()) {
            Text(
                text = stringResource(
                    Res.string.exercise_details_secondary_muscles,
                    muscleNames(details.secondaryMuscles)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        when (val history = details.history) {
            ExerciseDetailsHistory.Hidden -> Unit
            ExerciseDetailsHistory.None -> Text(
                text = stringResource(Res.string.exercise_details_no_history),
                style = MaterialTheme.typography.bodyMedium
            )
            is ExerciseDetailsHistory.Logged -> {
                Text(
                    text = stringResource(Res.string.exercise_details_last_time, history.dateLabel),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = history.setLines.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium
                )
                history.heaviestLine?.let { line ->
                    Text(
                        text = stringResource(Res.string.exercise_details_heaviest, line),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentSection(heading: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
    }
}

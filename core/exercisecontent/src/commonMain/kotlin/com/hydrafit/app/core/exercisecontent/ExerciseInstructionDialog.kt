package com.hydrafit.app.core.exercisecontent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import hydrafit.core.exercisecontent.generated.resources.Res
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_close
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
fun ExerciseInstructionDialog(exerciseName: String, exerciseId: String?, onDismiss: () -> Unit) {
    val keys = ExerciseInstructionSource.keysFor(exerciseId)
    val caution = ExerciseCautionSource.cautionFor(exerciseId)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.exercise_instruction_close))
            }
        },
        title = { Text(exerciseName) },
        text = {
            if (keys == null && caution == null) {
                Text(stringResource(Res.string.exercise_instruction_unavailable))
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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

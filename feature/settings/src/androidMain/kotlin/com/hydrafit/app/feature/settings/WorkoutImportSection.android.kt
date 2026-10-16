package com.hydrafit.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_workout_import_choose_file
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun WorkoutImportSection(
    isSupported: Boolean,
    busy: Boolean,
    onFileChosen: (String) -> Unit,
    modifier: Modifier
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onFileChosen(it.toString()) } }

    OutlinedButton(
        onClick = {
            picker.launch(
                arrayOf(
                    "text/csv",
                    "text/comma-separated-values",
                    "text/plain",
                    "*/*"
                )
            )
        },
        enabled = isSupported && !busy,
        modifier = modifier.testTag(WorkoutImportTestTags.CHOOSE_FILE)
    ) {
        Text(stringResource(Res.string.settings_workout_import_choose_file))
    }
}

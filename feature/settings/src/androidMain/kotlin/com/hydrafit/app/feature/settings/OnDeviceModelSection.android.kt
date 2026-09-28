package com.hydrafit.app.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hydrafit.app.core.llm.AndroidOnDeviceModelManager
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_local_llm_unavailable
import hydrafit.feature.settings.generated.resources.settings_model_import
import hydrafit.feature.settings.generated.resources.settings_model_installed
import hydrafit.feature.settings.generated.resources.settings_model_remove
import hydrafit.feature.settings.generated.resources.settings_model_section
import hydrafit.feature.settings.generated.resources.settings_model_terms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
actual fun OnDeviceModelSection(
    installed: Boolean,
    onModelChanged: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val manager = koinInject<AndroidOnDeviceModelManager>()
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { manager.importFromUri(uri) } }
                onModelChanged()
            }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.settings_model_section),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = if (installed) {
                stringResource(Res.string.settings_model_installed)
            } else {
                stringResource(Res.string.settings_local_llm_unavailable)
            },
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (installed) {
                OutlinedButton(onClick = {
                    manager.remove()
                    onModelChanged()
                }) {
                    Text(stringResource(Res.string.settings_model_remove))
                }
            } else {
                Button(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Text(stringResource(Res.string.settings_model_import))
                }
            }
        }
        TextButton(onClick = { context.openGemmaTerms() }) {
            Text(stringResource(Res.string.settings_model_terms))
        }
    }
}

private fun Context.openGemmaTerms() {
    startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.google.dev/gemma/terms"))
    )
}

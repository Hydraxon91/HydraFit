package com.hydrafit.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_local_llm_unavailable
import hydrafit.feature.settings.generated.resources.settings_model_section
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun OnDeviceModelSection(
    installed: Boolean,
    onModelChanged: () -> Unit,
    modifier: Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.settings_model_section),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(Res.string.settings_local_llm_unavailable),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

package com.hydrafit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.userdata.settings.AppLanguage
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_home_language
import hydrafit.feature.settings.generated.resources.settings_language_hint
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LanguageRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LanguageSettingsViewModel = koinViewModel()
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    LanguageScreen(
        language = language,
        onLanguageSelected = viewModel::onLanguageSelected,
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
fun LanguageScreen(
    language: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsPageHeader(
            title = stringResource(Res.string.settings_home_language),
            onBack = onBack
        )
        Text(
            text = stringResource(Res.string.settings_language_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppLanguage.entries.forEach { entry ->
            SettingsRadioRow(
                label = stringResource(entry.labelResource()),
                selected = language == entry,
                onSelected = { onLanguageSelected(entry) }
            )
        }
    }
}

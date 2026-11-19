package com.hydrafit.app.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.ui.appColorScheme
import com.hydrafit.app.core.userdata.settings.AppColorMode
import com.hydrafit.app.core.userdata.settings.AppThemeId
import com.hydrafit.app.core.userdata.settings.AppThemePreference
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_appearance_hint
import hydrafit.feature.settings.generated.resources.settings_appearance_mode_section
import hydrafit.feature.settings.generated.resources.settings_appearance_theme_section
import hydrafit.feature.settings.generated.resources.settings_home_appearance
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppearanceRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppearanceSettingsViewModel = koinViewModel()
) {
    val preference by viewModel.preference.collectAsStateWithLifecycle()
    AppearanceScreen(
        preference = preference,
        onThemeSelected = viewModel::onThemeSelected,
        onColorModeSelected = viewModel::onColorModeSelected,
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
fun AppearanceScreen(
    preference: AppThemePreference,
    onThemeSelected: (AppThemeId) -> Unit,
    onColorModeSelected: (AppColorMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val previewDark = when (preference.colorMode) {
        AppColorMode.SYSTEM -> isSystemInDarkTheme()
        AppColorMode.LIGHT -> false
        AppColorMode.DARK -> true
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsPageHeader(
            title = stringResource(Res.string.settings_home_appearance),
            onBack = onBack
        )
        Text(
            text = stringResource(Res.string.settings_appearance_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(Res.string.settings_appearance_mode_section),
            style = MaterialTheme.typography.titleMedium
        )
        AppColorMode.entries.forEach { mode ->
            SettingsRadioRow(
                label = stringResource(mode.labelResource()),
                selected = preference.colorMode == mode,
                onSelected = { onColorModeSelected(mode) }
            )
        }
        Text(
            text = stringResource(Res.string.settings_appearance_theme_section),
            style = MaterialTheme.typography.titleMedium
        )
        AppThemeId.entries.forEach { theme ->
            ThemeOptionRow(
                theme = theme,
                previewDark = previewDark,
                selected = preference.theme == theme,
                onSelected = { onThemeSelected(theme) }
            )
        }
    }
}

/**
 * One theme drawn in its own colours, so the choice is seen rather than described. The row is one
 * radio-style target; the swatches are decorative and the name carries the meaning.
 */
@Composable
private fun ThemeOptionRow(
    theme: AppThemeId,
    previewDark: Boolean,
    selected: Boolean,
    onSelected: () -> Unit
) {
    val scheme = appColorScheme(theme, previewDark)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelected),
        shape = MaterialTheme.shapes.medium,
        color = scheme.surface,
        border = BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) scheme.primary else scheme.outline
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(theme.labelResource()),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier.clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Swatch(scheme.primary)
                Swatch(scheme.secondary)
                Swatch(scheme.tertiary)
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun Swatch(color: Color) {
    Spacer(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color)
    )
}

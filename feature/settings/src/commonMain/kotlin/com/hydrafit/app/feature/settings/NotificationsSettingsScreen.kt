package com.hydrafit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_home_notifications
import org.jetbrains.compose.resources.stringResource

@Composable
fun NotificationsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    NotificationsScreen(onBack = onBack, modifier = modifier)
}

@Composable
fun NotificationsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsPageHeader(
            title = stringResource(Res.string.settings_home_notifications),
            onBack = onBack
        )
        TrainingGuidanceSettingsSection()
        WorkoutReminderSettingsSection()
    }
}

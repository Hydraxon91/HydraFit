package com.hydrafit.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.hydrafit.app.core.navigation.FeatureDestination
import com.hydrafit.app.core.navigation.LocalSetupNavigator
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.nav_label
import hydrafit.feature.settings.generated.resources.settings_back
import hydrafit.feature.settings.generated.resources.settings_home_about
import hydrafit.feature.settings.generated.resources.settings_home_about_description
import hydrafit.feature.settings.generated.resources.settings_home_backup_and_data
import hydrafit.feature.settings.generated.resources.settings_home_backup_and_data_description
import hydrafit.feature.settings.generated.resources.settings_home_notifications
import hydrafit.feature.settings.generated.resources.settings_home_notifications_description
import hydrafit.feature.settings.generated.resources.settings_home_planner_engine
import hydrafit.feature.settings.generated.resources.settings_home_units
import hydrafit.feature.settings.generated.resources.settings_home_workout_preferences
import hydrafit.feature.settings.generated.resources.settings_home_workout_preferences_description
import hydrafit.feature.settings.generated.resources.settings_setup
import hydrafit.feature.settings.generated.resources.settings_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

val settingsRoute: String = "settings"

/**
 * Category page routes. Each one is nested under [settingsRoute] so the shell keeps the Settings
 * group selected while a page is open, and so Back from a page returns to the hub.
 */
internal object SettingsRoutes {
    const val HOME = "settings/home"
    const val WORKOUT_PREFERENCES = "settings/workout-preferences"
    const val NOTIFICATIONS = "settings/notifications"
    const val PLANNER_ENGINE = "settings/planner-engine"
    const val UNITS = "settings/units"
    const val BACKUP_AND_DATA = "settings/backup-and-data"
}

val settingsDestination: FeatureDestination = FeatureDestination(
    route = settingsRoute,
    label = Res.string.nav_label,
    graph = { navController -> { settingsGraph(navController) } }
)

fun NavGraphBuilder.settingsGraph(navController: NavController) {
    // A real nested graph keeps the Settings tab selected on its sub-routes (the shell matches
    // destinations by graph hierarchy), so every category page is a child of Settings, not a
    // sibling. Back from a page returns to the hub; Back from the hub closes the overlay.
    navigation(startDestination = SettingsRoutes.HOME, route = settingsRoute) {
        composable(SettingsRoutes.HOME) {
            SettingsHomeRoute(
                onOpenWorkoutPreferences = {
                    navController.navigate(SettingsRoutes.WORKOUT_PREFERENCES)
                },
                onOpenNotifications = { navController.navigate(SettingsRoutes.NOTIFICATIONS) },
                onOpenPlannerEngine = { navController.navigate(SettingsRoutes.PLANNER_ENGINE) },
                onOpenUnits = { navController.navigate(SettingsRoutes.UNITS) },
                onOpenBackupAndData = { navController.navigate(SettingsRoutes.BACKUP_AND_DATA) },
                onOpenAcknowledgments = { navController.navigate(acknowledgmentsRoute) },
                onOpenSetup = LocalSetupNavigator.current,
                onBack = { navController.popBackStack() }
            )
        }
        composable(SettingsRoutes.WORKOUT_PREFERENCES) {
            WorkoutPreferencesRoute(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.NOTIFICATIONS) {
            NotificationsRoute(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.PLANNER_ENGINE) {
            PlannerEngineRoute(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.UNITS) {
            UnitsRoute(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.BACKUP_AND_DATA) {
            BackupAndDataRoute(onBack = { navController.popBackStack() })
        }
        composable(acknowledgmentsRoute) {
            AcknowledgmentsRoute(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
fun SettingsHomeRoute(
    onOpenWorkoutPreferences: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPlannerEngine: () -> Unit,
    onOpenUnits: () -> Unit,
    onOpenBackupAndData: () -> Unit,
    onOpenAcknowledgments: () -> Unit,
    onOpenSetup: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Saves happen on the category pages, so re-read them when the hub is shown again; otherwise
    // its Planner engine and Units summaries could describe the values from before the edit.
    LaunchedEffect(Unit) { viewModel.refresh() }
    SettingsHomeScreen(
        state = state,
        onOpenWorkoutPreferences = onOpenWorkoutPreferences,
        onOpenNotifications = onOpenNotifications,
        onOpenPlannerEngine = onOpenPlannerEngine,
        onOpenUnits = onOpenUnits,
        onOpenBackupAndData = onOpenBackupAndData,
        onOpenAcknowledgments = onOpenAcknowledgments,
        onOpenSetup = onOpenSetup,
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
fun SettingsHomeScreen(
    state: SettingsUiState,
    onOpenWorkoutPreferences: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPlannerEngine: () -> Unit,
    onOpenUnits: () -> Unit,
    onOpenBackupAndData: () -> Unit,
    onOpenAcknowledgments: () -> Unit,
    onOpenSetup: () -> Unit,
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
        SettingsPageHeader(title = stringResource(Res.string.settings_title), onBack = onBack)
        SettingsCategoryRow(
            title = stringResource(Res.string.settings_home_workout_preferences),
            description = stringResource(
                Res.string.settings_home_workout_preferences_description
            ),
            onClick = onOpenWorkoutPreferences
        )
        SettingsCategoryRow(
            title = stringResource(Res.string.settings_home_notifications),
            description = stringResource(Res.string.settings_home_notifications_description),
            onClick = onOpenNotifications
        )
        SettingsCategoryRow(
            title = stringResource(Res.string.settings_home_planner_engine),
            description = state.selectedEngine?.let { stringResource(it.labelResource()) },
            onClick = onOpenPlannerEngine
        )
        SettingsCategoryRow(
            title = stringResource(Res.string.settings_home_units),
            description = stringResource(state.weightUnit.labelResource()) + " · " +
                stringResource(state.distanceUnit.labelResource()),
            onClick = onOpenUnits
        )
        SettingsCategoryRow(
            title = stringResource(Res.string.settings_home_backup_and_data),
            description = stringResource(Res.string.settings_home_backup_and_data_description),
            onClick = onOpenBackupAndData
        )
        SettingsCategoryRow(
            title = stringResource(Res.string.settings_home_about),
            description = stringResource(Res.string.settings_home_about_description),
            onClick = onOpenAcknowledgments
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenSetup) {
            Text(stringResource(Res.string.settings_setup))
        }
    }
}

/**
 * The title row shared by the hub and its category pages. Settings opens over a tab, so it owns
 * its own way out rather than relying on the system Back gesture alone.
 */
@Composable
internal fun SettingsPageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onBack) {
            Text(stringResource(Res.string.settings_back))
        }
    }
}

/** One hub row that opens a category page: its title, then either a live value or a short hint. */
@Composable
private fun SettingsCategoryRow(title: String, description: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        if (description != null) {
            Text(text = description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

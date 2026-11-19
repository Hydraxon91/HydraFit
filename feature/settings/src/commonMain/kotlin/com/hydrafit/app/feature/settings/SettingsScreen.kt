package com.hydrafit.app.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.hydrafit.app.core.navigation.FeatureDestination
import com.hydrafit.app.core.navigation.LocalSetupNavigator
import com.hydrafit.app.core.ui.HubCard
import com.hydrafit.app.core.ui.ScreenHeader
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
 * Category page routes. Each one is nested under [settingsRoute], so Back from a page returns to
 * the hub, while the Settings tab remains selected.
 */
internal object SettingsRoutes {
    const val HOME = "settings/home"
    const val WORKOUT_PREFERENCES = "settings/workout-preferences"
    const val NOTIFICATIONS = "settings/notifications"
    const val PLANNER_ENGINE = "settings/planner-engine"
    const val UNITS = "settings/units"
    const val BACKUP_AND_DATA = "settings/backup-and-data"
    const val LICENSES = "settings/licenses"
}

val settingsDestination: FeatureDestination = FeatureDestination(
    route = settingsRoute,
    label = Res.string.nav_label,
    graph = { navController -> { settingsGraph(navController) } }
)

fun NavGraphBuilder.settingsGraph(navController: NavController) {
    // A real nested graph makes every category page a child of Settings, not a sibling, and keeps
    // Back from a page returning to the hub, with one Settings tab for the entire graph.
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
                onOpenSetup = LocalSetupNavigator.current
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
            AcknowledgmentsRoute(
                onBack = { navController.popBackStack() },
                onOpenLicenses = { navController.navigate(SettingsRoutes.LICENSES) }
            )
        }
        composable(SettingsRoutes.LICENSES) {
            OpenSourceLicensesRoute(onBack = { navController.popBackStack() })
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ScreenHeader(title = stringResource(Res.string.settings_title))
        SettingsCategoryRow(
            title = stringResource(Res.string.settings_home_workout_preferences),
            icon = SettingsCategoryIcon.WORKOUT,
            description = stringResource(
                Res.string.settings_home_workout_preferences_description
            ),
            onClick = onOpenWorkoutPreferences
        )
        SettingsCategoryRow(
            title = stringResource(Res.string.settings_home_notifications),
            icon = SettingsCategoryIcon.NOTIFICATIONS,
            description = stringResource(Res.string.settings_home_notifications_description),
            onClick = onOpenNotifications
        )
        SettingsCategoryRow(
            title = stringResource(Res.string.settings_home_planner_engine),
            icon = SettingsCategoryIcon.PLANNER,
            description = state.selectedEngine?.let { stringResource(it.labelResource()) },
            onClick = onOpenPlannerEngine
        )
        SettingsCategoryRow(
            title = stringResource(Res.string.settings_home_units),
            icon = SettingsCategoryIcon.UNITS,
            description = stringResource(state.weightUnit.labelResource()) + " · " +
                stringResource(state.distanceUnit.labelResource()),
            onClick = onOpenUnits
        )
        SettingsCategoryRow(
            title = stringResource(Res.string.settings_home_backup_and_data),
            icon = SettingsCategoryIcon.DATA,
            description = stringResource(Res.string.settings_home_backup_and_data_description),
            onClick = onOpenBackupAndData
        )
        SettingsCategoryRow(
            title = stringResource(Res.string.settings_home_about),
            icon = SettingsCategoryIcon.ABOUT,
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
 * Category pages carry an explicit way back to the Settings hub.
 */
@Composable
internal fun SettingsPageHeader(title: String, onBack: () -> Unit) {
    ScreenHeader(
        title = title,
        onBack = onBack,
        backLabel = stringResource(Res.string.settings_back)
    )
}

/** One hub card that opens a category page: its title, then either a live value or a short hint. */
@Composable
private fun SettingsCategoryRow(
    title: String,
    icon: SettingsCategoryIcon,
    description: String?,
    onClick: () -> Unit
) {
    HubCard(
        title = title,
        description = description,
        onClick = onClick,
        leading = { SettingsCategoryGlyph(icon) }
    )
}

private enum class SettingsCategoryIcon { WORKOUT, NOTIFICATIONS, PLANNER, UNITS, DATA, ABOUT }

/** One original geometric glyph per category; the title and description carry its meaning. */
@Composable
private fun SettingsCategoryGlyph(icon: SettingsCategoryIcon) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(28.dp).clearAndSetSemantics { }) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round)
            when (icon) {
                SettingsCategoryIcon.WORKOUT -> {
                    drawLine(color, Offset(4f, 8f), Offset(20f, 8f), 1.8f, StrokeCap.Round)
                    drawLine(color, Offset(4f, 16f), Offset(20f, 16f), 1.8f, StrokeCap.Round)
                    drawCircle(color, 2.6f, Offset(9f, 8f))
                    drawCircle(color, 2.6f, Offset(15f, 16f))
                }
                SettingsCategoryIcon.NOTIFICATIONS -> {
                    drawArc(
                        color = color,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(6f, 5f),
                        size = Size(12f, 12f),
                        style = stroke
                    )
                    drawLine(color, Offset(6f, 11f), Offset(5f, 17f), 1.8f, StrokeCap.Round)
                    drawLine(color, Offset(18f, 11f), Offset(19f, 17f), 1.8f, StrokeCap.Round)
                    drawLine(color, Offset(4f, 17f), Offset(20f, 17f), 1.8f, StrokeCap.Round)
                    drawCircle(color, 1.6f, Offset(12f, 20.5f))
                }
                SettingsCategoryIcon.PLANNER -> {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(5f, 5f),
                        size = Size(14f, 14f),
                        cornerRadius = CornerRadius(2.5f, 2.5f),
                        style = stroke
                    )
                    drawCircle(color, 2.4f, Offset(12f, 12f))
                }
                SettingsCategoryIcon.UNITS -> {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(3f, 8f),
                        size = Size(18f, 8f),
                        cornerRadius = CornerRadius(1.5f, 1.5f),
                        style = stroke
                    )
                    listOf(7f, 11f, 15f, 19f).forEach { x ->
                        drawLine(color, Offset(x, 8f), Offset(x, 12f), 1.8f, StrokeCap.Round)
                    }
                }
                SettingsCategoryIcon.DATA -> {
                    listOf(5f, 10f, 15f).forEach { y ->
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(5f, y),
                            size = Size(14f, 4f),
                            cornerRadius = CornerRadius(2f, 2f),
                            style = stroke
                        )
                    }
                }
                SettingsCategoryIcon.ABOUT -> {
                    drawCircle(color, 8f, Offset(12f, 12f), style = stroke)
                    drawLine(color, Offset(12f, 11f), Offset(12f, 17f), 1.8f, StrokeCap.Round)
                    drawCircle(color, 1.2f, Offset(12f, 7.8f))
                }
            }
        }
    }
}

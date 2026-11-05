package com.hydrafit.app.feature.equipment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.hydrafit.app.core.navigation.AppTab
import com.hydrafit.app.core.navigation.FeatureDestination
import com.hydrafit.app.core.navigation.LocalAppTabNavigator
import hydrafit.feature.equipment.generated.resources.Res
import hydrafit.feature.equipment.generated.resources.equipment_section_baselines_description
import hydrafit.feature.equipment.generated.resources.equipment_section_baselines_title
import hydrafit.feature.equipment.generated.resources.equipment_section_catalog_description
import hydrafit.feature.equipment.generated.resources.equipment_section_catalog_title
import hydrafit.feature.equipment.generated.resources.equipment_section_exclusions_description
import hydrafit.feature.equipment.generated.resources.equipment_section_exclusions_title
import hydrafit.feature.equipment.generated.resources.equipment_section_inventory_description
import hydrafit.feature.equipment.generated.resources.equipment_section_inventory_title
import hydrafit.feature.equipment.generated.resources.equipment_section_profile_description
import hydrafit.feature.equipment.generated.resources.equipment_section_profile_title
import hydrafit.feature.equipment.generated.resources.nav_label
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.viewmodel.koinViewModel

val equipmentRoute: String = "equipment"

/** The hub shown when the Equipment destination is opened; every section is a child of it. */
internal val equipmentHomeRoute: String = "equipment/home"

/**
 * The five pages of the Equipment hub. Each one renders an existing content group from the former
 * combined page, and shares the graph-scoped ViewModels with the hub so a page never opens a second
 * independent profile or planning-settings state.
 */
enum class EquipmentSection(
    val route: String,
    val title: StringResource,
    val description: StringResource,
    val contentTag: String
) {
    PROFILE_LOADING(
        route = "equipment/profile-loading",
        title = Res.string.equipment_section_profile_title,
        description = Res.string.equipment_section_profile_description,
        contentTag = "equipment_section_profile_loading_content"
    ),
    INVENTORY_STATIONS(
        route = "equipment/inventory-stations",
        title = Res.string.equipment_section_inventory_title,
        description = Res.string.equipment_section_inventory_description,
        contentTag = "equipment_section_inventory_stations_content"
    ),
    EXERCISE_CATALOG(
        route = "equipment/exercise-catalog",
        title = Res.string.equipment_section_catalog_title,
        description = Res.string.equipment_section_catalog_description,
        contentTag = "equipment_section_exercise_catalog_content"
    ),
    EXCLUSIONS(
        route = "equipment/exclusions",
        title = Res.string.equipment_section_exclusions_title,
        description = Res.string.equipment_section_exclusions_description,
        contentTag = "equipment_section_exclusions_content"
    ),
    BASELINE_RECORDS(
        route = "equipment/baseline-records",
        title = Res.string.equipment_section_baselines_title,
        description = Res.string.equipment_section_baselines_description,
        contentTag = "equipment_section_baseline_records_content"
    )
}

val equipmentDestination: FeatureDestination = FeatureDestination(
    route = equipmentRoute,
    label = Res.string.nav_label,
    graph = { navController -> { equipmentGraph(navController) } }
)

fun NavGraphBuilder.equipmentGraph(navController: NavController) {
    // A real nested graph keeps every section under the Equipment picker entry, so the shell's tab
    // selection and Back stay inside one feature graph. The graph entry also owns the shared
    // ViewModels below.
    navigation(startDestination = equipmentHomeRoute, route = equipmentRoute) {
        composable(equipmentHomeRoute) {
            val viewModel = equipmentProfilerViewModel(navController) ?: return@composable
            val openTab = LocalAppTabNavigator.current
            EquipmentHomeRoute(
                viewModel = viewModel,
                onOpenSection = { section -> navController.navigate(section.route) },
                // The hub is a pushed destination over the Library root, so Back pops to it; if it
                // was opened without an entry beneath it, the Library root is the fallback.
                onBack = {
                    if (!navController.popBackStack()) openTab(AppTab.ROUTINES)
                }
            )
        }
        EquipmentSection.entries.forEach { section ->
            composable(section.route) {
                val viewModel = equipmentProfilerViewModel(navController) ?: return@composable
                val planningSettings =
                    equipmentPlanningSettingsViewModel(navController) ?: return@composable
                EquipmentProfilerRoute(
                    section = section,
                    onBack = { navController.popBackStack() },
                    viewModel = viewModel,
                    planningSettings = planningSettings
                )
            }
        }
    }
}

/**
 * The profile ViewModel shared by the hub and every section, scoped to the Equipment graph entry.
 * It is abandoned when that entry is destroyed so an exiting destination composes nothing.
 */
@Composable
private fun equipmentProfilerViewModel(navController: NavController): EquipmentProfilerViewModel? {
    val owner = remember { navController.getBackStackEntry(equipmentRoute) }
    if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return null
    return koinViewModel(viewModelStoreOwner = owner)
}

/** The planning-settings ViewModel shared by every section, scoped to the Equipment graph entry. */
@Composable
private fun equipmentPlanningSettingsViewModel(
    navController: NavController
): ExercisePlanningSettingsViewModel? {
    val owner = remember { navController.getBackStackEntry(equipmentRoute) }
    if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return null
    return koinViewModel(viewModelStoreOwner = owner)
}

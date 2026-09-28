package com.hydrafit.app.feature.equipment

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hydrafit.app.core.navigation.FeatureDestination
import hydrafit.feature.equipment.generated.resources.Res
import hydrafit.feature.equipment.generated.resources.nav_label

val equipmentRoute: String = "equipment"

val equipmentDestination: FeatureDestination = FeatureDestination(
    route = equipmentRoute,
    label = Res.string.nav_label,
    graph = { equipmentGraph() }
)

fun NavGraphBuilder.equipmentGraph() {
    composable(equipmentRoute) { EquipmentProfilerRoute() }
}

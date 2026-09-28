package com.hydrafit.app.feature.equipment

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

val equipmentRoute: String = "equipment"

fun NavGraphBuilder.equipmentGraph() {
    composable(equipmentRoute) { EquipmentProfilerRoute() }
}

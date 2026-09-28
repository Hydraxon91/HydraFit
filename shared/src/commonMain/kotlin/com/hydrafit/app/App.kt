package com.hydrafit.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hydrafit.app.feature.equipment.equipmentGraph
import com.hydrafit.app.feature.equipment.equipmentRoute
import com.hydrafit.app.feature.fatigueheatmap.fatigueHeatmapGraph
import com.hydrafit.app.feature.fatigueheatmap.fatigueHeatmapRoute
import com.hydrafit.app.feature.splitbuilder.splitBuilderGraph
import com.hydrafit.app.feature.splitbuilder.splitBuilderRoute
import hydrafit.shared.generated.resources.Res
import hydrafit.shared.generated.resources.nav_equipment
import hydrafit.shared.generated.resources.nav_fatigue
import hydrafit.shared.generated.resources.nav_plan
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class AppDestination(val route: String, val label: StringResource)

@Composable
@Preview
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            val destinations = listOf(
                AppDestination(equipmentRoute, Res.string.nav_equipment),
                AppDestination(fatigueHeatmapRoute, Res.string.nav_fatigue),
                AppDestination(splitBuilderRoute, Res.string.nav_plan)
            )

            Scaffold(
                bottomBar = {
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = backStackEntry?.destination
                    NavigationBar {
                        destinations.forEach { destination ->
                            NavigationBarItem(
                                selected =
                                currentDestination?.hierarchy?.any {
                                    it.route ==
                                        destination.route
                                } ==
                                    true,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {},
                                label = { Text(stringResource(destination.label)) }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = equipmentRoute,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    equipmentGraph()
                    fatigueHeatmapGraph()
                    splitBuilderGraph()
                }
            }
        }
    }
}

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
import com.hydrafit.app.feature.equipment.equipmentDestination
import com.hydrafit.app.feature.equipment.equipmentRoute
import com.hydrafit.app.feature.fatigueheatmap.fatigueHeatmapDestination
import com.hydrafit.app.feature.logger.loggerDestination
import com.hydrafit.app.feature.splitbuilder.splitBuilderDestination
import org.jetbrains.compose.resources.stringResource

@Composable
@Preview
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            val destinations = listOf(
                equipmentDestination,
                fatigueHeatmapDestination,
                splitBuilderDestination,
                loggerDestination
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
                    destinations.forEach { destination -> destination.graph(this) }
                }
            }
        }
    }
}

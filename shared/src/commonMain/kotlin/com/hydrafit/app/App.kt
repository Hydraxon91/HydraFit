package com.hydrafit.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.feature.equipment.equipmentDestination
import com.hydrafit.app.feature.equipment.equipmentRoute
import com.hydrafit.app.feature.fatigueheatmap.fatigueHeatmapDestination
import com.hydrafit.app.feature.logger.loggerDestination
import com.hydrafit.app.feature.routines.routinesDestination
import com.hydrafit.app.feature.settings.settingsDestination
import com.hydrafit.app.feature.splitbuilder.splitBuilderDestination
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val STARTUP_INDICATOR_DELAY_MILLIS = 150L

@Composable
@Preview
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val readiness: StartupReadiness = koinInject()
            val isReady by readiness.isReady.collectAsState()
            if (isReady) {
                AppContent()
            } else {
                StartupLoadingGate()
            }
        }
    }
}

@Composable
private fun AppContent() {
    val navController = rememberNavController()
    val destinations = listOf(
        equipmentDestination,
        fatigueHeatmapDestination,
        splitBuilderDestination,
        loggerDestination,
        routinesDestination,
        settingsDestination
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
            destinations.forEach { destination -> destination.graph(navController)(this) }
        }
    }
}

/**
 * Shown while startup maintenance runs. The indicator only appears after a short delay so a fast
 * (usually single-digit-millisecond) maintenance pass never flashes a spinner.
 */
@Composable
private fun StartupLoadingGate() {
    var showIndicator by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(STARTUP_INDICATOR_DELAY_MILLIS)
        showIndicator = true
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (showIndicator) {
            CircularProgressIndicator()
        }
    }
}

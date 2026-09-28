package com.hydrafit.app.core.navigation

import androidx.navigation.NavGraphBuilder
import org.jetbrains.compose.resources.StringResource

data class FeatureDestination(
    val route: String,
    val label: StringResource,
    val graph: NavGraphBuilder.() -> Unit
)

package com.hydrafit.app.feature.progress

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The detail pane needs a genuinely wide window before it sits beside the exercise list. */
private val twoPaneMinWidth = 840.dp

/** A short detail area would leave the charts and history cramped, so the pane also needs height. */
private val twoPaneMinHeight = 480.dp

/**
 * Chooses the adaptive Progress layout from the space the feature actually receives.
 *
 * Only a window that clears both thresholds gets the two-pane presentation; anything narrower or
 * shorter keeps the compact selection workflow. Both bounds are inclusive, and this predicate is
 * pure so the boundary behavior is unit-testable without a composition.
 */
internal fun progressUsesTwoPane(availableWidth: Dp, availableHeight: Dp): Boolean =
    availableWidth >= twoPaneMinWidth && availableHeight >= twoPaneMinHeight

package com.hydrafit.app.feature.fatigueheatmap

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The single transfer from a fatigue [score] into map colour: the clamped 0..1 blend factor. It is
 * monotonic and never exceeds 1, so a higher score is never drawn cooler or outside the ramp.
 */
fun fatigueIntensity(score: Double): Float = score.coerceIn(0.0, 1.0).toFloat()

/** Theme-aware neutral-to-error blend at [intensity]; 0 is neutral and 1 is the error colour. */
fun lerpFatigueColor(neutral: Color, error: Color, intensity: Float): Color =
    lerp(neutral, error, intensity.coerceIn(0f, 1f))

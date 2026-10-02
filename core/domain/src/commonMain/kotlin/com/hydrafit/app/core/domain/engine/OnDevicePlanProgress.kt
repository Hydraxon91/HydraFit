package com.hydrafit.app.core.domain.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live progress for an on-device plan generation. [expectedTokens] is the engine's output ceiling
 * rather than a promise, so the UI shows it as an approximate target.
 */
data class OnDevicePlanProgress(
    val tokensGenerated: Int,
    val expectedTokens: Int,
    val tokensPerSecond: Double
)

/**
 * A single-slot progress channel shared by the on-device planner and the UI. The engine reports as
 * tokens stream in and clears when the attempt ends; the screen observes [progress] while loading.
 */
interface OnDevicePlanProgressReporter {
    val progress: StateFlow<OnDevicePlanProgress?>

    fun report(progress: OnDevicePlanProgress)

    fun clear()

    companion object {
        val Noop: OnDevicePlanProgressReporter = object : OnDevicePlanProgressReporter {
            override val progress: StateFlow<OnDevicePlanProgress?> =
                MutableStateFlow<OnDevicePlanProgress?>(null).asStateFlow()

            override fun report(progress: OnDevicePlanProgress) = Unit

            override fun clear() = Unit
        }
    }
}

/** The single shared reporter; a fresh install has one instance and everyone reads the same flow. */
class DefaultOnDevicePlanProgressReporter : OnDevicePlanProgressReporter {
    private val _progress = MutableStateFlow<OnDevicePlanProgress?>(null)
    override val progress: StateFlow<OnDevicePlanProgress?> = _progress.asStateFlow()

    override fun report(progress: OnDevicePlanProgress) {
        _progress.value = progress
    }

    override fun clear() {
        _progress.value = null
    }
}

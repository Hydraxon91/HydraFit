package com.hydrafit.app.core.llm

import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps the cached on-device engine in step with the selected planner engine: selecting a non-on-device
 * engine releases its native resources, off the main thread. Disposal waits for any in-flight
 * generation (`release()` is synchronized) rather than cancelling it; cancelling an active run is the
 * generator's own stall/timeout path.
 */
class OnDeviceEngineLifecycle(
    private val generator: OnDeviceTextGenerator,
    private val preference: EnginePreferenceRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    /** Releases the cached engine when [engine] is not the on-device engine. Pure, for testing. */
    suspend fun onEngineSelected(engine: PlannerEngineId) {
        if (engine != PlannerEngineId.LOCAL_LLM) generator.release()
    }

    fun start() {
        scope.launch {
            preference.engineFlow()
                .distinctUntilChanged()
                .collect { onEngineSelected(it) }
        }
    }
}

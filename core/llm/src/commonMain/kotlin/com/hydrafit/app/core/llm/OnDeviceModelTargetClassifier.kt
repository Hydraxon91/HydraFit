package com.hydrafit.app.core.llm

import com.hydrafit.app.core.userdata.llm.OnDeviceModelTarget

/**
 * Infers the model target from its file name. NPU packs are published with a hardware or vendor
 * marker (`sm8750`, `qualcomm`, `npu`, `tensor`); everything else is treated as the portable
 * CPU/GPU pack. Detection is best-effort: the engine still walks a fallback chain at runtime, so a
 * mis-detected file degrades to a slower backend rather than failing.
 */
object OnDeviceModelTargetClassifier {

    private val NPU_MARKERS = listOf("sm8750", "qualcomm", "npu", "tensor")

    fun classify(fileName: String?): OnDeviceModelTarget {
        val normalized = fileName?.lowercase().orEmpty()
        return if (NPU_MARKERS.any { it in normalized }) {
            OnDeviceModelTarget.NPU
        } else {
            OnDeviceModelTarget.CPU_GPU
        }
    }
}

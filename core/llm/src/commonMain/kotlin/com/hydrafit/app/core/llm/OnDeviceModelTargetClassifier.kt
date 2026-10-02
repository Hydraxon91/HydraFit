package com.hydrafit.app.core.llm

import com.hydrafit.app.core.userdata.llm.OnDeviceModelTarget

/**
 * Infers the model target from its file name. NPU packs are published with a hardware or vendor
 * marker (`sm8750`, `qualcomm`, `npu`, `tensor`); everything else is treated as the portable
 * CPU/GPU pack. Detection is best-effort: the engine still walks a fallback chain at runtime, so a
 * mis-detected file degrades to a slower backend rather than failing.
 */
object OnDeviceModelTargetClassifier {

    private val NPU_MARKERS = listOf("qualcomm", "snapdragon", "npu", "tensor")

    /** Qualcomm part numbers (`sm8750`, `sm8850`, …) mark NPU builds regardless of the SoC revision. */
    private val QUALCOMM_PART = Regex("""sm\d{4}""")

    fun classify(fileName: String?): OnDeviceModelTarget {
        val normalized = fileName?.lowercase().orEmpty()
        val isNpu = NPU_MARKERS.any { it in normalized } ||
            QUALCOMM_PART.containsMatchIn(normalized)
        return if (isNpu) OnDeviceModelTarget.NPU else OnDeviceModelTarget.CPU_GPU
    }
}

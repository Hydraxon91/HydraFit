package com.hydrafit.app.core.userdata.llm

/**
 * Best-effort detection of a Qualcomm Snapdragon 8 Elite (SM8750) class SoC, whose Hexagon NPU is
 * what the `_sm8750` `.litertlm` packs target. `Build.SOC_MODEL` is only available on API 31+, so
 * older devices fall back to the hardware/board strings. Detection only drives a guidance hint; it
 * never changes how the engine runs.
 */
object NpuDeviceDetector {

    private val NPU_MARKERS = listOf("sm8750", "8 elite")

    fun isNpuCapable(socModel: String?, hardware: String?, board: String?): Boolean {
        val haystack = listOfNotNull(socModel, hardware, board)
            .joinToString(" ")
            .lowercase()
        return NPU_MARKERS.any { it in haystack }
    }
}

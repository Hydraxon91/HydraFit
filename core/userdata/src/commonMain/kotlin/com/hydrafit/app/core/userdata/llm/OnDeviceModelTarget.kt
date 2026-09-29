package com.hydrafit.app.core.userdata.llm

/**
 * Which hardware a `.litertlm` bundle was built for. Qualcomm/Google publish separate AOT-compiled
 * NPU packs (e.g. `*_sm8750.litertlm`) alongside the portable CPU/GPU packs, so the installed file
 * tells us which backend to prefer.
 */
enum class OnDeviceModelTarget {
    CPU_GPU,
    NPU
}

package com.hydrafit.app.core.llm

/**
 * Heuristic emulator/simulator detection from `android.os.Build` fields. On an emulator the LiteRT-LM
 * GPU path degrades to WebGPU, whose kernel compilation pegs the host CPU without producing output,
 * so the generator keeps those devices on the CPU backend. Pure so it can be unit-tested.
 */
internal fun looksLikeEmulator(
    fingerprint: String,
    hardware: String,
    product: String,
    model: String
): Boolean = fingerprint.startsWith("generic") ||
    fingerprint.contains("emulator", ignoreCase = true) ||
    hardware.contains("ranchu", ignoreCase = true) ||
    hardware.contains("goldfish", ignoreCase = true) ||
    product.contains("sdk", ignoreCase = true) ||
    model.contains("emulator", ignoreCase = true) ||
    model.contains("Android SDK built for", ignoreCase = true)

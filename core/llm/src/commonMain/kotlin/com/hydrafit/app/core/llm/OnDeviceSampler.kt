package com.hydrafit.app.core.llm

import kotlin.random.Random

/**
 * Sampling parameters for on-device generation. The seed is randomized per generation so repeated
 * "Regenerate" taps actually vary at the token level; a fixed seed made every generation identical.
 * Temperature is raised above the near-greedy default because constrained JSON decoding keeps the
 * output structurally valid, and the plan variety enforcer guards against repetition regardless.
 */
data class OnDeviceSampler(
    val topK: Int = TOP_K,
    val topP: Double = TOP_P,
    val temperature: Double = TEMPERATURE
) {
    init {
        require(topK >= 1) { "topK must be at least 1" }
        require(topP > 0.0 && topP <= 1.0) { "topP must be in (0.0, 1.0]" }
        require(temperature >= 0.0) { "temperature must not be negative" }
    }

    /** A fresh seed for one generation, so consecutive generations differ. */
    fun randomSeed(random: Random = Random.Default): Int = random.nextInt()

    companion object {
        const val TOP_K: Int = 40
        const val TOP_P: Double = 0.95
        const val TEMPERATURE: Double = 0.6
    }
}

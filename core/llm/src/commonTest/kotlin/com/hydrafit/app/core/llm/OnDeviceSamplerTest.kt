package com.hydrafit.app.core.llm

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OnDeviceSamplerTest {

    @Test
    fun usesVariedSeedsSoRepeatedGenerationsDiffer() {
        val sampler = OnDeviceSampler()
        val random = Random(42)

        val first = sampler.randomSeed(random)
        val second = sampler.randomSeed(random)

        assertNotEquals(first, second)
    }

    @Test
    fun defaultsAreWithinUsableBounds() {
        val sampler = OnDeviceSampler()

        assertTrue(sampler.topK >= 1)
        assertTrue(sampler.topP in 0.0..1.0)
        assertTrue(sampler.temperature > 0.0)
    }

    @Test
    fun rejectsInvalidParameters() {
        assertFailsWith<IllegalArgumentException> { OnDeviceSampler(topK = 0) }
        assertFailsWith<IllegalArgumentException> { OnDeviceSampler(topP = 0.0) }
        assertFailsWith<IllegalArgumentException> { OnDeviceSampler(temperature = -1.0) }
    }

    @Test
    fun acceptsExplicitOverrides() {
        val sampler = OnDeviceSampler(topK = 10, topP = 0.8, temperature = 0.3)

        assertEquals(10, sampler.topK)
        assertEquals(0.8, sampler.topP)
        assertEquals(0.3, sampler.temperature)
    }
}

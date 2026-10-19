package com.hydrafit.app.core.domain.routine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SupersetTest {
    private val pair = Superset("arms", 0, 120)

    @Test
    fun encodingAndWorkoutScopedIdentityRoundTrip() {
        assertEquals(pair, SupersetJson.decode(SupersetJson.encode(pair)))
        repeat(2) { validateSupersets(listOf("curl" to pair, "extension" to pair)) }
    }

    @Test
    fun invalidMembershipCannotBeInterpretedAsAPair() {
        val invalid = listOf(
            listOf("a" to pair),
            listOf("a" to pair, "b" to null, "c" to pair),
            listOf("a" to pair, "a" to pair),
            listOf("a" to pair, "b" to pair.copy(pairRestSeconds = 90)),
            listOf("a" to pair, "b" to pair, "c" to pair)
        )
        invalid.forEach { entries ->
            assertFailsWith<IllegalArgumentException> { validateSupersets(entries) }
        }
    }

    @Test
    fun restBoundsReuseTheExistingCountdownAndPermitImmediateTransition() {
        pair.requireValid()
        pair.copy(transitionSeconds = Superset.MAX_REST_SECONDS).requireValid()
        assertFailsWith<IllegalArgumentException> { pair.copy(pairRestSeconds = 0).requireValid() }
        assertFailsWith<IllegalArgumentException> {
            pair.copy(transitionSeconds = -1).requireValid()
        }
        assertFailsWith<IllegalArgumentException> { pair.copy(key = " ").requireValid() }
    }

    @Test
    fun routineValidationRejectsBrokenPairBeforeSaving() {
        val template = RoutineTemplate(
            name = "Arms",
            workouts = listOf(
                RoutineWorkout(
                    name = "Day",
                    entries = listOf(
                        RoutineEntry(exerciseId = "curl", sets = 3, reps = 10, superset = pair)
                    )
                )
            )
        )
        assertFailsWith<RoutineTemplateException> { RoutineTemplateValidator.validate(template) }
    }
}

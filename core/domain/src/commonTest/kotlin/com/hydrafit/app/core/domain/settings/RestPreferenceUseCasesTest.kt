package com.hydrafit.app.core.domain.settings

import com.hydrafit.app.core.domain.engine.TrainingGoal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class RestPreferenceUseCasesTest {
    @Test
    fun exerciseOverrideWinsAndClearingItFallsBackToGlobalDefault() = runTest {
        val repository = FakeRestPreferenceRepository(globalSeconds = 180L)
        val resolve = ResolveRestDurationUseCase(repository)

        assertEquals(180L, resolve("squat"))
        SetExerciseRestDurationUseCase(repository)("squat", 240L)
        assertEquals(240L, resolve("squat"))
        ClearExerciseRestDurationUseCase(repository)("squat")
        assertEquals(180L, resolve("squat"))

        assertFailsWith<IllegalArgumentException> {
            SetExerciseRestDurationUseCase(repository)("", 240L)
        }
        assertFailsWith<IllegalArgumentException> {
            SetExerciseRestDurationUseCase(repository)("squat", 0L)
        }
        assertFailsWith<IllegalArgumentException> {
            ClearExerciseRestDurationUseCase(repository)("")
        }
    }

    @Test
    fun eachGoalStartsFromItsOwnRestDefaultWithinTheCountdownRange() {
        assertEquals(180L, defaultRestSecondsFor(TrainingGoal.STRENGTH))
        assertEquals(120L, defaultRestSecondsFor(TrainingGoal.BALANCED))
        assertEquals(120L, defaultRestSecondsFor(TrainingGoal.HYPERTROPHY))
        assertEquals(60L, defaultRestSecondsFor(TrainingGoal.ENDURANCE))
        TrainingGoal.entries.forEach {
            assertTrue(defaultRestSecondsFor(it) in MIN_REST_SECONDS..MAX_REST_SECONDS)
        }
    }

    @Test
    fun globalDefaultIsObservableAndMustStayWithinRestCountdownRange() = runTest {
        val repository = FakeRestPreferenceRepository()
        val set = SetGlobalRestDurationUseCase(repository)

        set(300L)
        assertEquals(300L, repository.globalSeconds)
        assertFailsWith<IllegalArgumentException> { set(0L) }
        assertFailsWith<IllegalArgumentException> { set(MAX_REST_SECONDS + 1L) }
    }

    private class FakeRestPreferenceRepository(var globalSeconds: Long = DEFAULT_REST_SECONDS) :
        RestPreferenceRepository {
        private val global = MutableStateFlow(globalSeconds)
        private val overrides = mutableMapOf<String, Long>()

        override fun globalDefaultSecondsFlow(): Flow<Long> = global

        override suspend fun globalDefaultSeconds(): Long = globalSeconds

        override suspend fun setGlobalDefaultSeconds(seconds: Long) {
            globalSeconds = seconds
            global.value = seconds
        }

        override suspend fun exerciseOverrideSeconds(exerciseId: String): Long? =
            overrides[exerciseId]

        override suspend fun setExerciseOverrideSeconds(exerciseId: String, seconds: Long) {
            overrides[exerciseId] = seconds
        }

        override suspend fun clearExerciseOverride(exerciseId: String) {
            overrides.remove(exerciseId)
        }
    }
}

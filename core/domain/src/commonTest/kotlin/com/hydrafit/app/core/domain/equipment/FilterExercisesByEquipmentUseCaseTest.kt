package com.hydrafit.app.core.domain.equipment

import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterExercisesByEquipmentUseCaseTest {

    private val useCase = FilterExercisesByEquipmentUseCase()

    @Test
    fun keepsExerciseWhenAllRequiredEquipmentIsAvailable() {
        val squat = exercise("squat", required = setOf(EquipmentTag.BARBELL))

        val result = useCase(listOf(squat), setOf(EquipmentTag.BARBELL))

        assertEquals(listOf(squat), result)
    }

    @Test
    fun dropsExerciseWhenRequiredEquipmentIsMissing() {
        val squat = exercise("squat", required = setOf(EquipmentTag.BARBELL))

        val result = useCase(listOf(squat), setOf(EquipmentTag.DUMBBELL))

        assertTrue(result.isEmpty())
    }

    @Test
    fun exerciseRequiringMultipleEquipmentNeedsAllOfThem() {
        val benchPress = exercise(
            "bench-press",
            required = setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH)
        )

        assertTrue(useCase(listOf(benchPress), setOf(EquipmentTag.BARBELL)).isEmpty())
        assertEquals(
            listOf(benchPress),
            useCase(listOf(benchPress), setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH))
        )
    }

    @Test
    fun exerciseWithNoRequiredEquipmentIsAlwaysAvailable() {
        val pushUp = exercise("push-up")

        assertEquals(listOf(pushUp), useCase(listOf(pushUp), emptySet()))
    }

    @Test
    fun bodyweightTagIsTreatedAsAlwaysAvailable() {
        val pullUp = exercise("pull-up", required = setOf(EquipmentTag.BODYWEIGHT))

        assertEquals(listOf(pullUp), useCase(listOf(pullUp), emptySet()))
    }

    @Test
    fun bodyweightAlongsideMissingEquipmentStillRequiresTheEquipment() {
        val weightedPullUp = exercise(
            "weighted-pull-up",
            required = setOf(EquipmentTag.BODYWEIGHT, EquipmentTag.PULL_UP_BAR)
        )

        assertTrue(useCase(listOf(weightedPullUp), emptySet()).isEmpty())
        assertFalse(useCase(listOf(weightedPullUp), setOf(EquipmentTag.PULL_UP_BAR)).isEmpty())
    }

    @Test
    fun preservesInputOrderAndKeepsOnlyAvailableExercises() {
        val squat = exercise("squat", required = setOf(EquipmentTag.BARBELL))
        val pushUp = exercise("push-up")
        val row = exercise("row", required = setOf(EquipmentTag.DUMBBELL))

        val result = useCase(
            listOf(squat, pushUp, row),
            setOf(EquipmentTag.DUMBBELL)
        )

        assertEquals(listOf(pushUp, row), result)
    }

    private fun exercise(
        id: String,
        required: Set<EquipmentTag> = emptySet(),
        primary: Set<MuscleGroup> = setOf(MuscleGroup.CHEST)
    ) = Exercise(
        id = id,
        name = id,
        requiredEquipment = required,
        primaryMuscles = primary
    )
}

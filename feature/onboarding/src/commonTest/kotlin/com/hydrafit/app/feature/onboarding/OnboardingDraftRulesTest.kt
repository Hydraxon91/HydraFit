package com.hydrafit.app.feature.onboarding

import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.setup.SetupStep
import com.hydrafit.app.core.domain.setup.SetupStepInput
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.unit.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pure decisions the setup editors use; none of them can write anything. */
class OnboardingDraftRulesTest {

    @Test
    fun anAbsentDraftIsNeverSubmittable() {
        assertFalse(isSubmittable(null))
    }

    @Test
    fun weekdaysAreSubmittableOnlyWhenUndecidedOrTwoToSixDays() {
        assertTrue(isSubmittable(SetupStepInput.Weekdays(null)))
        assertTrue(
            isSubmittable(SetupStepInput.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)))
        )
        assertTrue(
            isSubmittable(
                SetupStepInput.Weekdays(
                    setOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY,
                        DayOfWeek.SATURDAY
                    )
                )
            )
        )
        assertFalse(isSubmittable(SetupStepInput.Weekdays(emptySet())))
        assertFalse(isSubmittable(SetupStepInput.Weekdays(setOf(DayOfWeek.MONDAY))))
        assertFalse(isSubmittable(SetupStepInput.Weekdays(DayOfWeek.entries.toSet())))
    }

    @Test
    fun otherStepsAreSubmittableOnceTheirDraftExists() {
        assertTrue(isSubmittable(SetupStepInput.Continue(SetupStep.WELCOME)))
        assertTrue(isSubmittable(SetupStepInput.Units(WeightUnit.KG)))
        assertTrue(isSubmittable(SetupStepInput.Goal(TrainingGoal.STRENGTH)))
        assertTrue(isSubmittable(SetupStepInput.Equipment(emptySet())))
        assertTrue(isSubmittable(SetupStepInput.PersonalRecords(emptyList())))
    }

    @Test
    fun togglingAddsAndRemovesDaysWithoutSilentlyUndeciding() {
        assertEquals(
            setOf(DayOfWeek.MONDAY),
            toggleWeekday(null, DayOfWeek.MONDAY)
        )
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            toggleWeekday(setOf(DayOfWeek.MONDAY), DayOfWeek.WEDNESDAY)
        )
        assertEquals(
            setOf(DayOfWeek.WEDNESDAY),
            toggleWeekday(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), DayOfWeek.MONDAY)
        )
    }

    @Test
    fun theDraftValueOutranksTheStoredRecordUntilAnExplicitEdit() {
        val stored = PersonalRecord("bench", 80.0, 5)
        val draft = PersonalRecord("bench", 90.0, 3)

        assertEquals(stored, displayedRecord("bench", emptyList(), listOf(stored)))
        assertEquals(draft, displayedRecord("bench", listOf(draft), listOf(stored)))
        assertNull(displayedRecord("squat", listOf(draft), listOf(stored)))
    }

    @Test
    fun addingADraftReplacesOnlyThatExercise() {
        val first = PersonalRecord("bench", 80.0, 5)
        val second = PersonalRecord("squat", 120.0, 5)
        val replacement = PersonalRecord("bench", 85.0, 3)

        val result = withDraftRecord(listOf(first, second), replacement)

        assertEquals(2, result.size)
        assertEquals(
            mapOf("bench" to replacement, "squat" to second),
            result.associateBy { it.exerciseId }
        )
    }

    @Test
    fun weightAndRepsParsingRejectsAnythingThatIsNotPositive() {
        assertEquals(100.0, parseDisplayWeight(" 100 "))
        assertEquals(2.5, parseDisplayWeight("2.5"))
        assertNull(parseDisplayWeight(""))
        assertNull(parseDisplayWeight("0"))
        assertNull(parseDisplayWeight("-5"))
        assertNull(parseDisplayWeight("heavy"))
        assertNull(parseDisplayWeight("NaN"))
        assertNull(parseDisplayWeight("Infinity"))
    }

    @Test
    fun repsParsingRejectsAnythingThatIsNotAPositiveWholeNumber() {
        assertEquals(5, parseReps("5"))
        assertEquals(12, parseReps(" 12 "))
        assertNull(parseReps(""))
        assertNull(parseReps("0"))
        assertNull(parseReps("-3"))
        assertNull(parseReps("2.5"))
        assertNull(parseReps("lots"))
    }

    @Test
    fun theGoalStepOffersEveryDeclaredGoal() {
        assertEquals(TrainingGoal.entries.toList(), onboardingGoalChoices)
        TrainingGoal.entries.forEach { goal ->
            assertTrue(goal in onboardingGoalChoices, "missing goal choice: $goal")
        }
        assertEquals(TrainingGoal.entries.size, onboardingGoalChoices.distinct().size)
    }

    @Test
    fun theGoalGroupPreselectsTheLoadedGoalAndFallsBackToTheStoredDefault() {
        TrainingGoal.entries.forEach { goal ->
            assertEquals(goal, selectedGoalChoice(SetupStepInput.Goal(goal)))
        }
        // No goal draft yet: the stored default is shown rather than leaving the group unselected.
        assertEquals(TrainingGoal.BALANCED, selectedGoalChoice(null))
        assertEquals(TrainingGoal.BALANCED, selectedGoalChoice(SetupStepInput.Units(WeightUnit.KG)))
    }
}

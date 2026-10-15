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
        val equipment = SetupStepInput.Equipment(
            emptySet(),
            profileContext = com.hydrafit.app.core.domain.equipment.EquipmentProfileContext(1L, 0L)
        )
        assertTrue(isSubmittable(equipment))
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
    fun anOpenedEntryStartsFromTheShownRecordInTheFrozenUnitAndRemembersIt() {
        assertEquals(
            PersonalRecordEntryDraft("bench", "80", "5", PersonalRecord("bench", 80.0, 5)),
            personalRecordEntryDraft("bench", WeightUnit.KG, PersonalRecord("bench", 80.0, 5))
        )
        assertEquals(
            PersonalRecordEntryDraft("bench", "176.4", "3", PersonalRecord("bench", 80.0, 3)),
            personalRecordEntryDraft("bench", WeightUnit.LB, PersonalRecord("bench", 80.0, 3))
        )
        assertEquals(
            PersonalRecordEntryDraft("bench", "", ""),
            personalRecordEntryDraft("bench", WeightUnit.KG, null)
        )
    }

    @Test
    fun anEntryBecomesARecordOnlyWhenBothFieldsParseInTheEntryUnit() {
        assertEquals(
            PersonalRecord("bench", WeightUnit.LB.displayToKilograms(225.0), 5),
            personalRecordFrom("bench", WeightUnit.LB, " 225 ", "5")
        )
        assertNull(personalRecordFrom("bench", WeightUnit.KG, "", "5"))
        assertNull(personalRecordFrom("bench", WeightUnit.KG, "100", ""))
        assertNull(personalRecordFrom("bench", WeightUnit.KG, "0", "5"))
        assertNull(personalRecordFrom("bench", WeightUnit.KG, "-5", "5"))
        assertNull(personalRecordFrom("bench", WeightUnit.KG, "NaN", "5"))
        assertNull(personalRecordFrom("bench", WeightUnit.KG, "Infinity", "5"))
        assertNull(personalRecordFrom("bench", WeightUnit.KG, "heavy", "5"))
        assertNull(personalRecordFrom("bench", WeightUnit.KG, "100", "0"))
        assertNull(personalRecordFrom("bench", WeightUnit.KG, "100", "lots"))
    }

    @Test
    fun anEntryWithAnUnchangedTextDecidesNothing() {
        val stored = PersonalRecord("bench", 100.0, 5)
        // Opening a stored record and submitting it untouched must never round-trip its display text.
        listOf(WeightUnit.KG, WeightUnit.LB).forEach { unit ->
            val opened = personalRecordEntryDraft("bench", unit, stored)
            assertNull(
                decidePersonalRecordEntry(opened, unit),
                "an untouched ${unit.label} entry decided a record"
            )
        }
        // A row that has no value yet still submits whatever its text parses to.
        assertEquals(
            PersonalRecord("bench", 100.0, 5),
            decidePersonalRecordEntry(PersonalRecordEntryDraft("bench", "100", "5"), WeightUnit.KG)
        )
    }

    @Test
    fun aSingleChangedFieldKeepsTheOtherExactOriginValue() {
        // 100.0 kg shown in pounds pre-fills 220.5, which would round-trip to 100.0171... kg.
        val stored = PersonalRecord("bench", 100.0, 5)
        val opened = personalRecordEntryDraft("bench", WeightUnit.LB, stored)

        assertEquals(
            PersonalRecord("bench", 100.0, 6),
            decidePersonalRecordEntry(opened.copy(repsText = "6"), WeightUnit.LB)
        )
        assertEquals(
            PersonalRecord("bench", WeightUnit.LB.displayToKilograms(225.0), 5),
            decidePersonalRecordEntry(opened.copy(weightText = "225"), WeightUnit.LB)
        )
        assertEquals(
            PersonalRecord("bench", WeightUnit.LB.displayToKilograms(225.0), 6),
            decidePersonalRecordEntry(
                PersonalRecordEntryDraft("bench", "225", "6", stored),
                WeightUnit.LB
            )
        )
    }

    @Test
    fun aChangedFieldThatDoesNotParseDecidesNothing() {
        val stored = PersonalRecord("bench", 100.0, 5)
        val opened = personalRecordEntryDraft("bench", WeightUnit.LB, stored)

        assertNull(decidePersonalRecordEntry(opened.copy(weightText = ""), WeightUnit.LB))
        assertNull(decidePersonalRecordEntry(opened.copy(weightText = "heavy"), WeightUnit.LB))
        assertNull(decidePersonalRecordEntry(opened.copy(weightText = "0"), WeightUnit.LB))
        assertNull(decidePersonalRecordEntry(opened.copy(repsText = "0"), WeightUnit.LB))
        assertNull(decidePersonalRecordEntry(opened.copy(repsText = "lots"), WeightUnit.LB))
    }

    @Test
    fun foldingAnUntouchedEntryAddsNoDraftEdit() {
        val draft = SetupStepInput.PersonalRecords(listOf(PersonalRecord("squat", 120.0, 5)))
        val stored = PersonalRecord("bench", 100.0, 5)
        val opened = personalRecordEntryDraft("bench", WeightUnit.LB, stored)

        assertEquals(draft, foldPersonalRecordEntry(draft, opened, WeightUnit.LB))
        assertEquals(draft, foldPersonalRecordEntry(draft, null, WeightUnit.LB))
    }

    @Test
    fun foldingAnEntryAffectsOnlyThatExerciseAndIgnoresOtherStepsAndInvalidText() {
        val staged = PersonalRecord("bench", 80.0, 5)
        val other = PersonalRecord("squat", 120.0, 5)
        val draft = SetupStepInput.PersonalRecords(listOf(staged, other))

        val folded = foldPersonalRecordEntry(
            draft,
            PersonalRecordEntryDraft("bench", "90", "3"),
            WeightUnit.KG
        ) as SetupStepInput.PersonalRecords

        // The staged value is replaced, not written twice, and the other exercise is untouched.
        assertEquals(
            mapOf("bench" to PersonalRecord("bench", 90.0, 3), "squat" to other),
            folded.edits.associateBy { it.exerciseId }
        )
        assertEquals(draft, foldPersonalRecordEntry(draft, null, WeightUnit.KG))
        // An absent, blank or unparseable entry adds nothing.
        listOf(
            PersonalRecordEntryDraft("bench", "", "3"),
            PersonalRecordEntryDraft("bench", "90", "0")
        ).forEach { entry ->
            assertEquals(draft, foldPersonalRecordEntry(draft, entry, WeightUnit.KG))
        }
        // No other step's draft is touched, and an absent draft stays absent.
        val units = SetupStepInput.Units(WeightUnit.LB)
        val entry = PersonalRecordEntryDraft("bench", "90", "3")
        assertEquals(units, foldPersonalRecordEntry(units, entry, WeightUnit.KG))
        assertNull(foldPersonalRecordEntry(null, entry, WeightUnit.KG))
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

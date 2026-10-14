package com.hydrafit.app.feature.onboarding

import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.setup.SetupStepInput
import com.hydrafit.app.core.domain.time.DayOfWeek

/**
 * The setup editors' draft decisions, kept out of the Composables so the rules that gate Next and
 * shape a draft are unit-testable without Compose. None of these touch persistence: only the
 * ViewModel's `next`/`skip` write.
 */

/**
 * Whether the current step's draft can be submitted. Null (no draft yet) never can. The only local
 * constraint today is the 2–6 weekday range: null means the explicit "Decide later" choice, and any
 * other selection must satisfy `requirePreferredWeekdays` so the domain check never rejects it.
 */
internal fun isSubmittable(input: SetupStepInput?): Boolean = when (input) {
    null -> false
    is SetupStepInput.Weekdays -> input.weekdays.let { days -> days == null || days.size in 2..6 }
    else -> true
}

/**
 * Adds or removes one day from the draft. Undecided (null) is a real choice, so picking a day starts
 * a concrete selection rather than silently replacing the choice; only "Decide later" returns null.
 */
internal fun toggleWeekday(weekdays: Set<DayOfWeek>?, day: DayOfWeek): Set<DayOfWeek> =
    weekdays.orEmpty().let { if (day in it) it - day else it + day }

/**
 * The value shown for one exercise: the pending draft edit first, then the stored record. A stored
 * record therefore remains the shown value until the user explicitly edits that exercise.
 */
internal fun displayedRecord(
    exerciseId: String,
    draft: List<PersonalRecord>,
    existing: List<PersonalRecord>
): PersonalRecord? = draft.firstOrNull { it.exerciseId == exerciseId }
    ?: existing.firstOrNull { it.exerciseId == exerciseId }

/**
 * Replaces only this exercise's draft entry. Every other draft and every stored record is left
 * untouched, so omitting an exercise — including "I don't know" — never deletes anything.
 */
internal fun withDraftRecord(
    draft: List<PersonalRecord>,
    record: PersonalRecord
): List<PersonalRecord> = draft.filterNot { it.exerciseId == record.exerciseId } + record

/**
 * A positive, finite display weight, or null for blank, non-numeric, zero, negative or non-finite
 * input. The editor uses this so an unparseable field can never become a stored record.
 */
internal fun parseDisplayWeight(text: String): Double? =
    text.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }

/** A positive whole rep count, or null for blank, non-numeric, zero or negative input. */
internal fun parseReps(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it > 0 }

/**
 * The goals the Goal step offers: every declared [TrainingGoal], so the stored default (BALANCED) is
 * always representable and a new goal can never be silently omitted from the group.
 */
internal val onboardingGoalChoices: List<TrainingGoal> = TrainingGoal.entries.toList()

/**
 * The goal the group preselects: the one the step loaded, falling back to the stored default when no
 * goal draft exists yet. Choosing another goal stays an ordinary `Goal` edit.
 */
internal fun selectedGoalChoice(input: SetupStepInput?): TrainingGoal =
    (input as? SetupStepInput.Goal)?.goal ?: TrainingGoal.BALANCED

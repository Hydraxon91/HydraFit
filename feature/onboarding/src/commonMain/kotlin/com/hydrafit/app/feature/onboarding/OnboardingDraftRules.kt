package com.hydrafit.app.feature.onboarding

import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.setup.SetupStepInput
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatWeight

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
 * The raw text an opened entry starts from: the shown record's value in the frozen entry unit, so the
 * editor continues from what the row already displays. A record that is absent starts blank. The
 * shown record is kept as the entry's origin, so an untouched entry can submit nothing.
 */
internal fun personalRecordEntryDraft(
    exerciseId: String,
    unit: WeightUnit,
    shown: PersonalRecord?
): PersonalRecordEntryDraft = PersonalRecordEntryDraft(
    exerciseId = exerciseId,
    weightText = prefillWeightText(unit, shown),
    repsText = shown?.reps?.toString().orEmpty(),
    origin = shown
)

/** The weight text an opening entry pre-fills its field with, in the frozen entry unit. */
private fun prefillWeightText(unit: WeightUnit, shown: PersonalRecord?): String =
    shown?.let { formatWeight(unit.kilogramsToDisplay(it.weightKg)) }.orEmpty()

/**
 * The record an open entry's text describes, or null when either field is blank, non-numeric, zero,
 * negative or non-finite. Save and Next read the same text through this one rule, so Next records
 * exactly what Save would.
 */
internal fun personalRecordFrom(
    exerciseId: String,
    unit: WeightUnit,
    weightText: String,
    repsText: String
): PersonalRecord? {
    val weight = parseDisplayWeight(weightText) ?: return null
    val reps = parseReps(repsText) ?: return null
    return PersonalRecord(exerciseId, unit.displayToKilograms(weight), reps)
}

/**
 * The one rule decision for an open entry: the record it submits, or null when it submits nothing.
 * Save, Next and a switch to another entry all read it, so they cannot diverge:
 *
 * - No text differs from what the entry was pre-filled with: nothing, so opening a stored record and
 *   leaving it alone never rewrites it. Re-encoding a rounded display value in another unit would
 *   otherwise replace the record with slightly different kilograms.
 * - One field differs: the changed field is parsed and converted through the frozen entry unit, while
 *   the other field keeps its exact origin value instead of the round-tripped display value.
 * - A changed field is blank or unparseable: nothing, so an invalid edit records nothing and never
 *   blocks Next.
 *
 * An entry with no origin (a row with no value yet) submits whatever its text parses to, as before.
 */
internal fun decidePersonalRecordEntry(
    entry: PersonalRecordEntryDraft,
    unit: WeightUnit
): PersonalRecord? {
    val origin = entry.origin
        ?: return personalRecordFrom(entry.exerciseId, unit, entry.weightText, entry.repsText)
    val weightChanged = entry.weightText != prefillWeightText(unit, origin)
    val repsChanged = entry.repsText != origin.reps.toString()
    if (!weightChanged && !repsChanged) return null
    val weight = if (weightChanged) {
        val display = parseDisplayWeight(entry.weightText) ?: return null
        unit.displayToKilograms(display)
    } else {
        origin.weightKg
    }
    val reps = if (repsChanged) parseReps(entry.repsText) ?: return null else origin.reps
    return PersonalRecord(entry.exerciseId, weight, reps)
}

/**
 * Folds the open entry into a submitted draft. A valid entry adds or replaces just that exercise's
 * record using the frozen entry unit; an absent, unchanged or unparseable entry adds nothing, so
 * "I don't know", an untouched row and an invalid field record nothing and never block Next. Other
 * steps are returned untouched, no other draft edit or stored record is affected, and a value already
 * staged by Save is replaced rather than written twice.
 */
internal fun foldPersonalRecordEntry(
    input: SetupStepInput?,
    entry: PersonalRecordEntryDraft?,
    unit: WeightUnit
): SetupStepInput? {
    val draft = input as? SetupStepInput.PersonalRecords ?: return input
    val record = entry?.let { decidePersonalRecordEntry(it, unit) } ?: return input
    return draft.copy(edits = withDraftRecord(draft.edits, record))
}

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

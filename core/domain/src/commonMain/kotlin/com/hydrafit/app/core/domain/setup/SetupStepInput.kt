package com.hydrafit.app.core.domain.setup

import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.equipment.EquipmentProfileContext
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.LoadKind

sealed interface SetupStepInput {
    val step: SetupStep

    data class Continue(override val step: SetupStep) : SetupStepInput

    data class Units(val unit: WeightUnit) : SetupStepInput {
        override val step: SetupStep = SetupStep.UNITS
    }

    /** Only explicitly edited ceilings are written; other inventory values remain untouched. */
    data class Equipment(
        val selected: Set<EquipmentTag>,
        val editedMaxWeightsKg: Map<EquipmentTag, Double?> = emptyMap(),
        val profileContext: EquipmentProfileContext
    ) : SetupStepInput {
        override val step: SetupStep = SetupStep.EQUIPMENT
    }

    data class Goal(val goal: TrainingGoal) : SetupStepInput {
        override val step: SetupStep = SetupStep.GOAL
    }

    data class Weekdays(val weekdays: Set<DayOfWeek>?) : SetupStepInput {
        override val step: SetupStep = SetupStep.WEEKDAYS
    }

    /** Omitted exercises are never deleted; replacing an existing record requires its explicit edit. */
    data class PersonalRecords(val edits: List<PersonalRecord>) : SetupStepInput {
        override val step: SetupStep = SetupStep.PERSONAL_RECORDS
    }
}

fun requireSetupInput(input: SetupStepInput) {
    when (input) {
        is SetupStepInput.Continue -> Unit
        is SetupStepInput.Units -> Unit
        is SetupStepInput.Goal -> Unit
        is SetupStepInput.Weekdays -> requirePreferredWeekdays(input.weekdays)
        is SetupStepInput.Equipment -> input.editedMaxWeightsKg.values.forEach { weight ->
            require(weight == null || (weight.isFinite() && weight >= 0.0))
        }
        is SetupStepInput.PersonalRecords -> {
            require(input.edits.map { it.exerciseId }.distinct().size == input.edits.size)
            input.edits.forEach {
                require(it.exerciseId.isNotBlank())
                require(it.weightKg.isFinite() && it.weightKg > 0.0)
                require(it.reps > 0)
                require(it.loadKind == LoadKind.EXTERNAL)
            }
        }
    }
}

/** Capture collection values before dispatching a save, so later editor mutations cannot change it. */
fun SetupStepInput.snapshot(): SetupStepInput = when (this) {
    is SetupStepInput.Equipment -> copy(
        selected = selected.toSet(),
        editedMaxWeightsKg = editedMaxWeightsKg.toMap()
    )
    is SetupStepInput.Weekdays -> copy(weekdays = weekdays?.toSet())
    is SetupStepInput.PersonalRecords -> copy(edits = edits.toList())
    else -> this
}

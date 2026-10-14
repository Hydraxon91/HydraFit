package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.setup.SetupCommit
import com.hydrafit.app.core.domain.setup.SetupState as CommittedSetupState
import com.hydrafit.app.core.domain.setup.SetupStepInput
import com.hydrafit.app.core.domain.setup.SetupWriteRepository
import com.hydrafit.app.core.domain.setup.StaleSetupContextException
import com.hydrafit.app.core.domain.time.TimeProvider

class SqlDelightSetupWriteRepository(
    private val database: HydraFitDatabase,
    private val timeProvider: TimeProvider
) : SetupWriteRepository {
    override suspend fun commit(command: SetupCommit): CommittedSetupState =
        database.transactionWithResult {
            val current = database.setupQueries.selectState().executeAsOne().toSetupState()
            if (current != command.expected) throw StaleSetupContextException()
            val next = command.nextState()
            when (val input = command.input) {
                null, is SetupStepInput.Continue -> Unit
                is SetupStepInput.Units -> {
                    database.plannerEngineQueries.insertIgnoreRow("DETERMINISTIC")
                    database.plannerEngineQueries.updateWeightUnit(input.unit.name)
                }
                is SetupStepInput.Goal -> {
                    database.plannerEngineQueries.insertIgnoreRow("DETERMINISTIC")
                    database.plannerEngineQueries.updateTrainingGoal(input.goal.name)
                }
                is SetupStepInput.Weekdays -> database.savePreferredWeekdays(input.weekdays)
                is SetupStepInput.Equipment -> {
                    (input.selected + input.editedMaxWeightsKg.keys).forEach { tag ->
                        val row = database.equipmentQueries.selectById(tag.id).executeAsOneOrNull()
                        require(row != null)
                    }
                    database.userEquipmentQueries.deleteAllSelected()
                    input.selected.sortedBy { it.id }.forEach {
                        database.userEquipmentQueries.insertSelected(it.id)
                    }
                    input.editedMaxWeightsKg.forEach { (tag, weight) ->
                        database.equipmentQueries.updateMaxWeight(weight, tag.id)
                    }
                }
                is SetupStepInput.PersonalRecords -> {
                    val catalog = SqlDelightExerciseCatalog(database)
                    input.edits.forEach { record ->
                        require(
                            catalog.findById(record.exerciseId)?.loadCapability ==
                                ExerciseLoadCapability.EXTERNAL
                        )
                    }
                    val updatedAt = timeProvider.nowMillis()
                    input.edits.forEach { record ->
                        database.personalRecordQueries.upsert(
                            exerciseId = record.exerciseId,
                            weightKg = record.weightKg,
                            reps = record.reps.toLong(),
                            updatedAt = updatedAt,
                            loadKind = record.loadKind.name
                        )
                    }
                }
            }
            database.setupQueries.updateState(
                next.version.toLong(),
                next.disposition.name,
                next.step.name,
                next.revision
            )
            next
        }
}

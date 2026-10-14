package com.hydrafit.app.feature.onboarding

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.setup.SaveSetupStepUseCase
import com.hydrafit.app.core.domain.setup.SetupCommit
import com.hydrafit.app.core.domain.setup.SetupState
import com.hydrafit.app.core.domain.setup.SetupStepInput
import com.hydrafit.app.core.domain.setup.SetupWriteRepository
import com.hydrafit.app.core.domain.setup.SkipSetupUseCase
import com.hydrafit.app.core.domain.setup.StaleSetupContextException
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import com.hydrafit.app.core.userdata.equipment.PersonalRecordRepository
import com.hydrafit.app.core.userdata.settings.PreferredWeekdaysRepository
import com.hydrafit.app.core.userdata.settings.SetupRepository
import com.hydrafit.app.core.userdata.settings.TrainingGoalRepository
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

internal class OnboardingFixture {
    val committed = MutableStateFlow(SetupState())
    val ready = MutableStateFlow(true)
    var writeGate: CompletableDeferred<Unit>? = null
    var unitGate: CompletableDeferred<Unit>? = null
    var failure: Exception? = null
    var unitFailure: Exception? = null
    var unit = WeightUnit.KG
    val attempts = mutableListOf<SetupCommit>()
    val savedRecords = mutableMapOf<String, PersonalRecord>()

    val setup = object : SetupRepository {
        override suspend fun current() = committed.value
        override fun observe() = committed
    }
    val readiness = object : StartupReadiness {
        override val isReady = ready
    }
    val writer = object : SetupWriteRepository {
        override suspend fun commit(command: SetupCommit): SetupState {
            attempts += command
            writeGate?.await()
            failure?.let { throw it }
            if (command.expected != committed.value) throw StaleSetupContextException()
            when (val input = command.input) {
                is SetupStepInput.Units -> unit = input.unit
                is SetupStepInput.PersonalRecords -> input.edits.forEach {
                    savedRecords[it.exerciseId] = it
                }
                else -> Unit
            }
            committed.value = command.nextState()
            return committed.value
        }
    }
    val units = object : WeightUnitRepository {
        override suspend fun selectedUnit(): WeightUnit {
            unitGate?.await()
            unitFailure?.let { throw it }
            return unit
        }
        override fun unitFlow() = flowOf(unit)
        override suspend fun setUnit(unit: WeightUnit) = error("Direct write is forbidden")
    }
    val equipment = object : EquipmentRepository {
        override fun observeAll() = flowOf(emptyList<Equipment>())
        override suspend fun all() = emptyList<Equipment>()
        override suspend fun add(name: String): Equipment = error("Direct write is forbidden")
        override suspend fun remove(id: EquipmentTag) = error("Direct write is forbidden")
        override suspend fun rename(
            id: EquipmentTag,
            name: String,
            maxWeightKg: Double?
        ): Equipment = error("Direct write is forbidden")
        override suspend fun setMaxWeight(id: EquipmentTag, maxWeightKg: Double?) =
            error("Direct write is forbidden")
    }
    val selection = object : EquipmentSelectionRepository {
        override suspend fun selected() = emptySet<EquipmentTag>()
        override fun selectedFlow() = flowOf(emptySet<EquipmentTag>())
        override suspend fun setSelected(tags: Set<EquipmentTag>) =
            error("Direct write is forbidden")
    }
    val goals = object : TrainingGoalRepository {
        override suspend fun selectedGoal() = TrainingGoal.BALANCED
        override fun goalFlow() = flowOf(TrainingGoal.BALANCED)
        override suspend fun setGoal(goal: TrainingGoal) = error("Direct write is forbidden")
    }
    val weekdays = object : PreferredWeekdaysRepository {
        override suspend fun current(): Set<DayOfWeek>? = null
        override fun observe() = flowOf<Set<DayOfWeek>?>(null)
        override suspend fun set(weekdays: Set<DayOfWeek>?) = error("Direct write is forbidden")
    }
    val records = object : PersonalRecordRepository {
        override fun observe() = flowOf(savedRecords.values.toList())
        override suspend fun set(record: PersonalRecord) = error("Direct write is forbidden")
        override suspend fun clear(exerciseId: String) = error("Direct write is forbidden")
    }
    val catalog = object : ExerciseCatalog {
        override suspend fun all() = listOf(
            Exercise("external", "External", emptySet(), emptySet()),
            Exercise(
                "bodyweight",
                "Bodyweight",
                emptySet(),
                emptySet(),
                loadCapability = ExerciseLoadCapability.BODYWEIGHT_ONLY
            )
        )
    }

    fun viewModel(dispatcher: CoroutineDispatcher) = OnboardingViewModel(
        setup = setup,
        saveStep = SaveSetupStepUseCase(writer, dispatcher),
        skipSetup = SkipSetupUseCase(writer, dispatcher),
        units = units,
        equipment = equipment,
        selection = selection,
        goals = goals,
        weekdays = weekdays,
        records = records,
        catalog = catalog,
        readiness = readiness,
        dispatcher = dispatcher
    )
}

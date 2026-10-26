package com.hydrafit.app.feature.progress

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.unit.DistanceUnit
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.ExerciseHistoryPage
import com.hydrafit.app.core.domain.workout.ExerciseHistoryRequest
import com.hydrafit.app.core.domain.workout.ExerciseProgress
import com.hydrafit.app.core.domain.workout.ExerciseProgressRepository
import com.hydrafit.app.core.domain.workout.ExerciseProgressRequest
import com.hydrafit.app.core.domain.workout.WorkoutHistorySet
import com.hydrafit.app.core.userdata.measurements.BodyWeightMeasurement
import com.hydrafit.app.core.userdata.measurements.BodyWeightRepository
import com.hydrafit.app.core.userdata.settings.DistanceUnitRepository
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class, ExperimentalCoroutinesApi::class)
class ProgressModuleVerificationTest {

    /**
     * `verify()` cannot reflect a lambda `viewModel { }` chain, so the module is verified statically
     * for the extra types it needs and the ViewModel is then resolved from a real container with
     * fake ports and the test Main dispatcher.
     */
    @Test
    fun progressModuleVerifiesAndTheViewModelResolvesAtRuntime() = runTest {
        progressModule.verify(
            extraTypes = listOf(
                ExerciseProgressRepository::class,
                ExerciseCatalog::class,
                WeightUnitRepository::class,
                DistanceUnitRepository::class,
                TimeProvider::class,
                BodyWeightRepository::class
            )
        )

        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val koin = koinApplication {
            modules(
                module {
                    single<ExerciseProgressRepository> { FakeProgressRepository }
                    single<ExerciseCatalog> { FakeCatalog }
                    single<WeightUnitRepository> { FakeWeightUnitRepository }
                    single<DistanceUnitRepository> { FakeDistanceUnitRepository }
                    single<TimeProvider> { TimeProvider { 0L } }
                    single<BodyWeightRepository> { FakeBodyWeightRepository }
                },
                progressModule
            )
        }.koin
        try {
            assertNotNull(koin.get<ProgressViewModel>())
            assertNotNull(koin.get<BodyWeightViewModel>())
            advanceUntilIdle()
        } finally {
            koin.close()
            Dispatchers.resetMain()
        }
    }

    private object FakeProgressRepository : ExerciseProgressRepository {
        override fun observeHistory(request: ExerciseHistoryRequest): Flow<ExerciseHistoryPage> =
            flowOf(ExerciseHistoryPage(emptyList(), hasMore = false))

        override fun observeProgress(request: ExerciseProgressRequest): Flow<ExerciseProgress> =
            flowOf(ExerciseProgress(emptyList(), emptyList(), emptyList()))

        override fun observeStationOptions(
            exerciseId: String
        ): Flow<List<com.hydrafit.app.core.domain.equipment.StationAttribution>> =
            flowOf(emptyList())

        override fun observeSet(exerciseId: String, setId: Long): Flow<WorkoutHistorySet?> =
            flowOf(null)

        override fun observeOccurrenceExerciseIds(occurrenceId: Long): Flow<List<String>> =
            flowOf(emptyList())
    }

    private object FakeCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = emptyList()
    }

    private object FakeWeightUnitRepository : WeightUnitRepository {
        override suspend fun selectedUnit(): WeightUnit = WeightUnit.KG
        override fun unitFlow(): Flow<WeightUnit> = flowOf(WeightUnit.KG)
        override suspend fun setUnit(unit: WeightUnit) = Unit
    }

    private object FakeDistanceUnitRepository : DistanceUnitRepository {
        override suspend fun selectedUnit(): DistanceUnit = DistanceUnit.METERS
        override fun unitFlow(): Flow<DistanceUnit> = flowOf(DistanceUnit.METERS)
        override suspend fun setUnit(unit: DistanceUnit) = Unit
    }

    /** Construction must not write: every read is empty and every mutation errors if it is reached. */
    private object FakeBodyWeightRepository : BodyWeightRepository {
        override fun observeMeasurements(): Flow<List<BodyWeightMeasurement>> = flowOf(emptyList())

        override suspend fun measurements(): List<BodyWeightMeasurement> = emptyList()

        override suspend fun add(localEpochDay: Long, weightKg: Double, replace: Boolean) =
            error("No body-weight writes are expected in graph verification")

        override suspend fun update(
            previousLocalEpochDay: Long,
            localEpochDay: Long,
            weightKg: Double
        ) = error("No body-weight writes are expected in graph verification")

        override suspend fun delete(localEpochDay: Long) =
            error("No body-weight writes are expected in graph verification")

        override fun observeGoal(): Flow<Double?> = flowOf(null)

        override suspend fun setGoal(weightKg: Double?) =
            error("No body-weight writes are expected in graph verification")
    }
}

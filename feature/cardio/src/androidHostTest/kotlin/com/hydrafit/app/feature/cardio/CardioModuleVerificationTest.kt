package com.hydrafit.app.feature.cardio

import com.hydrafit.app.core.domain.cardio.CardioActivity
import com.hydrafit.app.core.domain.cardio.CardioRepository
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.unit.DistanceUnit
import com.hydrafit.app.core.userdata.settings.DistanceUnitRepository
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
class CardioModuleVerificationTest {

    /**
     * `verify()` cannot reflect a lambda `viewModel { }` chain, so the module is verified statically
     * for the extra types it needs and the ViewModel is then resolved from a real container with
     * fake ports and the test Main dispatcher.
     */
    @Test
    fun cardioModuleVerifiesAndTheViewModelResolvesAtRuntime() = runTest {
        cardioModule.verify(
            extraTypes = listOf(
                CardioRepository::class,
                DistanceUnitRepository::class,
                TimeProvider::class
            )
        )

        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val koin = koinApplication {
            modules(
                module {
                    single<CardioRepository> { NoWriteCardioRepository }
                    single<DistanceUnitRepository> { FakeDistanceUnitRepository }
                    single<TimeProvider> { TimeProvider { 0L } }
                },
                cardioModule
            )
        }.koin
        try {
            assertNotNull(koin.get<CardioViewModel>())
            advanceUntilIdle()
        } finally {
            koin.close()
            Dispatchers.resetMain()
        }
    }

    /** Construction must not write: every read is empty and every mutation errors if it is reached. */
    private object NoWriteCardioRepository : CardioRepository {
        override fun activitiesFlow(): Flow<List<CardioActivity>> = flowOf(emptyList())

        override suspend fun all(): List<CardioActivity> = emptyList()

        override suspend fun add(activity: CardioActivity): Long =
            error("No cardio writes are expected in graph verification")

        override suspend fun update(activity: CardioActivity) =
            error("No cardio writes are expected in graph verification")

        override suspend fun delete(id: Long) =
            error("No cardio writes are expected in graph verification")

        override suspend fun byIdentity(identity: String): CardioActivity? = null
    }

    private object FakeDistanceUnitRepository : DistanceUnitRepository {
        override suspend fun selectedUnit(): DistanceUnit = DistanceUnit.METERS

        override fun unitFlow(): Flow<DistanceUnit> = flowOf(DistanceUnit.METERS)

        override suspend fun setUnit(unit: DistanceUnit) = Unit
    }
}

package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.routine.RoutineEntry
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.RoutineWorkout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightRoutineTemplateRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightRoutineTemplateRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        repository = SqlDelightRoutineTemplateRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun savesAndReloadsATemplateWithNestedWorkoutsAndSlots() = runTest {
        val id = repository.save(template(name = "Upper"))

        val reloaded = requireNotNull(repository.get(id))
        assertEquals("Upper", reloaded.name)
        assertEquals(1, reloaded.revision)
        assertEquals(1, reloaded.workouts.size)
        assertEquals(SplitFocus.PUSH, reloaded.workouts[0].focus)
        assertEquals(listOf("bench-press", "overhead-press"), reloaded.workouts[0].entries.map { it.exerciseId })
        assertEquals(82.5, reloaded.workouts[0].entries[0].weightKg)
        assertNull(reloaded.workouts[0].entries[1].weightKg)
        assertNull(reloaded.sourcePlanId)
    }

    @Test
    fun updatingPreservesWorkoutAndSlotIdsAcrossReordering() = runTest {
        val id = repository.save(template(name = "Upper"))
        val before = requireNotNull(repository.get(id))
        val benchId = before.workouts[0].entries[0].id
        val ohpId = before.workouts[0].entries[1].id

        repository.save(
            before.copy(
                name = "Upper v2",
                revision = 2,
                updatedAtMillis = 2_000L,
                workouts = listOf(
                    before.workouts[0].copy(
                        entries = listOf(before.workouts[0].entries[1], before.workouts[0].entries[0])
                    )
                )
            )
        )

        val reloaded = requireNotNull(repository.get(id))
        assertEquals("Upper v2", reloaded.name)
        assertEquals(listOf(ohpId, benchId), reloaded.workouts[0].entries.map { it.id })
        assertEquals(listOf("overhead-press", "bench-press"), reloaded.workouts[0].entries.map { it.exerciseId })
        assertEquals(listOf(0, 1), reloaded.workouts[0].entries.map { it.position })
    }

    @Test
    fun removingAWorkoutAndSlotDeletesOnlyTheRemovedRows() = runTest {
        val id = repository.save(
            template(
                name = "Two days",
                workouts = listOf(
                    workout(name = "Day 1", position = 0, focus = SplitFocus.PUSH),
                    workout(name = "Day 2", position = 1, focus = SplitFocus.PULL)
                )
            )
        )
        val before = requireNotNull(repository.get(id))

        repository.save(
            before.copy(workouts = listOf(before.workouts[1].copy(entries = emptyList())))
        )

        val reloaded = requireNotNull(repository.get(id))
        assertEquals(listOf("Day 2"), reloaded.workouts.map { it.name })
        assertTrue(reloaded.workouts[0].entries.isEmpty())
        assertEquals(1, database.routineTemplateQueries.selectAllWorkouts().executeAsList().size)
        assertEquals(0, database.routineTemplateQueries.selectAllEntries().executeAsList().size)
    }

    @Test
    fun archivesAndRestoresATemplate() = runTest {
        val id = repository.save(template(name = "Upper"))

        repository.setArchived(id, 99L)
        assertEquals(99L, requireNotNull(repository.get(id)).archivedAtMillis)

        repository.setArchived(id, null)
        assertNull(requireNotNull(repository.get(id)).archivedAtMillis)
    }

    @Test
    fun observingOrdersByMostRecentlyUpdated() = runTest {
        val first = repository.save(template(name = "First", updatedAt = 1_000L))
        val second = repository.save(template(name = "Second", updatedAt = 2_000L))

        val observed = repository.observeAll().first()

        assertEquals(listOf(second, first), observed.map { it.id })
    }

    @Test
    fun deletingRemovesTheTemplateAndItsChildren() = runTest {
        val id = repository.save(template(name = "Upper"))

        repository.delete(id)

        assertNull(repository.get(id))
        assertEquals(0, database.routineTemplateQueries.selectAllWorkouts().executeAsList().size)
        assertEquals(0, database.routineTemplateQueries.selectAllEntries().executeAsList().size)
        assertEquals(0, database.routineTemplateQueries.selectAllTemplates().executeAsList().size)
    }

    private fun template(
        name: String,
        updatedAt: Long = 1_000L,
        workouts: List<RoutineWorkout> = listOf(workout())
    ) = RoutineTemplate(
        name = name,
        revision = 1,
        createdAtMillis = 1_000L,
        updatedAtMillis = updatedAt,
        workouts = workouts
    )

    private fun workout(
        name: String = "Day 1",
        position: Int = 0,
        focus: SplitFocus? = SplitFocus.PUSH
    ) = RoutineWorkout(
        position = position,
        name = name,
        focus = focus,
        entries = listOf(
            RoutineEntry(position = 0, exerciseId = "bench-press", sets = 3, reps = 8, weightKg = 82.5),
            RoutineEntry(position = 1, exerciseId = "overhead-press", sets = 3, reps = 8)
        )
    )
}

package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutHistoryEntry
import com.hydrafit.app.core.domain.workout.WorkoutHistoryKey
import com.hydrafit.app.core.domain.workout.WorkoutHistoryNameSource
import com.hydrafit.app.core.domain.workout.WorkoutHistoryRequest
import com.hydrafit.app.core.domain.workout.WorkoutHistorySetPage
import com.hydrafit.app.core.domain.workout.WorkoutHistorySetRequest
import com.hydrafit.app.core.domain.workout.WorkoutSetCorrection
import com.hydrafit.app.core.domain.workout.unicodeCaseFold
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

class SqlDelightWorkoutHistoryRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightWorkoutHistoryRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        repository = SqlDelightWorkoutHistoryRepository(database)
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun explicitIdentitiesListNewestFirstAndNullIdentitiesNeverGroup() = runTest {
        insertExercise("back-squat", "Back Squat")
        insertSession("s1", startedAt = 100L, localEpochDay = 10L)
        insertSession("s2", startedAt = 200L, localEpochDay = 11L)
        insertSet("back-squat", sessionId = "s1", performedAt = 110L)
        insertSet("back-squat", sessionId = "s2", performedAt = 210L)
        insertSet("back-squat", sessionId = null, performedAt = 150L)
        insertSet("back-squat", sessionId = null, performedAt = 140L)

        val page = repository.entries()

        assertEquals(
            listOf(
                WorkoutHistoryKey.Session("s2"),
                WorkoutHistoryKey.UnassignedSet(3L),
                WorkoutHistoryKey.UnassignedSet(4L),
                WorkoutHistoryKey.Session("s1")
            ),
            page.map { it.key }
        )
        assertEquals(listOf(200L, 150L, 140L, 100L), page.map { it.timestampMillis })
    }

    @Test
    fun twoSessionsOnTheSameLocalDayStayDistinct() = runTest {
        insertExercise("back-squat", "Back Squat")
        insertSession("am", startedAt = 100L, localEpochDay = 10L)
        insertSession("pm", startedAt = 200L, localEpochDay = 10L)
        insertSet("back-squat", sessionId = "am", performedAt = 110L)
        insertSet("back-squat", sessionId = "pm", performedAt = 210L)

        assertEquals(
            listOf(WorkoutHistoryKey.Session("pm"), WorkoutHistoryKey.Session("am")),
            repository.entries().map { it.key }
        )
    }

    @Test
    fun notedEmptySessionAppearsWhileAnEmptyUnnotedSessionDoesNot() = runTest {
        insertExercise("back-squat", "Back Squat")
        insertSession("noted-empty", startedAt = 300L, localEpochDay = 12L)
        insertSession("silent-empty", startedAt = 400L, localEpochDay = 13L)
        insertSessionNote("noted-empty", "")

        val entries = repository.entries()

        assertEquals(listOf(WorkoutHistoryKey.Session("noted-empty")), entries.map { it.key })
        assertEquals("", entries.single().note)
        assertEquals(0, entries.single().recordedSetCount)
        assertEquals(WorkoutHistoryNameSource.NONE, entries.single().nameSource)
    }

    @Test
    fun noteTextSearchesAndAnExplicitClearStaysDistinctFromAbsent() = runTest {
        insertExercise("back-squat", "Back Squat")
        insertSession("s1", startedAt = 100L, localEpochDay = 10L)
        insertSet("back-squat", sessionId = "s1", performedAt = 110L)

        assertNull(repository.entry(WorkoutHistoryKey.Session("s1"))!!.note)

        insertSessionNote("s1", "great focus today")
        assertEquals(
            "great focus today",
            repository.entry(WorkoutHistoryKey.Session("s1"))!!.note
        )
        assertEquals(
            listOf(WorkoutHistoryKey.Session("s1")),
            repository.entries(WorkoutHistoryRequest(search = "focus")).map { it.key }
        )

        insertSessionNote("s1", "")
        assertEquals("", repository.entry(WorkoutHistoryKey.Session("s1"))!!.note)
        assertTrue(repository.entries(WorkoutHistoryRequest(search = "focus")).isEmpty())
    }

    @Test
    fun missingSessionIdentityIsReportedHonestly() = runTest {
        insertExercise("back-squat", "Back Squat")
        // No workoutSession row is created for "ghost".
        insertSet("back-squat", sessionId = "ghost", performedAt = 5L * 86_400_000L)

        val entry = repository.entries().single()

        assertEquals(WorkoutHistoryKey.Session("ghost"), entry.key)
        assertTrue(entry.sessionMissing)
        assertNull(entry.note)
        assertEquals(5L * 86_400_000L, entry.timestampMillis)
        assertEquals(5L, entry.localEpochDay)
        assertEquals(1, entry.recordedSetCount)
    }

    @Test
    fun searchMatchesCurrentCatalogNamesAndIsLiteral() = runTest {
        insertExercise("back-squat", "Back Squat")
        insertExercise("bench-press", "Bench Press 45%")
        insertSession("s1", startedAt = 100L, localEpochDay = 10L)
        insertSet("back-squat", sessionId = "s1", performedAt = 110L)

        // Current catalog name matching is case-insensitive for ASCII.
        assertEquals(
            listOf(WorkoutHistoryKey.Session("s1")),
            repository.entries(WorkoutHistoryRequest(search = "back squat")).map { it.key }
        )
        // The percent sign and underscore are matched literally, not as wildcards.
        assertTrue(repository.entries(WorkoutHistoryRequest(search = "45%")).isEmpty())
        assertTrue(repository.entries(WorkoutHistoryRequest(search = "45_")).isEmpty())
        assertTrue(repository.entries(WorkoutHistoryRequest(search = "zzz")).isEmpty())

        insertSession("literal", startedAt = 200L, localEpochDay = 10L)
        insertSet("bench-press", sessionId = "literal", performedAt = 210L)
        assertEquals(
            listOf(WorkoutHistoryKey.Session("literal")),
            repository.entries(WorkoutHistoryRequest(search = "45%")).map { it.key }
        )
        assertTrue(repository.entries(WorkoutHistoryRequest(search = "45_")).isEmpty())
        assertTrue(repository.entries(WorkoutHistoryRequest(search = "45\\%")).isEmpty())

        insertExercise("accent", "Épaules")
        insertSession("accent-session", startedAt = 300L, localEpochDay = 10L)
        insertSet("accent", sessionId = "accent-session", performedAt = 310L)
        assertEquals(
            listOf(WorkoutHistoryKey.Session("accent-session")),
            repository.entries(WorkoutHistoryRequest(search = "ÉPAULES")).map { it.key }
        )
        assertEquals(
            listOf(WorkoutHistoryKey.Session("accent-session")),
            repository.entries(WorkoutHistoryRequest(search = "épaules")).map { it.key }
        )
    }

    @Test
    fun exerciseSetupNotesAreNeverSearchedAsHistory() = runTest {
        insertExercise("back-squat", "Back Squat")
        insertSession("s1", startedAt = 100L, localEpochDay = 10L)
        insertSet("back-squat", sessionId = "s1", performedAt = 110L)
        database.exerciseNoteQueries.upsert("back-squat", "uniquetoken setup note")

        assertTrue(repository.entries(WorkoutHistoryRequest(search = "uniquetoken")).isEmpty())
    }

    @Test
    fun paginationPresentsEveryEntryExactlyOnceOnUnchangedData() = runTest {
        insertExercise("back-squat", "Back Squat")
        repeat(7) { index ->
            insertSession("s$index", startedAt = 100L + index * 10L, localEpochDay = 10L)
            insertSet("back-squat", sessionId = "s$index", performedAt = 105L + index * 10L)
        }

        repeat(3) { index ->
            insertSession("tied-$index", startedAt = 999L, localEpochDay = 10L)
            insertSet("back-squat", sessionId = "tied-$index", performedAt = 999L)
        }

        val collected: MutableList<WorkoutHistoryKey> = mutableListOf()
        var request = WorkoutHistoryRequest(pageSize = 3)
        var pages = 0
        while (true) {
            val page = repository.repositoryEntries(request)
            collected += page.entries.map { it.key }
            pages++
            val cursor = page.nextCursor(request) ?: break
            request = request.copy(cursor = cursor)
        }

        val expectedKeys: List<WorkoutHistoryKey> =
            (2 downTo 0).map { WorkoutHistoryKey.Session("tied-$it") } +
                (6 downTo 0).map { WorkoutHistoryKey.Session("s$it") }
        assertEquals(expectedKeys, collected)
        assertEquals(expectedKeys.size, collected.toSet().size, "no duplicate entries across pages")
        assertEquals(4, pages)
    }

    @Test
    fun localDayFilterUsesTheStoredSessionDayAndTheCallerOffsetForUnassignedSets() = runTest {
        val day = 86_400_000L
        insertExercise("back-squat", "Back Squat")
        insertSession("s1", startedAt = 5L * day, localEpochDay = 5L)
        insertSet("back-squat", sessionId = "s1", performedAt = 5L * day + 10L)
        insertSet("back-squat", sessionId = null, performedAt = 5L * day + 20L)
        insertSet("back-squat", sessionId = null, performedAt = 6L * day + 20L)

        val onDayFive = repository.entries(
            WorkoutHistoryRequest(dayFrom = 5L, dayTo = 5L, utcOffsetMillis = 0L)
        )
        assertEquals(
            setOf(WorkoutHistoryKey.Session("s1"), WorkoutHistoryKey.UnassignedSet(2L)),
            onDayFive.map { it.key }.toSet()
        )

        val onDaySix = repository.entries(
            WorkoutHistoryRequest(dayFrom = 6L, dayTo = 6L, utcOffsetMillis = 0L)
        )
        assertEquals(listOf(WorkoutHistoryKey.UnassignedSet(3L)), onDaySix.map { it.key })
    }

    @Test
    fun derivedDaysUseFloorDivisionForNegativeShiftedTimes() = runTest {
        insertExercise("back-squat", "Back Squat")
        insertSet("back-squat", sessionId = null, performedAt = 0L)

        val entry = repository.entry(WorkoutHistoryKey.UnassignedSet(1L), -3_600_000L)!!
        assertEquals(-1L, entry.localEpochDay)

        val filtered = repository.entries(
            WorkoutHistoryRequest(dayFrom = -1L, dayTo = -1L, utcOffsetMillis = -3_600_000L)
        )
        assertEquals(listOf(WorkoutHistoryKey.UnassignedSet(1L)), filtered.map { it.key })
    }

    @Test
    fun entryMetadataAndRecordedSetsResolveByExplicitIdentity() = runTest {
        insertExercise("back-squat", "Back Squat")
        insertSession("s1", startedAt = 100L, localEpochDay = 10L)
        insertSet(
            exerciseId = "back-squat",
            sessionId = "s1",
            performedAt = 110L,
            reps = 5L,
            weightKg = 100.0,
            rir = 2L
        )
        insertSet("back-squat", sessionId = "s1", performedAt = 120L, isWarmup = true)

        val entry = repository.entry(WorkoutHistoryKey.Session("s1"))!!
        assertEquals(2, entry.recordedSetCount)
        assertEquals(WorkoutHistoryNameSource.CURRENT_CATALOG, entry.nameSource)

        val sets = repository.sets(WorkoutHistoryKey.Session("s1")).sets
        assertEquals(listOf(120L, 110L), sets.map { it.performedAtMillis })
        assertTrue(sets.first().isWarmup)
        assertEquals(LoadKind.EXTERNAL, sets.last().loadKind)
        assertEquals(2, sets.last().rir)
        assertEquals("Back Squat", sets.last().exerciseName)

        val firstSetPage = repository.sets(
            WorkoutHistoryKey.Session("s1"),
            WorkoutHistorySetRequest(pageSize = 1)
        )
        assertTrue(firstSetPage.hasMore)
        val cursor = firstSetPage.nextCursor(WorkoutHistorySetRequest(pageSize = 1))!!
        val secondSetPage = repository.sets(
            WorkoutHistoryKey.Session("s1"),
            WorkoutHistorySetRequest(pageSize = 1, cursor = cursor)
        )
        assertEquals(listOf(110L), secondSetPage.sets.map { it.performedAtMillis })
        assertFalse(secondSetPage.hasMore)
    }

    @Test
    fun recordedSetsCarryTheirOccurrenceAttribution() = runTest {
        insertExercise("back-squat", "Back Squat")
        insertSession("s1", startedAt = 100L, localEpochDay = 10L)
        insertSet(
            exerciseId = "back-squat",
            sessionId = "s1",
            performedAt = 110L,
            occurrenceId = 77L
        )
        insertSet("back-squat", sessionId = "s1", performedAt = 120L)
        val unassigned = insertSet(
            exerciseId = "back-squat",
            sessionId = null,
            performedAt = 130L,
            occurrenceId = 88L
        )

        val sets = repository.sets(WorkoutHistoryKey.Session("s1")).sets
        assertEquals(77L, sets.single { it.performedAtMillis == 110L }.occurrenceId)
        assertNull(sets.single { it.performedAtMillis == 120L }.occurrenceId)
        assertEquals(
            88L,
            repository.sets(WorkoutHistoryKey.UnassignedSet(unassigned)).sets.single().occurrenceId
        )
    }

    @Test
    fun aCorrectionReProjectsTheEntryAndKeepsANotedSessionVisibleWithoutSets() = runTest {
        insertExercise("back-squat", "Back Squat")
        insertSession("noted", startedAt = 100L, localEpochDay = 10L)
        insertSession("later", startedAt = 1_000L, localEpochDay = 10L)
        insertSessionNote("noted", "Kept with the session")
        val setId = insertSet("back-squat", sessionId = "noted", performedAt = 110L)

        SqlDelightSessionResegmenter(database).resegmentAfterSetCorrection(
            setId = setId,
            correction = WorkoutSetCorrection(
                reps = 5,
                weightKg = 100.0,
                rir = null,
                performedAtMillis = 1_100L
            ),
            utcOffsetMillis = 0L
        )

        // The set moved to the nearer session; the noted session stays visible with no sets.
        val noted = repository.entry(WorkoutHistoryKey.Session("noted"))!!
        assertEquals(0, noted.recordedSetCount)
        assertEquals("Kept with the session", noted.note)
        assertTrue(repository.sets(WorkoutHistoryKey.Session("noted")).sets.isEmpty())
        assertEquals(
            listOf(setId),
            repository.sets(WorkoutHistoryKey.Session("later")).sets.map { it.id }
        )
    }

    @Test
    fun aRenamedExerciseShowsAndSearchesByItsEffectiveName() = runTest {
        insertExercise("back-squat", "Back Squat")
        insertSession("s1", startedAt = 100L, localEpochDay = 10L)
        insertSet("back-squat", sessionId = "s1", performedAt = 110L)
        val overrides = SqlDelightExerciseOverrideRepository(database)

        overrides.update(
            exerciseId = "back-squat",
            name = "Safety Bar Squat",
            requiredEquipment = emptySet(),
            movementPattern = null,
            unilateral = null,
            loadCapability = null,
            involvements = emptyMap()
        )

        // The recorded set shows the name the user sees, and only that name finds it.
        assertEquals(
            "Safety Bar Squat",
            repository.sets(WorkoutHistoryKey.Session("s1")).sets.single().exerciseName
        )
        assertEquals(
            listOf(WorkoutHistoryKey.Session("s1")),
            repository.entries(WorkoutHistoryRequest(search = "safety bar")).map { it.key }
        )
        assertTrue(
            repository.entries(WorkoutHistoryRequest(search = "back squat")).isEmpty()
        )

        overrides.reset("back-squat")

        assertEquals(
            "Back Squat",
            repository.sets(WorkoutHistoryKey.Session("s1")).sets.single().exerciseName
        )
        assertTrue(
            repository.entries(WorkoutHistoryRequest(search = "safety bar")).isEmpty()
        )
        assertEquals(
            listOf(WorkoutHistoryKey.Session("s1")),
            repository.entries(WorkoutHistoryRequest(search = "back squat")).map { it.key }
        )
    }

    @Test
    fun unassignedSetKeysDecodeThePaddedIdExactlyAsStored() = runTest {
        insertExercise("back-squat", "Back Squat")
        insertSetWithId(id = -19L, exerciseId = "back-squat", sessionId = null, performedAt = 10L)
        insertSetWithId(
            id = Long.MAX_VALUE,
            exerciseId = "back-squat",
            sessionId = null,
            performedAt = 20L
        )

        val keys = repository.entries(WorkoutHistoryRequest()).map { it.key }
        assertTrue(WorkoutHistoryKey.UnassignedSet(-19L) in keys)
        assertTrue(WorkoutHistoryKey.UnassignedSet(Long.MAX_VALUE) in keys)
        // The decoded identity addresses the same set for both signs.
        assertEquals(
            listOf(-19L),
            repository.sets(WorkoutHistoryKey.UnassignedSet(-19L)).sets.map { it.id }
        )
        assertEquals(
            listOf(Long.MAX_VALUE),
            repository.sets(WorkoutHistoryKey.UnassignedSet(Long.MAX_VALUE)).sets.map { it.id }
        )
    }

    @Test
    fun unassignedSetEntryAndItsSingleSetResolveBySetId() = runTest {
        insertExercise("back-squat", "Back Squat")
        val setId = insertSet("back-squat", sessionId = null, performedAt = 42L)

        val entry = repository.entry(WorkoutHistoryKey.UnassignedSet(setId))!!
        assertEquals(1, entry.recordedSetCount)

        val page = repository.sets(WorkoutHistoryKey.UnassignedSet(setId))
        assertEquals(listOf(setId), page.sets.map { it.id })
        assertFalse(page.hasMore)
    }

    @Test
    fun readsCreateNothingAndAlterNoRecords() = runTest {
        insertExercise("back-squat", "Back Squat")
        insertSession("s1", startedAt = 100L, localEpochDay = 10L)
        insertSet("back-squat", sessionId = "s1", performedAt = 110L)

        repository.entries(WorkoutHistoryRequest(search = "back"))
        repository.entry(WorkoutHistoryKey.Session("s1"))
        repository.sets(WorkoutHistoryKey.Session("s1"))

        assertEquals(1, database.workoutSessionQueries.selectAllSessions().executeAsList().size)
        assertEquals(1, database.workoutLogQueries.selectAllSets().executeAsList().size)
    }

    @Test
    fun reReadsReflectSessionTimeCorrectionsAndNoteChanges() = runTest {
        insertExercise("back-squat", "Back Squat")
        insertSession("s1", startedAt = 100L, localEpochDay = 10L)
        insertSet("back-squat", sessionId = "s1", performedAt = 110L)
        insertSession("s2", startedAt = 200L, localEpochDay = 11L)
        insertSet("back-squat", sessionId = "s2", performedAt = 210L)

        assertEquals(
            listOf(WorkoutHistoryKey.Session("s2"), WorkoutHistoryKey.Session("s1")),
            repository.entries().map { it.key }
        )

        // A corrected session start re-orders history without changing either identity.
        database.workoutSessionQueries.updateSessionBounds(
            startedAtMillis = 300L,
            endedAtMillis = null,
            localEpochDay = 11L,
            id = "s1"
        )
        assertEquals(
            listOf(WorkoutHistoryKey.Session("s1"), WorkoutHistoryKey.Session("s2")),
            repository.entries().map { it.key }
        )

        insertSessionNote("s2", "added later")
        assertEquals(
            "added later",
            repository.entry(WorkoutHistoryKey.Session("s2"))!!.note
        )
    }

    @Test
    fun catalogRenameChangesSearchAndDisplayedCurrentName() = runTest {
        insertCustomExercise("back-squat", "Back Squat")
        insertSession("s1", startedAt = 100L, localEpochDay = 10L)
        insertSet("back-squat", sessionId = "s1", performedAt = 110L)

        assertEquals(1, repository.entries(WorkoutHistoryRequest(search = "back squat")).size)
        renameCustomExercise("back-squat", "Front Squat")
        assertTrue(repository.entries(WorkoutHistoryRequest(search = "back squat")).isEmpty())
        assertEquals(1, repository.entries(WorkoutHistoryRequest(search = "front squat")).size)
        val renamedSet = repository.sets(WorkoutHistoryKey.Session("s1")).sets.single()
        assertEquals("Front Squat", renamedSet.exerciseName)
    }

    @Test
    fun deletingTheLastSetAndResegmentingRefreshesSessionEntryAndDetails() = runTest {
        insertCustomExercise("back-squat", "Back Squat")
        insertSession("s1", startedAt = 100L, localEpochDay = 10L)
        val movingSetId = insertSet("back-squat", sessionId = "s1", performedAt = 110L)
        insertSession("s2", startedAt = 200L, localEpochDay = 11L)
        val deletingSetId = insertSet("back-squat", sessionId = "s2", performedAt = 210L)

        // The existing correction path moves the set and recomputes affected session bounds/day.
        SqlDelightSessionResegmenter(database).resegmentAfterTimeCorrection(
            setId = movingSetId,
            performedAtMillis = 120L,
            utcOffsetMillis = 0L
        )
        assertEquals(1, repository.entry(WorkoutHistoryKey.Session("s1"))!!.recordedSetCount)
        assertEquals(1, repository.entry(WorkoutHistoryKey.Session("s2"))!!.recordedSetCount)

        database.workoutLogQueries.deleteSet(deletingSetId)
        assertNull(repository.entry(WorkoutHistoryKey.Session("s2")))
        assertTrue(repository.sets(WorkoutHistoryKey.Session("s2")).sets.isEmpty())

        insertSessionNote("s2", "kept note")
        val retainedEmpty = repository.entry(WorkoutHistoryKey.Session("s2"))!!
        assertEquals(0, retainedEmpty.recordedSetCount)
        assertEquals("kept note", retainedEmpty.note)
    }

    @Test
    fun theHistoryFlowEmitsAgainAfterARecordedSetIsInserted() = runBlocking {
        insertExercise("back-squat", "Back Squat")
        insertSession("s1", startedAt = 100L, localEpochDay = 10L)
        insertSet("back-squat", sessionId = "s1", performedAt = 110L)

        val emissions = Channel<List<WorkoutHistoryKey>>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) {
            repository.observeEntries(WorkoutHistoryRequest()).collect { page ->
                emissions.send(page.entries.map { it.key })
            }
        }
        try {
            val initial = receiveEmission(emissions)
            assertEquals(listOf(WorkoutHistoryKey.Session("s1")), initial)

            insertSet("back-squat", sessionId = null, performedAt = 150L)

            val updated = receiveEmission(emissions)
            assertEquals(
                listOf(WorkoutHistoryKey.UnassignedSet(2L), WorkoutHistoryKey.Session("s1")),
                updated
            )
        } finally {
            job.cancel()
        }
    }

    @Test
    fun searchMatchesFoldingsWrittenByTheRealProducers() = runTest {
        SeedEquipmentCatalog(database).seed()
        SeedExerciseCatalog(database).seed()
        val custom = SqlDelightCustomExerciseRepository(database).add(
            name = "Straße Press",
            requiredEquipment = setOf(EquipmentTag.BARBELL),
            involvements = mapOf(MuscleGroup.LATS to 1.0, MuscleGroup.GLUTES to 1.0),
            movementPattern = MovementPattern.HINGE
        )
        insertSession("built-in", startedAt = 100L, localEpochDay = 10L)
        insertSet("back-squat", sessionId = "built-in", performedAt = 110L)
        insertSession("custom", startedAt = 200L, localEpochDay = 11L)
        insertSet(custom.id, sessionId = "custom", performedAt = 210L)
        SqlDelightSessionNoteRepository(database).save("custom", "Grüße ΣΟΦΙΑ")

        // Seeded catalog name, via SeedExerciseCatalog's folded projection.
        assertEquals(1, repository.entries(WorkoutHistoryRequest(search = "BACK SQUAT")).size)
        // Custom name via SqlDelightCustomExerciseRepository; ß folds to `ss`.
        assertEquals(1, repository.entries(WorkoutHistoryRequest(search = "STRASSE PRESS")).size)
        // Session note via SqlDelightSessionNoteRepository; Greek case folds to lowercase.
        assertEquals(1, repository.entries(WorkoutHistoryRequest(search = "grüsse σοφια")).size)
    }

    private suspend fun SqlDelightWorkoutHistoryRepository.entries(
        request: WorkoutHistoryRequest = WorkoutHistoryRequest()
    ): List<WorkoutHistoryEntry> = observeEntries(request).first().entries

    private suspend fun SqlDelightWorkoutHistoryRepository.repositoryEntries(
        request: WorkoutHistoryRequest
    ) = observeEntries(request).first()

    private suspend fun SqlDelightWorkoutHistoryRepository.entry(
        key: WorkoutHistoryKey,
        utcOffsetMillis: Long = 0L
    ): WorkoutHistoryEntry? = observeEntry(key, utcOffsetMillis).first()

    private suspend fun SqlDelightWorkoutHistoryRepository.sets(
        key: WorkoutHistoryKey,
        request: WorkoutHistorySetRequest = WorkoutHistorySetRequest()
    ): WorkoutHistorySetPage = observeEntrySets(key, request).first()

    private suspend fun <T> receiveEmission(channel: Channel<T>): T =
        kotlinx.coroutines.withContext(Dispatchers.Default.limitedParallelism(1)) {
            kotlinx.coroutines.withTimeout(5_000) { channel.receive() }
        }

    private fun insertExercise(id: String, name: String) {
        database.exerciseQueries.insert(
            id = id,
            name = name,
            requiredEquipment = "BARBELL",
            movementPattern = "SQUAT",
            loadCapability = "EXTERNAL"
        )
        database.exerciseQueries.updateSearchName(searchName = unicodeCaseFold(name), id = id)
    }

    private fun insertCustomExercise(id: String, name: String) {
        database.exerciseQueries.insertCustom(
            id = id,
            name = name,
            requiredEquipment = "BARBELL",
            movementPattern = "SQUAT",
            isUnilateral = 0L,
            loadCapability = "EXTERNAL",
            involvements = null
        )
        database.exerciseQueries.updateSearchName(searchName = unicodeCaseFold(name), id = id)
    }

    private fun renameCustomExercise(id: String, name: String) {
        database.exerciseQueries.updateCustom(
            name = name,
            requiredEquipment = "BARBELL",
            movementPattern = "SQUAT",
            isUnilateral = 0L,
            loadCapability = "EXTERNAL",
            involvements = null,
            id = id
        )
        database.exerciseQueries.updateSearchName(searchName = unicodeCaseFold(name), id = id)
    }

    private fun insertSessionNote(sessionId: String, note: String) {
        database.sessionNoteQueries.upsert(sessionId, note)
        database.sessionNoteQueries.updateSearchNote(
            searchNote = unicodeCaseFold(note),
            sessionId = sessionId
        )
    }

    private fun insertSession(id: String, startedAt: Long, localEpochDay: Long) {
        database.workoutSessionQueries.insertSession(
            id = id,
            startedAtMillis = startedAt,
            endedAtMillis = null,
            localEpochDay = localEpochDay
        )
    }

    private fun insertSet(
        exerciseId: String,
        sessionId: String?,
        performedAt: Long,
        reps: Long = 5L,
        weightKg: Double? = 100.0,
        isWarmup: Boolean = false,
        rir: Long? = null,
        occurrenceId: Long? = null
    ): Long {
        database.workoutLogQueries.insertSet(
            exerciseId = exerciseId,
            reps = reps,
            weightKg = weightKg,
            performedAt = performedAt,
            isWarmup = if (isWarmup) 1L else 0L,
            involvements = null,
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = rir,
            sessionId = sessionId,
            loadKind = "EXTERNAL"
        )
        val id = database.workoutLogQueries.lastInsertedSetId().executeAsOne()
        if (occurrenceId != null) {
            database.workoutLogQueries.assignOccurrence(
                occurrenceId = occurrenceId,
                occurrenceEntryId = null,
                id = id
            )
        }
        return id
    }

    /** The restore path's explicit-id insert, used to exercise unusual stored identities. */
    private fun insertSetWithId(
        id: Long,
        exerciseId: String,
        sessionId: String?,
        performedAt: Long
    ) {
        database.workoutLogQueries.insertSetWithId(
            id = id,
            exerciseId = exerciseId,
            reps = 5L,
            weightKg = 100.0,
            performedAt = performedAt,
            isWarmup = 0L,
            involvements = null,
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = sessionId,
            occurrenceId = null,
            occurrenceEntryId = null,
            loadKind = "EXTERNAL"
        )
    }
}

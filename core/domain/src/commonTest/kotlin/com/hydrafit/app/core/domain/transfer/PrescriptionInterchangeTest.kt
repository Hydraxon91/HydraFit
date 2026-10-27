package com.hydrafit.app.core.domain.transfer

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicy
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicyJson
import com.hydrafit.app.core.domain.engine.SlotSetTargets
import com.hydrafit.app.core.domain.engine.SlotSetTargetsJson
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.routine.ClusterKind
import com.hydrafit.app.core.domain.routine.ClusterPrescription
import com.hydrafit.app.core.domain.routine.ClusterPrescriptionJson
import com.hydrafit.app.core.domain.routine.ClusterSegmentTarget
import com.hydrafit.app.core.domain.routine.RoutineEntry
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.RoutineTemplateRepository
import com.hydrafit.app.core.domain.routine.RoutineWorkout
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

/** The versioned prescription document: strict shape, privacy by construction, ordered export. */
class PrescriptionInterchangeTest {

    private val seeded = Exercise(
        id = "back-squat",
        name = "Back Squat",
        requiredEquipment = setOf(EquipmentTag.BARBELL),
        primaryMuscles = setOf(MuscleGroup.QUADS)
    )
    private val custom = Exercise(
        id = "user-my-lift",
        name = "My Lift",
        requiredEquipment = setOf(EquipmentTag.DUMBBELL),
        primaryMuscles = emptySet(),
        movementPattern = MovementPattern.CORE,
        isCustom = true,
        isUnilateral = true,
        loadCapability = ExerciseLoadCapability.BODYWEIGHT_ADDABLE,
        involvements = mapOf(MuscleGroup.ABS to 0.7, MuscleGroup.OBLIQUES to 0.3)
    )
    private val catalog = object : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = listOf(seeded, custom)
    }

    private val routine = RoutineTemplate(
        id = 7L,
        name = "Push day",
        workouts = listOf(
            com.hydrafit.app.core.domain.routine.RoutineWorkout(
                position = 0,
                name = "Day 1",
                entries = listOf(
                    RoutineEntry(
                        position = 0,
                        exerciseId = "back-squat",
                        sets = 3,
                        reps = 5,
                        weightKg = 100.0,
                        loadKind = LoadKind.EXTERNAL
                    ),
                    RoutineEntry(
                        position = 1,
                        exerciseId = "user-my-lift",
                        sets = 2,
                        reps = 12,
                        weightKg = 5.0,
                        loadKind = LoadKind.ADDED
                    )
                )
            )
        )
    )

    private fun repository() = object : RoutineTemplateRepository {
        override fun observeAll(): Flow<List<RoutineTemplate>> = flowOf(listOf(routine))
        override suspend fun get(id: Long): RoutineTemplate? = routine.takeIf { it.id == id }
        override suspend fun save(template: RoutineTemplate): Long = 0L
        override suspend fun setArchived(id: Long, archivedAtMillis: Long?) = Unit
        override suspend fun isReferencedByActivation(id: Long): Boolean = false
        override suspend fun delete(id: Long) = Unit
    }

    @Test
    fun exportOmitsWeightsByDefaultAndIncludesThemOnlyOnRequest() = runBlocking {
        val stripped = ExportPrescriptionsUseCase(repository(), catalog)(listOf(7L), false, "t", 1L)
        assertFalse(stripped.includesWeights)
        assertTrue(stripped.routines.single().allEntries().all { it.weightKg == null })
        PrescriptionValidator.validate(stripped)
        // The custom definition travels with its resolved involvement map.
        assertEquals(
            mapOf("ABS" to 0.7, "OBLIQUES" to 0.3),
            stripped.customExercises.single().involvements
        )
        assertEquals(listOf("DUMBBELL"), stripped.customExercises.single().requiredEquipment)

        val withWeights =
            ExportPrescriptionsUseCase(repository(), catalog)(listOf(7L), true, "t", 1L)
        assertTrue(withWeights.includesWeights)
        val entries = withWeights.routines.single().allEntries()
        assertEquals(listOf(100.0, 5.0), entries.map { it.weightKg })
        assertEquals(listOf("EXTERNAL", "ADDED"), entries.map { it.loadKind })
        assertEquals(listOf(3, 2), entries.map { it.sets })
        PrescriptionValidator.validate(withWeights)
    }

    @Test
    fun roundTripsThroughJsonAndRejectsUnknownFields() = runBlocking {
        val document = ExportPrescriptionsUseCase(repository(), catalog)(listOf(7L), true, "t", 42L)
        val text = PrescriptionJson.encode(document)
        assertEquals(document, PrescriptionJson.decode(text))

        // Every section the contract excludes is unrepresentable, so a document cannot carry one.
        listOf(
            "workoutSets",
            "personalRecords",
            "sessionNotes",
            "exerciseNotes",
            "credentials",
            "equipmentProfiles",
            "scheduleState",
            "activations",
            "planTargetExplanations"
        ).forEach { forbidden ->
            val smuggled = text.dropLast(1) + ",\"$forbidden\":[],}"
            val failure =
                assertFailsWith<PrescriptionException> { PrescriptionJson.decode(smuggled) }
            assertEquals(PrescriptionFailure.MALFORMED, failure.failure)
        }
        // The encoded document names only prescription fields.
        listOf("workoutSets", "personalRecord", "note", "credential", "equipmentProfile").forEach {
            assertFalse(text.contains(it), "encoded document leaked $it")
        }
    }

    @Test
    fun expandedMusclesRoundTripInCustomPrescriptionsAndUnknownTokensStillFail() = runBlocking {
        val original = ExportPrescriptionsUseCase(repository(), catalog)(listOf(7L), false, "t", 1L)
        val muscles = mapOf(
            "TIBIALIS_ANTERIOR" to 1.0,
            "SERRATUS_ANTERIOR" to 0.5,
            "HIP_FLEXORS" to 0.7
        )
        val document = original.copy(
            customExercises = original.customExercises.map { it.copy(involvements = muscles) }
        )
        PrescriptionValidator.validate(document)
        assertEquals(document, PrescriptionJson.decode(PrescriptionJson.encode(document)))
        val invalid = document.copy(
            customExercises = document.customExercises.map {
                it.copy(involvements = muscles + ("UNKNOWN_MUSCLE" to 1.0))
            }
        )
        val failure = assertFailsWith<PrescriptionException> {
            PrescriptionValidator.validate(invalid)
        }
        assertEquals(PrescriptionFailure.INVALID_VALUE, failure.failure)
    }

    @Test
    fun manualIsolationPatternsRoundTripWithoutMakingManualRoutinesInvalid() = runBlocking {
        val original = ExportPrescriptionsUseCase(repository(), catalog)(listOf(7L), false, "t", 1L)
        listOf("TRAP_ISOLATION", "NECK_ISOLATION").forEach { pattern ->
            val document = original.copy(
                customExercises = original.customExercises.map {
                    it.copy(movementPattern = pattern)
                }
            )
            PrescriptionValidator.validate(document)
            assertEquals(document, PrescriptionJson.decode(PrescriptionJson.encode(document)))
        }
        val invalid = original.copy(
            customExercises = original.customExercises.map {
                it.copy(movementPattern = "UNKNOWN_ISOLATION")
            }
        )
        val failure = assertFailsWith<PrescriptionException> {
            PrescriptionValidator.validate(invalid)
        }
        assertEquals(PrescriptionFailure.INVALID_VALUE, failure.failure)
    }

    @Test
    fun rejectsAMissingSectionAnUnknownVersionAndAMalformedBody() {
        val document = PrescriptionDocument(
            format = PRESCRIPTION_FORMAT,
            formatVersion = PRESCRIPTION_FORMAT_VERSION,
            appVersion = "t",
            exportedAtMillis = 1L,
            routines = listOf(
                PrescriptionRoutineRecord(
                    "Push",
                    workouts = listOf(
                        PrescriptionWorkoutRecord(
                            "Day 1",
                            entries = listOf(entry("back-squat"))
                        )
                    )
                )
            )
        )
        val text = PrescriptionJson.encode(document)
        val missing = text.replace(",\"customExercises\":[]", "")
        assertFailsWith<PrescriptionException> { PrescriptionJson.decode(missing) }
        assertFailsWith<PrescriptionException> { PrescriptionJson.decode("not json") }
        val unknownVersion = document.copy(formatVersion = 99)
        assertEquals(
            PrescriptionFailure.UNSUPPORTED_VERSION,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(unknownVersion)
            }
                .failure
        )
    }

    @Test
    fun aWeightsFreeDocumentCannotCarryAWeight() {
        val document = PrescriptionDocument(
            format = PRESCRIPTION_FORMAT,
            formatVersion = PRESCRIPTION_FORMAT_VERSION,
            appVersion = "t",
            exportedAtMillis = 1L,
            includesWeights = false,
            routines = listOf(
                PrescriptionRoutineRecord(
                    "Push",
                    workouts = listOf(
                        PrescriptionWorkoutRecord(
                            "Day 1",
                            entries = listOf(entry("back-squat").copy(weightKg = 80.0))
                        )
                    )
                )
            )
        )
        assertEquals(
            PrescriptionFailure.INVALID_VALUE,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(document)
            }.failure
        )
    }

    @Test
    fun rejectsOutOfBoundsSlotsDuplicateDefinitionsAndUnknownTokens() {
        fun document(
            entries: List<PrescriptionEntryRecord>,
            customExercises: List<PrescriptionExerciseRecord> = emptyList()
        ) = PrescriptionDocument(
            format = PRESCRIPTION_FORMAT,
            formatVersion = PRESCRIPTION_FORMAT_VERSION,
            appVersion = "t",
            exportedAtMillis = 1L,
            routines = listOf(
                PrescriptionRoutineRecord(
                    "Push",
                    workouts = listOf(PrescriptionWorkoutRecord("Day 1", entries = entries))
                )
            ),
            customExercises = customExercises
        )
        listOf(
            entry("back-squat").copy(sets = 0),
            entry("back-squat").copy(reps = 0),
            entry("back-squat").copy(loadKind = "NOPE"),
            entry(""),
            entry("back-squat").copy(exerciseName = "")
        ).forEach { invalid ->
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(document(listOf(invalid)))
            }
        }
        // A declared definition must be referenced, and a referenced custom must be declared.
        val base = PrescriptionExerciseRecord(
            id = "user-my-lift",
            name = "My Lift",
            requiredEquipment = listOf("DUMBBELL"),
            movementPattern = "CORE",
            isUnilateral = true,
            loadCapability = "BODYWEIGHT_ADDABLE",
            involvements = mapOf("ABS" to 0.5)
        )
        // Declared but unreferenced.
        assertEquals(
            PrescriptionFailure.INVALID_VALUE,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(document(listOf(entry("back-squat")), listOf(base)))
            }.failure
        )
        // Referenced but declared with an unknown muscle token.
        assertEquals(
            PrescriptionFailure.INVALID_VALUE,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(
                    document(
                        listOf(entry("user-my-lift")),
                        listOf(base.copy(involvements = mapOf("NOT_A_MUSCLE" to 0.5)))
                    )
                )
            }.failure
        )
        // Referenced, declared and valid.
        PrescriptionValidator.validate(
            document(listOf(entry("user-my-lift")), listOf(base))
        )
        assertEquals(
            PrescriptionFailure.DUPLICATE_ID,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(
                    document(listOf(entry("user-my-lift")), listOf(base, base))
                )
            }.failure
        )
    }

    @Test
    fun orderedTargetsRoundTripAndAPreVersionFourDocumentRejectsThem() = runBlocking {
        val ordered = SlotSetTargets.of(
            listOf(
                SetTarget(reps = 10, loadKind = LoadKind.EXTERNAL, weightKg = 60.0),
                SetTarget(reps = 8, loadKind = LoadKind.EXTERNAL, weightKg = 70.0, targetRir = 2)
            )
        )
        val orderedRoutine = routine.copy(
            workouts = listOf(
                RoutineWorkout(
                    position = 0,
                    name = "Day 1",
                    entries = listOf(
                        RoutineEntry(
                            position = 0,
                            exerciseId = "back-squat",
                            sets = 2,
                            reps = 10,
                            weightKg = 60.0,
                            loadKind = LoadKind.EXTERNAL,
                            setTargets = ordered
                        )
                    )
                )
            )
        )

        val document =
            ExportPrescriptionsUseCase(
                repositoryOf(orderedRoutine),
                catalog
            )(listOf(7L), true, "t", 1L)

        assertEquals(PRESCRIPTION_FORMAT_VERSION, document.formatVersion)
        val entry = document.routines.single().allEntries().single()
        assertEquals(ordered, SlotSetTargetsJson.decode(requireNotNull(entry.setTargets)))
        PrescriptionValidator.validate(document)
        assertEquals(document, PrescriptionJson.decode(PrescriptionJson.encode(document)))
        // A document that predates ordered targets is refused outright rather than adopting them.
        assertEquals(
            PrescriptionFailure.INVALID_VALUE,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(document.copy(formatVersion = 3))
            }.failure
        )
    }

    @Test
    fun supersetRoundTripsAndOldFormatOrBrokenPairIsRejected(): Unit = runBlocking {
        val pair = com.hydrafit.app.core.domain.routine.Superset("arms", 0, 120)
        val paired = routine.copy(
            workouts = listOf(
                RoutineWorkout(
                    name = "Day",
                    entries = listOf("barbell-curl", "dumbbell-curl").mapIndexed { index, id ->
                        RoutineEntry(
                            position = index,
                            exerciseId = id,
                            sets = 3,
                            reps = 10,
                            superset = pair
                        )
                    }
                )
            )
        )
        val document =
            ExportPrescriptionsUseCase(repositoryOf(paired), catalog)(listOf(7L), false, "t", 1)
        PrescriptionValidator.validate(document)
        assertEquals(document, PrescriptionJson.decode(PrescriptionJson.encode(document)))
        assertEquals(
            listOf(pair, pair),
            document.routines.single().allEntries().map {
                com.hydrafit.app.core.domain.routine.SupersetJson.decode(
                    requireNotNull(it.superset)
                )
            }
        )
        assertFailsWith<PrescriptionException> {
            PrescriptionValidator.validate(document.copy(formatVersion = 4))
        }
        val broken = document.copy(
            routines = document.routines.map { exported ->
                exported.copy(
                    workouts = exported.workouts.map {
                        it.copy(entries = it.entries.take(1))
                    }
                )
            }
        )
        assertFailsWith<PrescriptionException> { PrescriptionValidator.validate(broken) }
    }

    @Test
    fun clusterRoundTripsAtVersionEightAndAPreVersionEightDocumentRejectsIt(): Unit = runBlocking {
        val cluster = ClusterPrescription(
            kind = ClusterKind.DROP_SET,
            segments = listOf(
                ClusterSegmentTarget(reps = 3, weightKg = 100.0),
                ClusterSegmentTarget(reps = 3, weightKg = 80.0)
            )
        )
        val clusterRoutine = routine.copy(
            workouts = listOf(
                RoutineWorkout(
                    position = 0,
                    name = "Day",
                    entries = listOf(
                        RoutineEntry(
                            position = 0,
                            exerciseId = "back-squat",
                            sets = 1,
                            reps = 3,
                            weightKg = 100.0,
                            loadKind = LoadKind.EXTERNAL,
                            cluster = cluster
                        )
                    )
                )
            )
        )
        val document = ExportPrescriptionsUseCase(repositoryOf(clusterRoutine), catalog)(
            listOf(7L),
            true,
            "t",
            1L
        )

        assertEquals(8, PRESCRIPTION_FORMAT_VERSION)
        assertEquals(PRESCRIPTION_CLUSTER_FORMAT_VERSION, document.formatVersion)
        val record = document.routines.single().allEntries().single()
        assertEquals(cluster, ClusterPrescriptionJson.decode(requireNotNull(record.cluster)))
        PrescriptionValidator.validate(document)
        assertEquals(document, PrescriptionJson.decode(PrescriptionJson.encode(document)))

        // The import preview decodes the cluster onto the routine entry it builds.
        val preview = PreviewPrescriptionImport.build(
            document,
            PrescriptionImportChoices(),
            catalog.all(),
            setOf(EquipmentTag.BARBELL)
        )
        assertEquals(
            cluster,
            preview.routines.single().workouts.single().entries.single().cluster
        )

        // A document that predates clusters refuses the value instead of dropping it.
        assertEquals(
            PrescriptionFailure.INVALID_VALUE,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(
                    document.copy(formatVersion = PRESCRIPTION_CLUSTER_FORMAT_VERSION - 1)
                )
            }.failure
        )
    }

    /** A read-only repository over one template, for the export paths under test. */
    private fun repositoryOf(template: RoutineTemplate) = object : RoutineTemplateRepository {
        override fun observeAll(): Flow<List<RoutineTemplate>> = flowOf(listOf(template))

        override suspend fun get(id: Long): RoutineTemplate? = template.takeIf { it.id == id }

        override suspend fun save(template: RoutineTemplate): Long = 0L
        override suspend fun setArchived(id: Long, archivedAtMillis: Long?) = Unit
        override suspend fun isReferencedByActivation(id: Long): Boolean = false
        override suspend fun delete(id: Long) = Unit
    }

    @Test
    fun aPolicyInAPreVersionThreeDocumentIsRejectedRatherThanRead() = runBlocking {
        val policyRoutine = routine.copy(
            workouts = listOf(
                RoutineWorkout(
                    position = 0,
                    name = "Day 1",
                    entries = listOf(
                        RoutineEntry(
                            position = 0,
                            exerciseId = "back-squat",
                            sets = 3,
                            reps = 5,
                            weightKg = 100.0,
                            loadKind = LoadKind.EXTERNAL,
                            progressionPolicy = SlotProgressionPolicy(
                                minReps = 5,
                                maxReps = 8,
                                incrementKg = 2.5
                            )
                        )
                    )
                )
            )
        )
        val policyRepository = object : RoutineTemplateRepository {
            override fun observeAll(): Flow<List<RoutineTemplate>> = flowOf(listOf(policyRoutine))

            override suspend fun get(id: Long): RoutineTemplate? =
                policyRoutine.takeIf { it.id == id }

            override suspend fun save(template: RoutineTemplate): Long = 0L
            override suspend fun setArchived(id: Long, archivedAtMillis: Long?) = Unit
            override suspend fun isReferencedByActivation(id: Long): Boolean = false
            override suspend fun delete(id: Long) = Unit
        }

        val document =
            ExportPrescriptionsUseCase(policyRepository, catalog)(listOf(7L), true, "t", 1L)

        assertEquals(PRESCRIPTION_FORMAT_VERSION, document.formatVersion)
        assertEquals(
            2.5,
            document.routines.single().allEntries().single().progressionPolicy?.let {
                SlotProgressionPolicyJson.decode(it).incrementKg
            }
        )
        PrescriptionValidator.validate(document)
        // A document that predates the policy is refused outright rather than adopting one.
        assertEquals(
            PrescriptionFailure.INVALID_VALUE,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(document.copy(formatVersion = 2))
            }.failure
        )
    }

    @Test
    fun exportsATimedSlotAtTheCurrentVersionAndStillReadsRepOnlyVersionOneDocuments() =
        runBlocking {
            val timedRoutine = routine.copy(
                workouts = listOf(
                    RoutineWorkout(
                        position = 0,
                        name = "Day 1",
                        entries = listOf(
                            RoutineEntry(
                                position = 0,
                                exerciseId = "back-squat",
                                sets = 3,
                                reps = null,
                                durationSeconds = 90,
                                weightKg = 60.0,
                                loadKind = LoadKind.EXTERNAL
                            )
                        )
                    )
                )
            )
            val timedRepository = object : RoutineTemplateRepository {
                override fun observeAll(): Flow<List<RoutineTemplate>> =
                    flowOf(listOf(timedRoutine))

                override suspend fun get(id: Long): RoutineTemplate? =
                    timedRoutine.takeIf { it.id == id }

                override suspend fun save(template: RoutineTemplate): Long = 0L
                override suspend fun setArchived(id: Long, archivedAtMillis: Long?) = Unit
                override suspend fun isReferencedByActivation(id: Long): Boolean = false
                override suspend fun delete(id: Long) = Unit
            }

            val document =
                ExportPrescriptionsUseCase(timedRepository, catalog)(listOf(7L), true, "t", 1L)

            assertEquals(PRESCRIPTION_FORMAT_VERSION, document.formatVersion)
            val timed = document.routines.single().allEntries().single()
            assertNull(timed.reps)
            assertEquals(90, timed.durationSeconds)
            PrescriptionValidator.validate(document)
            // The seconds are part of the timed-slot JSON contract, not just the object graph.
            assertEquals(document, PrescriptionJson.decode(PrescriptionJson.encode(document)))

            // A version-1 document is rep-only: the same file read as version 1 is refused outright,
            // rather than reinterpreting its seconds as a rep count.
            assertEquals(
                PrescriptionFailure.INVALID_VALUE,
                assertFailsWith<PrescriptionException> {
                    PrescriptionValidator.validate(document.copy(formatVersion = 1))
                }.failure
            )
            val legacy = document.copy(
                formatVersion = 1,
                routines = document.routines.map { written ->
                    written.copy(
                        workouts = written.workouts.map { workout ->
                            workout.copy(
                                entries = workout.entries.map {
                                    it.copy(reps = 8, durationSeconds = null)
                                }
                            )
                        }
                    )
                }
            )
            assertEquals(1, legacy.formatVersion)
            PrescriptionValidator.validate(legacy)
        }

    @Test
    fun exportsADistanceSlotAtTheCurrentVersionAndRefusesItInAnEarlierOne() = runBlocking {
        val distanceRoutine = routine.copy(
            workouts = listOf(
                RoutineWorkout(
                    position = 0,
                    name = "Day 1",
                    entries = listOf(
                        RoutineEntry(
                            position = 0,
                            exerciseId = "back-squat",
                            sets = 1,
                            reps = null,
                            distanceMeters = 5_000,
                            weightKg = null,
                            loadKind = LoadKind.BODYWEIGHT
                        )
                    )
                )
            )
        )
        val distanceRepository = object : RoutineTemplateRepository {
            override fun observeAll(): Flow<List<RoutineTemplate>> = flowOf(listOf(distanceRoutine))

            override suspend fun get(id: Long): RoutineTemplate? =
                distanceRoutine.takeIf { it.id == id }

            override suspend fun save(template: RoutineTemplate): Long = 0L
            override suspend fun setArchived(id: Long, archivedAtMillis: Long?) = Unit
            override suspend fun isReferencedByActivation(id: Long): Boolean = false
            override suspend fun delete(id: Long) = Unit
        }

        val document =
            ExportPrescriptionsUseCase(distanceRepository, catalog)(listOf(7L), false, "t", 1L)

        assertEquals(PRESCRIPTION_FORMAT_VERSION, document.formatVersion)
        val slot = document.routines.single().allEntries().single()
        assertNull(slot.reps)
        assertNull(slot.durationSeconds)
        assertEquals(5_000, slot.distanceMeters)
        PrescriptionValidator.validate(document)
        // Metres survive the JSON contract rather than only the object graph.
        assertEquals(document, PrescriptionJson.decode(PrescriptionJson.encode(document)))

        // A document that predates distance refuses the metres instead of dropping them.
        assertEquals(
            PrescriptionFailure.INVALID_VALUE,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(
                    document.copy(formatVersion = PRESCRIPTION_DISTANCE_FORMAT_VERSION - 1)
                )
            }.failure
        )
    }

    @Test
    fun aNonRepsSlotCannotCarryOrderedTargets() {
        val targets = SlotSetTargetsJson.encode(
            SlotSetTargets.of(
                List(2) { SetTarget(reps = 8, loadKind = LoadKind.EXTERNAL, weightKg = 30.0) }
            )
        )
        val force = entry("back-squat").copy(
            reps = null,
            sets = 2,
            weightKg = null,
            forceNewtons = 150.0,
            setTargets = targets
        )
        val document = PrescriptionDocument(
            format = PRESCRIPTION_FORMAT,
            formatVersion = PRESCRIPTION_FORMAT_VERSION,
            appVersion = "t",
            exportedAtMillis = 1L,
            includesWeights = false,
            routines = listOf(
                PrescriptionRoutineRecord(
                    "Force",
                    workouts = listOf(
                        PrescriptionWorkoutRecord("Day", entries = listOf(force))
                    )
                )
            )
        )

        assertEquals(
            PrescriptionFailure.INVALID_VALUE,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(document)
            }.failure
        )
    }

    @Test
    fun forceRoundTripsAtVersionSevenAndIsRejectedByOlderFormatsOrWithReps() {
        val force = entry("back-squat").copy(
            reps = null,
            weightKg = null,
            forceNewtons = 150.0
        )
        fun document(version: Int, slot: PrescriptionEntryRecord) = PrescriptionDocument(
            format = PRESCRIPTION_FORMAT,
            formatVersion = version,
            appVersion = "t",
            exportedAtMillis = 1L,
            includesWeights = false,
            routines = listOf(
                PrescriptionRoutineRecord(
                    "Force",
                    workouts = listOf(PrescriptionWorkoutRecord("Day", entries = listOf(slot)))
                )
            )
        )
        val current = document(PRESCRIPTION_FORMAT_VERSION, force)
        PrescriptionValidator.validate(current)
        assertEquals(
            force,
            PrescriptionJson.decode(PrescriptionJson.encode(current))
                .routines.single().allEntries().single()
        )
        assertEquals(
            PrescriptionFailure.INVALID_VALUE,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(document(6, force))
            }.failure
        )
        assertEquals(
            PrescriptionFailure.INVALID_VALUE,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(document(7, force.copy(reps = 5)))
            }.failure
        )
    }

    @Test
    fun exportWritesForceIntoAWeightsFreeVersionSevenDocument() = runBlocking {
        val forceRoutine = routine.copy(
            workouts = listOf(
                RoutineWorkout(
                    position = 0,
                    name = "Force day",
                    entries = listOf(
                        RoutineEntry(
                            position = 0,
                            exerciseId = "back-squat",
                            sets = 2,
                            reps = null,
                            weightKg = null,
                            loadKind = LoadKind.EXTERNAL,
                            forceNewtons = 150.0
                        )
                    )
                )
            )
        )
        val exported = ExportPrescriptionsUseCase(
            repositoryOf(forceRoutine),
            catalog
        )(listOf(7L), false, "t", 1L)
        val record = exported.routines.single().allEntries().single()

        assertEquals(PRESCRIPTION_FORMAT_VERSION, exported.formatVersion)
        assertEquals(150.0, record.forceNewtons)
        assertNull(record.weightKg)
        PrescriptionValidator.validate(exported)
        assertEquals(exported, PrescriptionJson.decode(PrescriptionJson.encode(exported)))
    }

    @Test
    fun aCurrentDocumentRefusesASlotMeasuredByBothOrByNeither() {
        fun document(entry: PrescriptionEntryRecord) = PrescriptionDocument(
            format = PRESCRIPTION_FORMAT,
            formatVersion = PRESCRIPTION_FORMAT_VERSION,
            appVersion = "t",
            exportedAtMillis = 1L,
            routines = listOf(
                PrescriptionRoutineRecord(
                    "Push",
                    workouts = listOf(PrescriptionWorkoutRecord("Day 1", entries = listOf(entry)))
                )
            )
        )

        PrescriptionValidator.validate(
            document(entry("back-squat").copy(reps = null, durationSeconds = 90))
        )
        PrescriptionValidator.validate(
            document(entry("back-squat").copy(reps = null, distanceMeters = 5_000))
        )
        listOf(
            entry("back-squat").copy(reps = 8, durationSeconds = 90),
            entry("back-squat").copy(reps = 8, distanceMeters = 5_000),
            entry("back-squat").copy(durationSeconds = 90, distanceMeters = 5_000),
            entry("back-squat").copy(reps = null, durationSeconds = null),
            entry("back-squat").copy(reps = null, durationSeconds = 0),
            entry("back-squat").copy(reps = null, durationSeconds = -60),
            entry("back-squat").copy(reps = null, distanceMeters = 0),
            entry("back-squat").copy(reps = null, distanceMeters = -5_000),
            entry("back-squat").copy(reps = null, distanceMeters = 100_001)
        ).forEach { invalid ->
            assertEquals(
                PrescriptionFailure.INVALID_VALUE,
                assertFailsWith<PrescriptionException> {
                    PrescriptionValidator.validate(document(invalid))
                }.failure
            )
        }
    }

    private fun entry(exerciseId: String) = PrescriptionEntryRecord(
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        sets = 3,
        reps = 5
    )

    /** Builds a one-routine document that references [record]'s identity. */
    private fun documentWithDefinition(record: PrescriptionExerciseRecord): PrescriptionDocument =
        PrescriptionDocument(
            format = PRESCRIPTION_FORMAT,
            formatVersion = PRESCRIPTION_FORMAT_VERSION,
            appVersion = "t",
            exportedAtMillis = 1L,
            routines = listOf(
                PrescriptionRoutineRecord(
                    "Push",
                    workouts = listOf(
                        PrescriptionWorkoutRecord(
                            "Day 1",
                            entries = listOf(entry(record.id))
                        )
                    )
                )
            ),
            customExercises = listOf(record)
        )

    @Test
    fun aDefinitionWithALegacyMuscleAliasIsRejectedAndARealNamePasses() {
        val definition = PrescriptionExerciseRecord(
            id = "user-my-lift",
            name = "My Lift",
            requiredEquipment = listOf("DUMBBELL"),
            movementPattern = "CORE",
            isUnilateral = false,
            loadCapability = "BODYWEIGHT_ADDABLE"
        )
        // CHEST is a backup-path alias the importer cannot resolve with MuscleGroup.valueOf, so the
        // document form must reject it rather than let it reach the decoder.
        assertEquals(
            PrescriptionFailure.INVALID_VALUE,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(
                    documentWithDefinition(definition.copy(involvements = mapOf("CHEST" to 0.5)))
                )
            }.failure
        )
        // The same definition carrying the resolved enum name is accepted.
        PrescriptionValidator.validate(
            documentWithDefinition(definition.copy(involvements = mapOf("CHEST_UPPER" to 0.5)))
        )
    }

    @Test
    fun aDefinitionWithNoRequiredEquipmentIsValidAndABlankTokenIsNot() {
        val definition = PrescriptionExerciseRecord(
            id = "user-bodyweight-move",
            name = "Bodyweight Move",
            requiredEquipment = emptyList(),
            movementPattern = "CORE",
            isUnilateral = false,
            loadCapability = "BODYWEIGHT_ONLY"
        )
        // A local custom exercise may legitimately need no equipment at all.
        PrescriptionValidator.validate(documentWithDefinition(definition))
        assertEquals(
            PrescriptionFailure.INVALID_VALUE,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(
                    documentWithDefinition(definition.copy(requiredEquipment = listOf(" ")))
                )
            }.failure
        )
    }

    @Test
    fun anEquipmentLessCustomExerciseExportsAndRevalidates() = runBlocking {
        val equipmentLess = Exercise(
            id = "user-flow",
            name = "Flow",
            requiredEquipment = emptySet(),
            primaryMuscles = emptySet(),
            isCustom = true,
            loadCapability = ExerciseLoadCapability.BODYWEIGHT_ONLY
        )
        val localCatalog = object : ExerciseCatalog {
            override suspend fun all(): List<Exercise> = listOf(equipmentLess)
        }
        val mobility = RoutineTemplate(
            id = 3L,
            name = "Mobility",
            workouts = listOf(
                RoutineWorkout(
                    position = 0,
                    name = "Day 1",
                    entries = listOf(
                        RoutineEntry(
                            position = 0,
                            exerciseId = "user-flow",
                            sets = 2,
                            reps = 10,
                            loadKind = LoadKind.BODYWEIGHT
                        )
                    )
                )
            )
        )
        val localRepository = object : RoutineTemplateRepository {
            override fun observeAll(): Flow<List<RoutineTemplate>> = flowOf(listOf(mobility))
            override suspend fun get(id: Long): RoutineTemplate? = mobility.takeIf { it.id == id }
            override suspend fun save(template: RoutineTemplate): Long = 0L
            override suspend fun setArchived(id: Long, archivedAtMillis: Long?) = Unit
            override suspend fun isReferencedByActivation(id: Long): Boolean = false
            override suspend fun delete(id: Long) = Unit
        }

        val document =
            ExportPrescriptionsUseCase(localRepository, localCatalog)(listOf(3L), false, "t", 1L)

        // The empty equipment list survives export and the produced document re-validates.
        PrescriptionValidator.validate(document)
        assertEquals(emptyList<String>(), document.customExercises.single().requiredEquipment)
    }

    @Test
    fun exportRefusesWhenAChosenRoutineNoLongerExists() = runBlocking {
        val missing = object : RoutineTemplateRepository {
            override fun observeAll(): Flow<List<RoutineTemplate>> = flowOf(emptyList())
            override suspend fun get(id: Long): RoutineTemplate? = null
            override suspend fun save(template: RoutineTemplate): Long = 0L
            override suspend fun setArchived(id: Long, archivedAtMillis: Long?) = Unit
            override suspend fun isReferencedByActivation(id: Long): Boolean = false
            override suspend fun delete(id: Long) = Unit
        }
        // The routine was deleted before the write, so the export must not produce an unreadable file.
        assertEquals(
            PrescriptionFailure.INVALID_VALUE,
            assertFailsWith<PrescriptionException> {
                ExportPrescriptionsUseCase(missing, catalog)(listOf(7L), false, "t", 1L)
            }.failure
        )
    }
}

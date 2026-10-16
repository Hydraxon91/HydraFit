package com.hydrafit.app.core.domain.transfer

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.routine.RoutineEntry
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.RoutineTemplateRepository
import com.hydrafit.app.core.domain.routine.RoutineWorkout
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

/**
 * The printable prescription: what a page shows, that a weights-free document prints no load, and that
 * printing an on-screen plan reads no stored routine.
 */
class PrescriptionPdfLayoutTest {

    private fun entry(
        name: String = "Back Squat",
        sets: Int = 3,
        reps: Int = 5,
        weightKg: Double? = 100.0,
        loadKind: LoadKind = LoadKind.EXTERNAL
    ) = PrescriptionEntryRecord(
        exerciseId = name.lowercase().replace(' ', '-'),
        exerciseName = name,
        sets = sets,
        reps = reps,
        weightKg = weightKg,
        loadKind = loadKind.name
    )

    private fun document(
        routines: List<PrescriptionRoutineRecord>,
        includesWeights: Boolean = true
    ) = PrescriptionDocument(
        format = PRESCRIPTION_FORMAT,
        formatVersion = PRESCRIPTION_FORMAT_VERSION,
        appVersion = "t",
        exportedAtMillis = 1L,
        includesWeights = includesWeights,
        routines = routines
    )

    private fun routine(vararg entries: PrescriptionEntryRecord, focus: SplitFocus? = null) =
        PrescriptionRoutineRecord(
            name = "Shared plan",
            workouts = listOf(
                PrescriptionWorkoutRecord("Day 1", focus = focus?.name, entries = entries.toList())
            )
        )

    private fun texts(pages: List<PrescriptionPage>): List<String> =
        pages.flatMap { page -> page.lines.map { it.text } }

    @Test
    fun printsTheTitleRoutineWorkoutAndSlotLinesInOrder() {
        val pages = PrescriptionPdfLayout.build(
            document(listOf(routine(entry(), entry(name = "Bench Press", sets = 4, reps = 8)))),
            linesPerPage = 100
        )
        assertEquals(1, pages.size)
        assertEquals(
            listOf(
                "Workout plan (1 routine)",
                "Shared plan",
                "Day 1",
                "  Back Squat: 3 × 5 @ 100 kg",
                "  Bench Press: 4 × 8 @ 100 kg"
            ),
            texts(pages)
        )
        assertEquals(PrescriptionLineRole.TITLE, pages.single().lines.first().role)
        assertEquals(PrescriptionLineRole.HEADING, pages.single().lines[1].role)
        assertTrue(pages.single().lines.drop(2).all { it.role == PrescriptionLineRole.BODY })
    }

    @Test
    fun namesTheRoutineCountAndPrintsTheWorkoutFocus() {
        val pages = PrescriptionPdfLayout.build(
            document(
                listOf(
                    routine(entry(), focus = SplitFocus.PUSH),
                    PrescriptionRoutineRecord(
                        "Second plan",
                        workouts = listOf(
                            PrescriptionWorkoutRecord("Day 1", entries = listOf(entry()))
                        )
                    )
                )
            ),
            linesPerPage = 100
        )
        val lines = texts(pages)
        assertEquals("Workout plan (2 routines)", lines.first())
        assertTrue("Day 1 — PUSH" in lines, lines.toString())
        assertTrue("Second plan" in lines)
    }

    @Test
    fun aWeightsFreeDocumentPrintsTheNoteAndNoLoad() {
        val pages = PrescriptionPdfLayout.build(
            document(listOf(routine(entry(weightKg = null))), includesWeights = false),
            linesPerPage = 100
        )
        val lines = texts(pages)
        assertTrue(
            "Prescribed weights are not included in this document." in lines,
            lines.toString()
        )
        assertTrue("  Back Squat: 3 × 5" in lines, lines.toString())
        assertFalse(lines.any { it.contains("kg") }, lines.toString())

        // The note is absent when weights are included.
        assertFalse(
            texts(
                PrescriptionPdfLayout.build(document(listOf(routine(entry()))), linesPerPage = 100)
            ).any { it.startsWith("Prescribed weights") }
        )
    }

    @Test
    fun printsLoadKindsAndTrimsWholeNumbers() {
        val pages = PrescriptionPdfLayout.build(
            document(
                listOf(
                    routine(
                        entry(name = "Pull-up", weightKg = null, loadKind = LoadKind.BODYWEIGHT),
                        entry(name = "Dip", weightKg = 2.5, loadKind = LoadKind.ADDED),
                        entry(name = "Plank", weightKg = 0.0, loadKind = LoadKind.ADDED)
                    )
                )
            ),
            linesPerPage = 100
        )
        val lines = texts(pages)
        assertTrue("  Pull-up: 3 × 5 bodyweight" in lines, lines.toString())
        assertTrue("  Dip: 3 × 5 @ 2.5 kg added" in lines, lines.toString())
        // A recorded zero is printed as a number, not mistaken for an absent load.
        assertTrue("  Plank: 3 × 5 @ 0 kg added" in lines, lines.toString())
    }

    @Test
    fun aWrapperSplitsLongLinesWithoutDroppingContentAndPagesCountWrappedLines() {
        val routine = routine(entry(name = "A very long exercise name that would not fit"))
        val document = document(listOf(routine))

        // Wrap each logical line into fixed 10-character pieces, as a narrow page would.
        val wrapper = PrescriptionLineWrapper { text -> text.chunked(10) }
        val pages = PrescriptionPdfLayout.build(document, linesPerPage = 3, wrapper = wrapper)
        val wrapped = texts(pages)

        assertTrue(wrapped.all { it.length <= 10 }, wrapped.toString())
        assertEquals(3, pages.first().lines.size)
        // Nothing is dropped or reordered: the wrapped pages still carry the whole document's text.
        assertEquals(
            texts(PrescriptionPdfLayout.build(document, linesPerPage = 100)).joinToString(""),
            wrapped.joinToString("")
        )
        // Every logical line keeps its role on each wrapped piece.
        assertTrue(pages.first().lines.all { it.role == PrescriptionLineRole.TITLE })

        // A wrapper that drops a line is refused rather than silently truncating the document.
        assertFailsWith<IllegalArgumentException> {
            PrescriptionPdfLayout.build(document, linesPerPage = 3, wrapper = { emptyList() })
        }
    }

    @Test
    fun splitsLinesIntoPagesAtTheGivenBoundary() {
        val routine = routine(
            entry(name = "A"),
            entry(name = "B"),
            entry(name = "C")
        )
        // Title + heading + workout + 3 slots = 6 lines.
        val pages = PrescriptionPdfLayout.build(document(listOf(routine)), linesPerPage = 4)
        assertEquals(listOf(4, 2), pages.map { it.lines.size })
        assertEquals(6, pages.sumOf { it.lines.size })
        // A boundary that divides evenly does not append an empty page.
        assertEquals(
            listOf(3, 3),
            PrescriptionPdfLayout.build(
                document(listOf(routine)),
                linesPerPage = 3
            ).map { it.lines.size }
        )
        assertFailsWith<IllegalArgumentException> {
            PrescriptionPdfLayout.build(document(listOf(routine)), linesPerPage = 0)
        }
    }

    @Test
    fun printingTheOnScreenPlanReadsNoStoredRoutine() = runBlocking {
        val repository = object : RoutineTemplateRepository {
            override fun observeAll(): Flow<List<RoutineTemplate>> = flowOf(emptyList())
            override suspend fun get(id: Long): RoutineTemplate =
                error("printing an on-screen plan must not read a stored routine")
            override suspend fun save(template: RoutineTemplate): Long =
                error("printing must not write")
            override suspend fun setArchived(id: Long, archivedAtMillis: Long?) =
                error("printing must not write")
            override suspend fun isReferencedByActivation(id: Long): Boolean =
                error("printing must not read an activation")
            override suspend fun delete(id: Long) = error("printing must not write")
        }
        val catalog = object : ExerciseCatalog {
            override suspend fun all(): List<Exercise> = emptyList()
        }
        val draft = RoutineTemplate(
            name = "On screen",
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
                            loadKind = LoadKind.EXTERNAL
                        )
                    )
                )
            )
        )

        val pages = ExportPrescriptionPdfUseCase(repository, catalog).pages(
            templates = listOf(draft),
            includeWeights = false,
            appVersion = "t",
            exportedAtMillis = 1L,
            linesPerPage = 100
        )

        val lines = texts(pages)
        assertEquals("Workout plan (1 routine)", lines.first())
        // A deleted custom identity keeps its stored id as the only available name.
        assertTrue("  back-squat: 3 × 5" in lines, lines.toString())
        assertFalse(lines.any { it.contains("kg") }, lines.toString())
    }
}

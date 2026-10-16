package com.hydrafit.app.core.domain.transfer

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.RoutineTemplateRepository

/** One line of a printable prescription, with the role that decides its type style. */
data class PrescriptionLine(val text: String, val role: PrescriptionLineRole)

enum class PrescriptionLineRole {
    TITLE,
    HEADING,
    BODY
}

/** One paginated page of lines. The renderer decides pixels; layout only decides what sits where. */
data class PrescriptionPage(val lines: List<PrescriptionLine>)

/**
 * Builds the printable lines of a prescription and splits them into pages.
 *
 * Pagination is pure and host-testable: the renderer measures nothing, and the caller supplies the
 * lines per page that its chosen font and paper size can hold. A single line longer than the page's
 * width is passed through unchanged for the renderer to clip, never silently truncated here.
 */
object PrescriptionPdfLayout {
    fun build(document: PrescriptionDocument, linesPerPage: Int): List<PrescriptionPage> {
        require(linesPerPage > 0) { "linesPerPage must be positive" }
        val lines = mutableListOf<PrescriptionLine>()
        val count = document.routines.size
        lines += PrescriptionLine(
            "Workout plan ($count routine${if (count == 1) "" else "s"})",
            PrescriptionLineRole.TITLE
        )
        if (!document.includesWeights) {
            lines += PrescriptionLine(
                "Prescribed weights are not included in this document.",
                PrescriptionLineRole.BODY
            )
        }
        document.routines.forEach { routine ->
            lines += PrescriptionLine(routine.name, PrescriptionLineRole.HEADING)
            routine.workouts.forEach { workout ->
                val focus = workout.focus?.let { " — $it" }.orEmpty()
                lines += PrescriptionLine(workout.name + focus, PrescriptionLineRole.BODY)
                workout.entries.forEach { entry ->
                    lines +=
                        PrescriptionLine(
                            entry.describe(document.includesWeights),
                            PrescriptionLineRole.BODY
                        )
                }
            }
        }
        return lines.chunked(linesPerPage).map(::PrescriptionPage)
    }
}

/** The printed form of one slot; a weights-free document prints no load at all. */
private fun PrescriptionEntryRecord.describe(includesWeights: Boolean): String {
    val load = if (includesWeights) weightKg?.let { " @ ${trimNumber(it)} kg" }.orEmpty() else ""
    val kind = when (loadKind) {
        "BODYWEIGHT" -> " bodyweight"
        "ADDED" -> " added"
        else -> ""
    }
    return "  $exerciseName: $sets × $reps$load$kind"
}

private fun trimNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/**
 * Reads the chosen routines into a printable prescription and paginates it, without touching a file
 * or a platform. The adapter draws the returned pages; weights follow the same explicit choice as the
 * interchange document, so a plan PDF cannot disclose a prescribed weight the user chose to omit.
 */
class ExportPrescriptionPdfUseCase(
    private val routines: RoutineTemplateRepository,
    private val catalog: ExerciseCatalog
) {
    suspend operator fun invoke(
        routineIds: List<Long>,
        includeWeights: Boolean,
        appVersion: String,
        exportedAtMillis: Long,
        linesPerPage: Int
    ): List<PrescriptionPage> = page(
        ExportPrescriptionsUseCase(routines, catalog)(
            routineIds,
            includeWeights,
            appVersion,
            exportedAtMillis
        ),
        linesPerPage
    )

    /** Prints an in-memory routine (the plan on screen) without persisting anything. */
    suspend fun pages(
        templates: List<RoutineTemplate>,
        includeWeights: Boolean,
        appVersion: String,
        exportedAtMillis: Long,
        linesPerPage: Int
    ): List<PrescriptionPage> = page(
        ExportPrescriptionsUseCase(routines, catalog).document(
            templates,
            includeWeights,
            appVersion,
            exportedAtMillis
        ),
        linesPerPage
    )

    private fun page(document: PrescriptionDocument, linesPerPage: Int): List<PrescriptionPage> =
        PrescriptionPdfLayout.build(document, linesPerPage)
}

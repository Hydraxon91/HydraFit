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
 * Splits one logical line into the printable lines that fit the page's text width **at the size that
 * line is actually drawn**, which is why the role travels with the text. The renderer supplies this,
 * because only it knows the real font metrics; the domain decides what text exists and in what order.
 * A conforming implementation never drops content: a token wider than the page is hard-broken rather
 * than clipped, and an embedded newline becomes a break.
 */
fun interface PrescriptionLineWrapper {
    fun wrap(text: String, role: PrescriptionLineRole): List<String>
}

/**
 * Builds the printable lines of a prescription and splits them into pages.
 *
 * Pagination is pure and host-testable: [wrapper] is injected, so a test can drive the wrapping
 * deterministically while the Android renderer supplies real font measurement. Every logical line is
 * wrapped to the page width *before* pagination, so pages hold the lines that are actually drawn and
 * a long exercise name can never clip its sets, reps or weight.
 */
object PrescriptionPdfLayout {
    fun build(
        document: PrescriptionDocument,
        linesPerPage: Int,
        wrapper: PrescriptionLineWrapper = PrescriptionLineWrapper { text, _ -> listOf(text) }
    ): List<PrescriptionPage> {
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
        val wrapped = lines.flatMap { line ->
            val parts = wrapper.wrap(line.text, line.role)
            require(parts.isNotEmpty()) { "a line wrapper must not drop a line" }
            parts.map { PrescriptionLine(it, line.role) }
        }
        return wrapped.chunked(linesPerPage).map(::PrescriptionPage)
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
    // A timed slot prints its seconds, never its (absent) rep count.
    val metric = durationSeconds?.let { "$it s" } ?: reps?.toString().orEmpty()
    return "  $exerciseName: $sets × $metric$load$kind"
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
        linesPerPage: Int,
        wrapper: PrescriptionLineWrapper = PrescriptionLineWrapper { text, _ -> listOf(text) }
    ): List<PrescriptionPage> = page(
        ExportPrescriptionsUseCase(routines, catalog)(
            routineIds,
            includeWeights,
            appVersion,
            exportedAtMillis
        ),
        linesPerPage,
        wrapper
    )

    /** Prints an in-memory routine (the plan on screen) without persisting anything. */
    suspend fun pages(
        templates: List<RoutineTemplate>,
        includeWeights: Boolean,
        appVersion: String,
        exportedAtMillis: Long,
        linesPerPage: Int,
        wrapper: PrescriptionLineWrapper = PrescriptionLineWrapper { text, _ -> listOf(text) }
    ): List<PrescriptionPage> = page(
        ExportPrescriptionsUseCase(routines, catalog).document(
            templates,
            includeWeights,
            appVersion,
            exportedAtMillis
        ),
        linesPerPage,
        wrapper
    )

    private fun page(
        document: PrescriptionDocument,
        linesPerPage: Int,
        wrapper: PrescriptionLineWrapper
    ): List<PrescriptionPage> = PrescriptionPdfLayout.build(document, linesPerPage, wrapper)
}

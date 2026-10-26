package com.hydrafit.app.core.domain.transfer

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.time.civilFromDays
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSessionRepository

/** One recorded set, projected for the analysis export. [weightKg] stays null for no recorded load. */
data class WorkoutHistoryCsvRow(
    val sessionId: String?,
    val sessionStartedAtMillis: Long?,
    val localEpochDay: Long?,
    val setId: Long,
    val performedAtMillis: Long,
    val exerciseId: String,
    val exerciseName: String,
    /** Repetitions for counted work, blank in the export for a timed set. */
    val reps: Int?,
    /** Elapsed whole seconds for a timed set, blank for counted or distance work. */
    val durationSeconds: Int? = null,
    /** Whole metres for a distance set, blank for counted or timed work. */
    val distanceMeters: Int? = null,
    val weightKg: Double?,
    val loadKind: String,
    val isWarmup: Boolean,
    val rir: Int?,
    val timingProvenance: String,
    val forceNewtons: Double? = null,
    val deviceName: String? = null,
    val deviceRatingKg: Double? = null
)

/**
 * Writes recorded history as CSV for analysis in another tool.
 *
 * This is an analysis export, not a backup and not a promised re-import format: it carries the
 * canonical recorded fields with explicit units (`weightKg` is kilograms) and UTC instants, one row
 * per recorded set, and it deliberately excludes personal notes, credentials, equipment state and
 * derived explanations, which have no column. Blank and zero stay distinct (an absent load is an
 * empty field, a recorded `0` is `0.0`). Columns that can carry stored or user-authored text are
 * quoted per RFC 4180 and neutralized when they could be read as a spreadsheet formula; the columns
 * this app generates as numbers or booleans are written plainly, so a negative value stays a number.
 */
object WorkoutHistoryCsvWriter {

    val COLUMNS: List<String> = listOf(
        "sessionId",
        "sessionStartedAtUtc",
        "localEpochDay",
        "setId",
        "performedAtUtc",
        "exerciseId",
        "exerciseName",
        "reps",
        "durationSeconds",
        "distanceMeters",
        "weightKg",
        "loadKind",
        "isWarmup",
        "rir",
        "timingProvenance",
        "forceNewtons",
        "deviceName",
        "deviceRatingKg"
    )

    fun write(rows: List<WorkoutHistoryCsvRow>): String = buildString {
        append(COLUMNS.joinToString(","))
        append("\r\n")
        rows.sortedWith(
            compareBy({ it.performedAtMillis }, { it.setId })
        ).forEach { row ->
            append(
                listOf(
                    textField(row.sessionId),
                    textField(row.sessionStartedAtMillis?.let(::utcInstant)),
                    plainField(row.localEpochDay?.toString()),
                    plainField(row.setId.toString()),
                    textField(utcInstant(row.performedAtMillis)),
                    textField(row.exerciseId),
                    textField(row.exerciseName),
                    plainField(row.reps?.toString()),
                    plainField(row.durationSeconds?.toString()),
                    plainField(row.distanceMeters?.toString()),
                    plainField(row.weightKg?.toString()),
                    textField(row.loadKind),
                    plainField(if (row.isWarmup) "true" else "false"),
                    plainField(row.rir?.toString()),
                    textField(row.timingProvenance),
                    plainField(row.forceNewtons?.toString()),
                    textField(row.deviceName),
                    plainField(row.deviceRatingKg?.toString())
                ).joinToString(",")
            )
            append("\r\n")
        }
    }

    /**
     * An app-generated numeric or boolean column, written plainly: a negative value (a recorded
     * `rir` of -1) stays a number, and this app authors the value, so none of it is user text.
     */
    private fun plainField(value: String?): String = value ?: ""

    /**
     * A column that can carry stored or user-authored text. A leading =, +, - or @ is the
     * spreadsheet-formula trigger; prefixing an apostrophe makes the cell text without changing what
     * the user sees as the value's content, and RFC 4180 quoting protects an embedded delimiter.
     */
    private fun textField(value: String?): String {
        if (value == null) return ""
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val neutralized = if (value.firstOrNull() in FORMULA_TRIGGERS) "'$value" else value
        return if (needsQuoting) "\"" + neutralized.replace("\"", "\"\"") + "\"" else neutralized
    }

    private val FORMULA_TRIGGERS = setOf('=', '+', '-', '@', '\t')

    /** `YYYY-MM-DDTHH:mm:ssZ` in UTC; the export never applies a local offset. */
    private fun utcInstant(epochMillis: Long): String {
        val day = localEpochDay(epochMillis, 0L)
        // localEpochDay floors, so the remainder is always within one day.
        val secondOfDay = (epochMillis - day * MILLIS_PER_DAY) / 1000L
        val (year, month, dayOfMonth) = civilFromDays(day)
        val hour = (secondOfDay / 3600L).toInt()
        val minute = ((secondOfDay / 60L) % 60L).toInt()
        val second = (secondOfDay % 60L).toInt()
        return "$year-" + pad(month) + "-" + pad(dayOfMonth) + "T" + pad(hour) + ":" + pad(minute) +
            ":" + pad(second) + "Z"
    }

    private fun pad(value: Int): String = value.toString().padStart(2, '0')

    private const val MILLIS_PER_DAY = 86_400_000L
}

/**
 * Reads all recorded sets with their session and effective exercise name. A user-initiated export
 * reads the whole history once; it never writes. An unassigned or missing session leaves the session
 * columns empty without dropping the set.
 */
class ExportWorkoutHistoryCsvUseCase(
    private val logs: WorkoutLogRepository,
    private val sessions: WorkoutSessionRepository,
    private val catalog: ExerciseCatalog
) {
    suspend operator fun invoke(): String {
        val names = catalog.all().associate { it.id to it.name }
        val byId = sessions.all().associateBy { it.id }
        val rows = logs.all().map { set ->
            val session = set.sessionId?.let { byId[it] }
            WorkoutHistoryCsvRow(
                sessionId = set.sessionId,
                sessionStartedAtMillis = session?.startedAtMillis,
                localEpochDay = session?.localEpochDay,
                setId = set.id,
                performedAtMillis = set.performedAtMillis,
                exerciseId = set.exerciseId,
                exerciseName = names[set.exerciseId] ?: set.exerciseId,
                reps = set.reps,
                durationSeconds = set.durationSeconds,
                distanceMeters = set.distanceMeters,
                weightKg = set.weightKg,
                loadKind = set.loadKind.name,
                isWarmup = set.isWarmup,
                rir = set.rir,
                timingProvenance = set.timingProvenance.name,
                forceNewtons = set.forceNewtons,
                deviceName = set.device?.name,
                deviceRatingKg = set.device?.ratingKg
            )
        }
        return WorkoutHistoryCsvWriter.write(rows)
    }
}

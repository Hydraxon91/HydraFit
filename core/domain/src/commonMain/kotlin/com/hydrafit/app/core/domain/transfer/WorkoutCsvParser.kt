package com.hydrafit.app.core.domain.transfer

import com.hydrafit.app.core.domain.time.civilFromDays
import com.hydrafit.app.core.domain.time.daysFromCivil
import com.hydrafit.app.core.domain.transfer.WorkoutCsvDiagnosticCode.INVALID_DATE
import com.hydrafit.app.core.domain.transfer.WorkoutCsvDiagnosticCode.INVALID_END_TIME
import com.hydrafit.app.core.domain.transfer.WorkoutCsvDiagnosticCode.INVALID_REPS
import com.hydrafit.app.core.domain.transfer.WorkoutCsvDiagnosticCode.INVALID_TIME
import com.hydrafit.app.core.domain.transfer.WorkoutCsvDiagnosticCode.INVALID_UNIT
import com.hydrafit.app.core.domain.transfer.WorkoutCsvDiagnosticCode.INVALID_WEIGHT
import com.hydrafit.app.core.domain.transfer.WorkoutCsvDiagnosticCode.MISSING_EXERCISE_NAME
import com.hydrafit.app.core.domain.transfer.WorkoutCsvDiagnosticCode.UNSUPPORTED_METADATA
import com.hydrafit.app.core.domain.transfer.WorkoutCsvDiagnosticCode.UNSUPPORTED_ROW_SHAPE
import com.hydrafit.app.core.domain.transfer.WorkoutCsvDiagnosticCode.UNSUPPORTED_SET_TYPE
import com.hydrafit.app.core.domain.unit.WeightUnit

/**
 * Parses a bounded provider export into ordered rows. Behaviour is deliberately conservative: the
 * caller names the provider rather than the parser guessing a layout, unknown headers and columns
 * are rejected, and a value that cannot be represented as a weighted rep set becomes a row
 * diagnostic instead of fabricated data. Timezone and session mapping are out of scope here; the
 * source-local civil date and optional time are preserved verbatim for the caller to map.
 */
object WorkoutCsvParser {

    fun parse(source: WorkoutCsvSource, text: String): ParsedWorkoutCsv {
        if (text.length.toLong() > WorkoutCsvLimits.MAX_UTF8_BYTES ||
            text.encodeToByteArray().size.toLong() > WorkoutCsvLimits.MAX_UTF8_BYTES
        ) {
            throw WorkoutCsvException(WorkoutCsvFailure.TOO_LARGE)
        }
        val delimiter = when (source) {
            WorkoutCsvSource.FITNOTES, WorkoutCsvSource.HEVY -> ','
            WorkoutCsvSource.STRONG -> ';'
        }
        val table = CsvTableReader.read(text, delimiter)
        if (table.records.size <= 1) {
            throw WorkoutCsvException(WorkoutCsvFailure.EMPTY_INPUT)
        }
        val header = table.records.first().values
        val dataRecords = table.records.drop(1)
        return when (source) {
            WorkoutCsvSource.FITNOTES -> parseFitNotes(header, dataRecords)
            WorkoutCsvSource.STRONG -> parseStrong(header, dataRecords)
            WorkoutCsvSource.HEVY -> parseHevy(header, dataRecords)
        }
    }
}

// --- FitNotes -----------------------------------------------------------------------------------

private const val FITNOTES_DATE = "Date"
private const val FITNOTES_EXERCISE = "Exercise"
private const val FITNOTES_CATEGORY = "Category"
private const val FITNOTES_WEIGHT_LBS = "Weight (lbs)"
private const val FITNOTES_WEIGHT_KG = "Weight (kg)"
private const val FITNOTES_REPS = "Reps"
private const val FITNOTES_DISTANCE = "Distance"
private const val FITNOTES_DISTANCE_UNIT = "Distance Unit"
private const val FITNOTES_TIME = "Time"

private val FITNOTES_OPTIONAL_COLUMNS = setOf(
    FITNOTES_CATEGORY,
    FITNOTES_DISTANCE,
    FITNOTES_DISTANCE_UNIT,
    FITNOTES_TIME
)

private val FITNOTES_METADATA_COLUMNS = listOf(
    FITNOTES_DISTANCE,
    FITNOTES_DISTANCE_UNIT,
    FITNOTES_TIME
)

private fun parseFitNotes(header: List<String>, records: List<CsvRecord>): ParsedWorkoutCsv {
    val hasLbs = FITNOTES_WEIGHT_LBS in header
    val hasKg = FITNOTES_WEIGHT_KG in header
    if (hasLbs == hasKg) {
        throw WorkoutCsvException(
            WorkoutCsvFailure.UNSUPPORTED_HEADER,
            "FitNotes export must carry exactly one Weight (lbs) or Weight (kg) column"
        )
    }
    val weightColumn = if (hasKg) FITNOTES_WEIGHT_KG else FITNOTES_WEIGHT_LBS
    val unit = if (hasKg) WeightUnit.KG else WeightUnit.LB
    validateHeader(
        header = header,
        required = setOf(FITNOTES_DATE, FITNOTES_EXERCISE, FITNOTES_REPS, weightColumn),
        optional = FITNOTES_OPTIONAL_COLUMNS,
        source = WorkoutCsvSource.FITNOTES
    )
    val index = HeaderIndex(header)
    val rows = ArrayList<WorkoutCsvRow>()
    val rejected = ArrayList<RejectedWorkoutCsvRow>()
    for (record in records) {
        requireWidth(record, header)
        val exerciseText = index.value(record, FITNOTES_EXERCISE)
        val dateText = index.value(record, FITNOTES_DATE)
        val weightText = index.value(record, weightColumn)
        val exercise = exerciseText.trim()
        val day = parseIsoDate(dateText)
        val reps = parsePositiveInt(index.value(record, FITNOTES_REPS))
        val weight = parseWeight(weightText)
        val diagnostics = RowDiagnostics()
        if (exercise.isEmpty()) {
            diagnostics.error(diagnostic(MISSING_EXERCISE_NAME, FITNOTES_EXERCISE, exerciseText))
        }
        if (day == null) diagnostics.error(diagnostic(INVALID_DATE, FITNOTES_DATE, dateText))
        if (reps == null) {
            diagnostics.error(
                repsDiagnostic(index, record, FITNOTES_REPS, listOf(FITNOTES_DISTANCE))
            )
        }
        if (weight is WeightValue.Invalid) {
            diagnostics.error(diagnostic(INVALID_WEIGHT, weightColumn, weightText))
        }
        for (column in FITNOTES_METADATA_COLUMNS) {
            addMetadataNote(index, record, column, diagnostics)
        }
        if (diagnostics.hasError) {
            rejected += RejectedWorkoutCsvRow(record.sourceRowIndex, diagnostics.findings)
        } else {
            rows += WorkoutCsvRow(
                sourceRowIndex = record.sourceRowIndex,
                exerciseName = exerciseText,
                day = checkNotNull(day),
                timeOfDaySeconds = null,
                endDay = null,
                endTimeOfDaySeconds = null,
                workoutTitle = null,
                groupHint = index.value(record, FITNOTES_CATEGORY).trim().ifBlank { null },
                weight = (weight as? WeightValue.Measured)?.value,
                unit = unit,
                reps = checkNotNull(reps),
                warmup = null,
                diagnostics = diagnostics.findings
            )
        }
    }
    return ParsedWorkoutCsv(WorkoutCsvSource.FITNOTES, header, rows, rejected)
}

// --- Strong -------------------------------------------------------------------------------------

private const val STRONG_DATE = "Date"
private const val STRONG_WORKOUT = "Workout Name"
private const val STRONG_EXERCISE = "Exercise Name"
private const val STRONG_SET_ORDER = "Set Order"
private const val STRONG_WEIGHT = "Weight"
private const val STRONG_WEIGHT_UNIT = "Weight Unit"
private const val STRONG_REPS = "Reps"
private const val STRONG_RPE = "RPE"
private const val STRONG_DISTANCE = "Distance"
private const val STRONG_DISTANCE_UNIT = "Distance Unit"
private const val STRONG_SECONDS = "Seconds"
private const val STRONG_NOTES = "Notes"
private const val STRONG_WORKOUT_NOTES = "Workout Notes"
private const val STRONG_WORKOUT_DURATION = "Workout Duration"

private val STRONG_OPTIONAL_COLUMNS = setOf(
    STRONG_SET_ORDER,
    STRONG_RPE,
    STRONG_DISTANCE,
    STRONG_DISTANCE_UNIT,
    STRONG_SECONDS,
    STRONG_NOTES,
    STRONG_WORKOUT_NOTES,
    STRONG_WORKOUT_DURATION
)

private val STRONG_METADATA_COLUMNS = listOf(
    STRONG_RPE,
    STRONG_DISTANCE,
    STRONG_DISTANCE_UNIT,
    STRONG_SECONDS,
    STRONG_NOTES,
    STRONG_WORKOUT_NOTES,
    STRONG_WORKOUT_DURATION
)

private fun parseStrong(header: List<String>, records: List<CsvRecord>): ParsedWorkoutCsv {
    validateHeader(
        header = header,
        required = setOf(
            STRONG_DATE,
            STRONG_WORKOUT,
            STRONG_EXERCISE,
            STRONG_WEIGHT,
            STRONG_WEIGHT_UNIT,
            STRONG_REPS
        ),
        optional = STRONG_OPTIONAL_COLUMNS,
        source = WorkoutCsvSource.STRONG
    )
    val index = HeaderIndex(header)
    val rows = ArrayList<WorkoutCsvRow>()
    val rejected = ArrayList<RejectedWorkoutCsvRow>()
    for (record in records) {
        requireWidth(record, header)
        val exerciseText = index.value(record, STRONG_EXERCISE)
        val dateText = index.value(record, STRONG_DATE)
        val unitText = index.value(record, STRONG_WEIGHT_UNIT)
        val exercise = exerciseText.trim()
        val day = strongDay(dateText)
        val seconds = strongTimeSeconds(dateText)
        val reps = parsePositiveInt(index.value(record, STRONG_REPS))
        val weightText = index.value(record, STRONG_WEIGHT)
        val weight = parseWeight(weightText)
        val unit = parseWeightUnit(unitText)
        val diagnostics = RowDiagnostics()
        if (exercise.isEmpty()) {
            diagnostics.error(diagnostic(MISSING_EXERCISE_NAME, STRONG_EXERCISE, exerciseText))
        }
        if (day == null) diagnostics.error(diagnostic(INVALID_DATE, STRONG_DATE, dateText))
        if (seconds == null) diagnostics.error(diagnostic(INVALID_TIME, STRONG_DATE, dateText))
        if (reps == null) {
            diagnostics.error(
                repsDiagnostic(index, record, STRONG_REPS, listOf(STRONG_DISTANCE, STRONG_SECONDS))
            )
        }
        if (weight is WeightValue.Invalid) {
            diagnostics.error(diagnostic(INVALID_WEIGHT, STRONG_WEIGHT, weightText))
        }
        if (unit == null) diagnostics.error(diagnostic(INVALID_UNIT, STRONG_WEIGHT_UNIT, unitText))
        for (column in STRONG_METADATA_COLUMNS) {
            addMetadataNote(index, record, column, diagnostics)
        }
        if (diagnostics.hasError) {
            rejected += RejectedWorkoutCsvRow(record.sourceRowIndex, diagnostics.findings)
        } else {
            rows += WorkoutCsvRow(
                sourceRowIndex = record.sourceRowIndex,
                exerciseName = exerciseText,
                day = checkNotNull(day),
                timeOfDaySeconds = seconds,
                endDay = null,
                endTimeOfDaySeconds = null,
                workoutTitle = index.value(record, STRONG_WORKOUT).trim().ifBlank { null },
                groupHint = null,
                weight = (weight as? WeightValue.Measured)?.value,
                unit = checkNotNull(unit),
                reps = checkNotNull(reps),
                warmup = null,
                diagnostics = diagnostics.findings
            )
        }
    }
    return ParsedWorkoutCsv(WorkoutCsvSource.STRONG, header, rows, rejected)
}

// --- Hevy ---------------------------------------------------------------------------------------

private const val HEVY_TITLE = "title"
private const val HEVY_START = "start_time"
private const val HEVY_END = "end_time"
private const val HEVY_DESCRIPTION = "description"
private const val HEVY_EXERCISE = "exercise_title"
private const val HEVY_SUPERSET = "superset_id"
private const val HEVY_EXERCISE_NOTES = "exercise_notes"
private const val HEVY_SET_INDEX = "set_index"
private const val HEVY_SET_TYPE = "set_type"
private const val HEVY_WEIGHT_LBS = "weight_lbs"
private const val HEVY_WEIGHT_KG = "weight_kg"
private const val HEVY_REPS = "reps"
private const val HEVY_DISTANCE_MI = "distance_miles"
private const val HEVY_DISTANCE_KM = "distance_km"
private const val HEVY_DURATION = "duration_seconds"
private const val HEVY_RPE = "rpe"

private fun parseHevy(header: List<String>, records: List<CsvRecord>): ParsedWorkoutCsv {
    val hasLbs = HEVY_WEIGHT_LBS in header
    val hasKg = HEVY_WEIGHT_KG in header
    if (hasLbs == hasKg) {
        throw WorkoutCsvException(
            WorkoutCsvFailure.UNSUPPORTED_HEADER,
            "Hevy export must carry exactly one weight_lbs or weight_kg column"
        )
    }
    val hasMiles = HEVY_DISTANCE_MI in header
    val hasKm = HEVY_DISTANCE_KM in header
    if (hasMiles && hasKm) {
        throw WorkoutCsvException(
            WorkoutCsvFailure.UNSUPPORTED_HEADER,
            "Hevy export cannot carry both distance_miles and distance_km"
        )
    }
    val weightColumn = if (hasKg) HEVY_WEIGHT_KG else HEVY_WEIGHT_LBS
    val unit = if (hasKg) WeightUnit.KG else WeightUnit.LB
    val distanceColumn = when {
        hasMiles -> HEVY_DISTANCE_MI
        hasKm -> HEVY_DISTANCE_KM
        else -> null
    }
    validateHeader(
        header = header,
        required = setOf(HEVY_TITLE, HEVY_START, HEVY_EXERCISE, HEVY_REPS, weightColumn),
        optional = buildSet {
            add(HEVY_END)
            add(HEVY_DESCRIPTION)
            add(HEVY_SUPERSET)
            add(HEVY_EXERCISE_NOTES)
            add(HEVY_SET_INDEX)
            add(HEVY_SET_TYPE)
            add(HEVY_DURATION)
            add(HEVY_RPE)
            if (distanceColumn != null) add(distanceColumn)
        },
        source = WorkoutCsvSource.HEVY
    )
    val index = HeaderIndex(header)
    val metadataColumns = buildList {
        add(HEVY_DESCRIPTION)
        add(HEVY_SUPERSET)
        add(HEVY_EXERCISE_NOTES)
        if (distanceColumn != null) add(distanceColumn)
        add(HEVY_DURATION)
        add(HEVY_RPE)
    }
    val shapeColumns = buildList {
        add(HEVY_DURATION)
        if (distanceColumn != null) add(distanceColumn)
    }
    val rows = ArrayList<WorkoutCsvRow>()
    val rejected = ArrayList<RejectedWorkoutCsvRow>()
    for (record in records) {
        requireWidth(record, header)
        val titleText = index.value(record, HEVY_TITLE)
        val exerciseText = index.value(record, HEVY_EXERCISE)
        val startText = index.value(record, HEVY_START)
        val endText = index.value(record, HEVY_END)
        val weightText = index.value(record, weightColumn)
        val setTypeText = index.value(record, HEVY_SET_TYPE)
        val exercise = exerciseText.trim()
        val start = parseHevyDateTime(startText)
        val end = parseHevyDateTime(endText)
        val reps = parsePositiveInt(index.value(record, HEVY_REPS))
        val weight = parseWeight(weightText)
        val warmup = when {
            setTypeText.isBlank() -> null
            setTypeText.trim().equals("normal", ignoreCase = true) -> false
            setTypeText.trim().equals("warmup", ignoreCase = true) -> true
            else -> null
        }
        val diagnostics = RowDiagnostics()
        if (exercise.isEmpty()) {
            diagnostics.error(diagnostic(MISSING_EXERCISE_NAME, HEVY_EXERCISE, exerciseText))
        }
        if (start == null) diagnostics.error(diagnostic(INVALID_DATE, HEVY_START, startText))
        if (reps == null) diagnostics.error(repsDiagnostic(index, record, HEVY_REPS, shapeColumns))
        if (weight is WeightValue.Invalid) {
            diagnostics.error(diagnostic(INVALID_WEIGHT, weightColumn, weightText))
        }
        if (warmup == null && setTypeText.isNotBlank()) {
            diagnostics.error(diagnostic(UNSUPPORTED_SET_TYPE, HEVY_SET_TYPE, setTypeText))
        }
        if (endText.isNotBlank()) {
            if (end == null) {
                diagnostics.error(diagnostic(INVALID_END_TIME, HEVY_END, endText))
            } else if (start != null && end.isBefore(start)) {
                diagnostics.error(diagnostic(INVALID_END_TIME, HEVY_END, endText))
            }
        }
        for (column in metadataColumns) {
            addMetadataNote(index, record, column, diagnostics)
        }
        if (diagnostics.hasError) {
            rejected += RejectedWorkoutCsvRow(record.sourceRowIndex, diagnostics.findings)
        } else {
            val startTime = checkNotNull(start)
            rows += WorkoutCsvRow(
                sourceRowIndex = record.sourceRowIndex,
                exerciseName = exerciseText,
                day = startTime.day,
                timeOfDaySeconds = startTime.secondsOfDay,
                endDay = end?.day,
                endTimeOfDaySeconds = end?.secondsOfDay,
                workoutTitle = titleText.trim().ifBlank { null },
                groupHint = null,
                weight = (weight as? WeightValue.Measured)?.value,
                unit = unit,
                reps = checkNotNull(reps),
                warmup = warmup,
                diagnostics = diagnostics.findings
            )
        }
    }
    return ParsedWorkoutCsv(WorkoutCsvSource.HEVY, header, rows, rejected)
}

// --- Shared parsing helpers ---------------------------------------------------------------------

private class HeaderIndex(columns: List<String>) {
    private val positions: Map<String, Int> =
        columns.withIndex().associate { (position, name) -> name to position }

    fun value(record: CsvRecord, name: String): String {
        val position = positions[name] ?: return ""
        return record.values.getOrElse(position) { "" }
    }
}

private class RowDiagnostics {
    val findings = ArrayList<WorkoutCsvDiagnostic>()
    var hasError = false

    fun error(diagnostic: WorkoutCsvDiagnostic) {
        findings += diagnostic
        hasError = true
    }

    fun note(diagnostic: WorkoutCsvDiagnostic) {
        findings += diagnostic
    }
}

private sealed interface WeightValue {
    data object Blank : WeightValue
    data class Measured(val value: Double) : WeightValue
    data object Invalid : WeightValue
}

private data class SourceDateTime(val day: SourceCivilDate, val secondsOfDay: Int) {
    fun isBefore(other: SourceDateTime): Boolean {
        val thisDay = day.epochDay()
        val otherDay = other.day.epochDay()
        return thisDay < otherDay || (thisDay == otherDay && secondsOfDay < other.secondsOfDay)
    }
}

private fun validateHeader(
    header: List<String>,
    required: Set<String>,
    optional: Set<String>,
    source: WorkoutCsvSource
) {
    val duplicates = header.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    if (duplicates.isNotEmpty()) {
        throw WorkoutCsvException(
            WorkoutCsvFailure.DUPLICATE_HEADER,
            "duplicate column(s): ${duplicates.joinToString()}"
        )
    }
    val allowed = required + optional
    val unknown = header.filterNot { it in allowed }
    val missing = required.filterNot { it in header }
    if (unknown.isNotEmpty() || missing.isNotEmpty()) {
        val detail = buildString {
            if (unknown.isNotEmpty()) append("unknown columns: ${unknown.joinToString()}; ")
            if (missing.isNotEmpty()) append("missing columns: ${missing.joinToString()}")
        }
        throw WorkoutCsvException(
            WorkoutCsvFailure.UNSUPPORTED_HEADER,
            "${source.name} header does not match the supported shape ($detail)"
        )
    }
}

private fun requireWidth(record: CsvRecord, header: List<String>) {
    if (record.values.size != header.size) {
        throw WorkoutCsvException(
            WorkoutCsvFailure.MALFORMED_CSV,
            "record ${record.sourceRowIndex} has ${record.values.size} fields, " +
                "expected ${header.size}"
        )
    }
}

private fun addMetadataNote(
    index: HeaderIndex,
    record: CsvRecord,
    column: String,
    diagnostics: RowDiagnostics
) {
    val value = index.value(record, column)
    if (value.trim().isNotEmpty()) {
        diagnostics.note(diagnostic(UNSUPPORTED_METADATA, column, value))
    }
}

private fun repsDiagnostic(
    index: HeaderIndex,
    record: CsvRecord,
    repsColumn: String,
    shapeColumns: List<String>
): WorkoutCsvDiagnostic {
    val repsText = index.value(record, repsColumn)
    val shapeValuePresent = shapeColumns.any { index.value(record, it).trim().isNotEmpty() }
    return if (repsText.trim().isEmpty() && shapeValuePresent) {
        diagnostic(UNSUPPORTED_ROW_SHAPE, repsColumn, repsText)
    } else {
        diagnostic(INVALID_REPS, repsColumn, repsText)
    }
}

private fun diagnostic(
    code: WorkoutCsvDiagnosticCode,
    field: String? = null,
    value: String? = null
): WorkoutCsvDiagnostic = WorkoutCsvDiagnostic(
    code = code,
    message = when (code) {
        UNSUPPORTED_METADATA ->
            "Field '$field' carries a value this importer does not map."
        UNSUPPORTED_SET_TYPE ->
            "Set type '$value' is not a supported normal/warmup value."
        MISSING_EXERCISE_NAME ->
            "Row has no exercise name."
        INVALID_DATE ->
            "Field '$field' is not a valid date."
        INVALID_TIME ->
            "Field '$field' is not a valid time."
        INVALID_END_TIME ->
            "End time is missing, malformed or earlier than the start time."
        INVALID_REPS ->
            "Field '$field' is not a positive whole number."
        INVALID_WEIGHT ->
            "Field '$field' is not a finite, non-negative weight."
        INVALID_UNIT ->
            "Weight unit '$value' is not a known kg/lb value."
        UNSUPPORTED_ROW_SHAPE ->
            "Row has no rep target; timed-only and distance-only sets are unsupported."
    },
    field = field,
    value = value
)

private fun parseWeight(text: String): WeightValue {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return WeightValue.Blank
    val parsed = trimmed.toDoubleOrNull() ?: return WeightValue.Invalid
    return if (parsed.isFinite() && parsed >= 0.0) {
        WeightValue.Measured(parsed)
    } else {
        WeightValue.Invalid
    }
}

private fun parseWeightUnit(text: String): WeightUnit? = when (text.trim().lowercase()) {
    "kg" -> WeightUnit.KG
    "lb", "lbs" -> WeightUnit.LB
    else -> null
}

private fun parsePositiveInt(text: String): Int? {
    val parsed = digits(text.trim(), minLength = 1, maxLength = 9) ?: return null
    return if (parsed > 0) parsed else null
}

private fun digits(text: String, minLength: Int, maxLength: Int): Int? {
    if (text.length !in minLength..maxLength) return null
    if (!text.all { it in '0'..'9' }) return null
    return text.toIntOrNull()
}

private fun parseIsoDate(text: String): SourceCivilDate? {
    val trimmed = text.trim()
    if (trimmed.length != 10 || trimmed[4] != '-' || trimmed[7] != '-') return null
    val year = digits(trimmed.substring(0, 4), minLength = 4, maxLength = 4) ?: return null
    val month = digits(trimmed.substring(5, 7), minLength = 2, maxLength = 2) ?: return null
    val day = digits(trimmed.substring(8, 10), minLength = 2, maxLength = 2) ?: return null
    return civilDate(year, month, day)
}

private fun civilDate(year: Int, month: Int, day: Int): SourceCivilDate? {
    if (year !in 1..9999 || month !in 1..12 || day !in 1..31) return null
    val (actualYear, actualMonth, actualDay) = civilFromDays(daysFromCivil(year, month, day))
    return if (actualYear == year && actualMonth == month && actualDay == day) {
        SourceCivilDate(year, month, day)
    } else {
        null
    }
}

private fun strongDay(text: String): SourceCivilDate? {
    val trimmed = text.trim()
    if (trimmed.length < 10) return null
    return parseIsoDate(trimmed.substring(0, 10))
}

private fun strongTimeSeconds(text: String): Int? {
    val trimmed = text.trim()
    if (trimmed.length != 19 || trimmed[10] != ' ') return null
    return parseClockSeconds(trimmed.substring(11), withSeconds = true)
}

private fun parseClockSeconds(text: String, withSeconds: Boolean): Int? {
    val parts = text.trim().split(':')
    val expected = if (withSeconds) 3 else 2
    if (parts.size != expected) return null
    val hour = digits(parts[0], minLength = 2, maxLength = 2) ?: return null
    val minute = digits(parts[1], minLength = 2, maxLength = 2) ?: return null
    val second = if (withSeconds) {
        digits(parts[2], minLength = 2, maxLength = 2) ?: return null
    } else {
        0
    }
    if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
    return hour * 3600 + minute * 60 + second
}

private val ENGLISH_MONTHS: Map<String, Int> = mapOf(
    "jan" to 1,
    "january" to 1,
    "feb" to 2,
    "february" to 2,
    "mar" to 3,
    "march" to 3,
    "apr" to 4,
    "april" to 4,
    "may" to 5,
    "jun" to 6,
    "june" to 6,
    "jul" to 7,
    "july" to 7,
    "aug" to 8,
    "august" to 8,
    "sep" to 9,
    "september" to 9,
    "oct" to 10,
    "october" to 10,
    "nov" to 11,
    "november" to 11,
    "dec" to 12,
    "december" to 12
)

private fun parseHevyDateTime(text: String): SourceDateTime? {
    val comma = text.indexOf(',')
    if (comma < 0) return null
    val dateParts = text.substring(0, comma).trim().split(' ').filter { it.isNotEmpty() }
    if (dateParts.size != 3) return null
    val day = digits(dateParts[0], minLength = 1, maxLength = 2) ?: return null
    val month = ENGLISH_MONTHS[dateParts[1].lowercase()] ?: return null
    val year = digits(dateParts[2], minLength = 4, maxLength = 4) ?: return null
    val date = civilDate(year, month, day) ?: return null
    val seconds = parseClockSeconds(text.substring(comma + 1), withSeconds = false) ?: return null
    return SourceDateTime(date, seconds)
}

private fun SourceCivilDate.epochDay(): Long = daysFromCivil(year, month, day)

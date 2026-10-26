package com.hydrafit.app.core.domain.transfer

import com.hydrafit.app.core.domain.unit.WeightUnit

/**
 * A workout-history export layout this importer can read. Selection is explicit: the caller states
 * which provider produced the file, so the parser never guesses a layout from headers or values.
 */
enum class WorkoutCsvSource {
    FITNOTES,
    STRONG,
    HEVY
}

/**
 * Why an export could not be parsed at all. Each value is a whole-file condition; a value problem on
 * an otherwise well-formed row is reported as a [WorkoutCsvDiagnostic] on that row instead.
 */
enum class WorkoutCsvFailure {
    /** The text is empty, or contains only a header record. */
    EMPTY_INPUT,

    /** The text exceeds [WorkoutCsvLimits.MAX_UTF8_BYTES] of UTF-8. */
    TOO_LARGE,

    /** The file holds more data records than [WorkoutCsvLimits.MAX_DATA_ROWS]. */
    TOO_MANY_ROWS,

    /** A record has more fields than [WorkoutCsvLimits.MAX_COLUMNS]. */
    TOO_MANY_COLUMNS,

    /** A field has more characters than [WorkoutCsvLimits.MAX_FIELD_CHARS]. */
    FIELD_TOO_LONG,

    /**
     * The CSV structure is broken: an unclosed quote, a quote inside an unquoted field, text after a
     * closing quote, or a data row whose width does not match the header.
     */
    MALFORMED_CSV,

    /** The header repeats a column name. */
    DUPLICATE_HEADER,

    /** The header does not match the selected provider's supported column set. */
    UNSUPPORTED_HEADER
}

/** Raised for a whole-file failure; [failure] is the typed category for the caller to map. */
class WorkoutCsvException(val failure: WorkoutCsvFailure, message: String? = null) :
    Exception(message ?: failure.name)

/**
 * Engineering safeguards for one export, in the same spirit as
 * [com.hydrafit.app.core.domain.backup.BackupLimits]. These bound work and memory rather than
 * product capability, and are checked before or during the scan.
 */
object WorkoutCsvLimits {
    /** Total UTF-8 size accepted before parsing. */
    const val MAX_UTF8_BYTES: Long = 32L * 1024 * 1024

    /** Maximum data records, excluding the header. */
    const val MAX_DATA_ROWS: Int = 250_000

    /** Maximum fields in any record. */
    const val MAX_COLUMNS: Int = 64

    /** Maximum characters in any single field. */
    const val MAX_FIELD_CHARS: Int = 65_536
}

/**
 * A source-local civil date with no timezone. The parser validates it by round-tripping through the
 * shared civil-date helpers, so an impossible date such as 2024-02-30 is rejected rather than
 * normalized forward into the next month.
 */
data class SourceCivilDate(val year: Int, val month: Int, val day: Int)

/**
 * Classifies a row-level problem, or a source value that is preserved but not mapped.
 */
enum class WorkoutCsvDiagnosticCode {
    /** A recognized field carried a value this importer does not map (RPE, notes, ...). */
    UNSUPPORTED_METADATA,

    /** A set-type value other than the supported `normal`/`warmup`. */
    UNSUPPORTED_SET_TYPE,

    /** A row has no exercise name. */
    MISSING_EXERCISE_NAME,

    /** A date field is malformed or names an impossible day. */
    INVALID_DATE,

    /** A time field is malformed or out of range. */
    INVALID_TIME,

    /** An end time is malformed, or precedes the start time. */
    INVALID_END_TIME,

    /** Reps are missing or are not a positive integer. */
    INVALID_REPS,

    /** A duration field is present but is not a whole number of seconds. */
    INVALID_DURATION,

    /** A distance field is present but is not a positive, finite number of the stated unit. */
    INVALID_DISTANCE,

    /** A distance unit is not a known m/km/mi value. */
    INVALID_DISTANCE_UNIT,

    /** A non-blank weight is not a finite number, or is negative. */
    INVALID_WEIGHT,

    /** A weight unit is not a known kg/lb value. */
    INVALID_UNIT,

    /**
     * The row records more than one of a rep count, a duration and a distance, so its measurement is
     * ambiguous.
     */
    AMBIGUOUS_ROW_SHAPE,

    /** The row records none of a rep count, a duration and a distance. */
    UNSUPPORTED_ROW_SHAPE
}

/**
 * A single row-level finding. [field] and [value] retain the original source text so an unsupported
 * or invalid value is preserved verbatim instead of being silently dropped.
 */
data class WorkoutCsvDiagnostic(
    val code: WorkoutCsvDiagnosticCode,
    val message: String,
    val field: String? = null,
    val value: String? = null
)

/**
 * One importable weighted set, counted, timed or distance-based, in source order. Nothing is inferred:
 * a blank weight stays null, an unavailable warmup flag stays null, and the source-local date/time is
 * never turned into a fake UTC instant or a session id. A row is measured by exactly one of a positive
 * rep count, positive whole seconds or a positive distance in whole metres.
 */
data class WorkoutCsvRow(
    /**
     * 1-based record position in the source file: the header is record 1 and truly blank lines are
     * not counted, so a quoted newline inside a field does not shift later indices.
     */
    val sourceRowIndex: Int,

    /** The exercise name exactly as written in the source. */
    val exerciseName: String,

    /** Source-local civil date of the set. */
    val day: SourceCivilDate,

    /** Seconds past local midnight when the source carried a time, otherwise null. */
    val timeOfDaySeconds: Int?,

    /** Source-local civil date of the end time when present, otherwise null. */
    val endDay: SourceCivilDate?,

    /** Seconds past local midnight of the end time when present, otherwise null. */
    val endTimeOfDaySeconds: Int?,

    /** Workout title or name when the provider carries one, otherwise null. */
    val workoutTitle: String?,

    /**
     * Exercise grouping hint when the provider carries one (for example FitNotes `Category`). This
     * is exercise metadata, never a session identifier.
     */
    val groupHint: String?,

    /** Native weight value exactly as exported, or null when the field is blank. */
    val weight: Double?,

    /** Unit the weight is expressed in, taken from the source column. */
    val unit: WeightUnit,

    /** Positive repetition count for counted work, or null when the set is measured by [durationSeconds]. */
    val reps: Int?,

    /** Positive whole seconds for a duration-measured set, or null for counted or distance work. */
    val durationSeconds: Int?,

    /** Positive whole metres for a distance-measured set, or null for counted or timed work. */
    val distanceMeters: Int? = null,

    /** Known warmup flag, or null when the source does not say. */
    val warmup: Boolean?,

    /** Row findings, including non-blocking `UNSUPPORTED_METADATA` notes. */
    val diagnostics: List<WorkoutCsvDiagnostic>
)

/** A source row that could not become an importable set; [sourceRowIndex] follows [WorkoutCsvRow]. */
data class RejectedWorkoutCsvRow(
    val sourceRowIndex: Int,
    val diagnostics: List<WorkoutCsvDiagnostic>
)

/** The parsed file: importable and rejected rows both keep source order and duplicates. */
data class ParsedWorkoutCsv(
    val source: WorkoutCsvSource,

    /** The header fields in source order. */
    val columns: List<String>,

    val rows: List<WorkoutCsvRow>,

    val rejectedRows: List<RejectedWorkoutCsvRow>,

    /** File-scoped notes; currently empty, reserved for provider-level findings. */
    val diagnostics: List<WorkoutCsvDiagnostic> = emptyList()
)

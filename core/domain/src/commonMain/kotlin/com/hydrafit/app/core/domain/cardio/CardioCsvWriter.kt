package com.hydrafit.app.core.domain.cardio

/**
 * Writes recorded cardio as its own analysis CSV.
 *
 * This is deliberately separate from the resistance-set analysis export: cardio has no reps, load,
 * e1RM or volume, and folding a duration into those columns would invent a meaning the data does not
 * have. It is an export for analysis in another tool, not a re-import format.
 */
object CardioCsvWriter {
    private val header = listOf(
        "activityType",
        "startedAtMillis",
        "durationSeconds",
        "distanceMeters",
        "note"
    )

    fun write(activities: List<CardioActivity>): String = buildString {
        appendLine(header.joinToString(","))
        activities.sortedBy { it.startedAtMillis }.forEach { activity ->
            appendLine(
                listOf(
                    activity.type.name,
                    activity.startedAtMillis.toString(),
                    activity.durationSeconds.toString(),
                    activity.distanceMeters?.toString() ?: "",
                    field(activity.note)
                ).joinToString(",")
            )
        }
    }

    private fun field(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val safeValue = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
            value.first() == '\t' ||
            value.first() == '\r'
        ) {
            "'$value"
        } else {
            value
        }
        val needsQuoting = safeValue.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return safeValue
        return "\"" + safeValue.replace("\"", "\"\"") + "\""
    }
}

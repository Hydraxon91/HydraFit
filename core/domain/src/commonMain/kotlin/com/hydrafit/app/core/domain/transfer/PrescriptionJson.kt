package com.hydrafit.app.core.domain.transfer

import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The JSON codec for a prescription document. Defaults are encoded so every field is explicit and
 * unknown fields are rejected, so a document from another version is never read as if it were this
 * one. Decode enforces the resource limits and the required top-level fields before any decode work.
 */
object PrescriptionJson {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    private val requiredTopLevelKeys: Set<String> =
        PrescriptionDocument.serializer().descriptor.elementNames.toSet()

    fun encode(document: PrescriptionDocument): String {
        requireRecordCount(document)
        val text = json.encodeToString(PrescriptionDocument.serializer(), document)
        if (text.encodeToByteArray().size.toLong() > PrescriptionLimits.MAX_BYTES) {
            throw PrescriptionException(PrescriptionFailure.TOO_LARGE)
        }
        return text
    }

    /** Decodes the payload, mapping a parse failure to [PrescriptionFailure.MALFORMED]. */
    fun decode(text: String): PrescriptionDocument {
        if (text.encodeToByteArray().size.toLong() > PrescriptionLimits.MAX_BYTES) {
            throw PrescriptionException(PrescriptionFailure.TOO_LARGE)
        }
        requireDepthAndStrings(text)
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw PrescriptionException(PrescriptionFailure.MALFORMED, e.message)
        }
        val obj = element as? JsonObject
            ?: throw PrescriptionException(PrescriptionFailure.MALFORMED, "not a JSON object")
        val version = (obj["formatVersion"] as? JsonPrimitive)?.intOrNull
        val requiredKeys = requiredTopLevelKeys - buildSet {
            if (version != null && version < 1) add("customExercises")
        }
        val missing = requiredKeys - obj.keys
        if (missing.isNotEmpty()) {
            throw PrescriptionException(PrescriptionFailure.MALFORMED, "missing fields: $missing")
        }
        val document = try {
            json.decodeFromJsonElement(PrescriptionDocument.serializer(), obj)
        } catch (e: Exception) {
            throw PrescriptionException(PrescriptionFailure.MALFORMED, e.message)
        }
        requireRecordCount(document)
        return document
    }

    /** A cheap pre-parse scan that bounds depth and per-string length without trusting the parser. */
    private fun requireDepthAndStrings(text: String) {
        var depth = 0
        var inString = false
        var escaped = false
        var stringLength = 0
        for (ch in text) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                    else -> {
                        stringLength++
                        if (stringLength > PrescriptionLimits.MAX_STRING_LENGTH) {
                            throw PrescriptionException(PrescriptionFailure.OVER_LIMIT)
                        }
                    }
                }
            } else {
                when (ch) {
                    '"' -> {
                        inString = true
                        stringLength = 0
                    }
                    '{', '[' -> {
                        depth++
                        if (depth > PrescriptionLimits.MAX_DEPTH) {
                            throw PrescriptionException(PrescriptionFailure.OVER_LIMIT)
                        }
                    }
                    '}', ']' -> if (depth > 0) depth--
                }
            }
        }
    }

    private fun requireRecordCount(document: PrescriptionDocument) {
        val count = document.routines.size +
            document.routines.sumOf { routine ->
                routine.workouts.size + routine.workouts.sumOf { workout -> workout.entries.size }
            } +
            document.customExercises.size
        if (count > PrescriptionLimits.MAX_RECORDS) {
            throw PrescriptionException(PrescriptionFailure.OVER_LIMIT)
        }
    }
}

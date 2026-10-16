package com.hydrafit.app.core.domain.backup

import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The JSON codec for the logical backup format. Defaults are encoded so every field (including an
 * explicit `null`) is present, and unknown fields are rejected so a newer file is not silently
 * read as if it were v1. Decode also enforces the resource limits and the required top-level fields,
 * so an incomplete or oversized file is rejected before any decode work.
 */
object BackupJson {
    /** Resource safeguard, not a product guarantee; both encode and decode reject above this size. */
    const val MAX_BYTES: Long = BackupLimits.MAX_BYTES

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    private val requiredTopLevelKeys: Set<String> =
        BackupFile.serializer().descriptor.elementNames.toSet()

    fun encode(file: BackupFile): String {
        requireRecordCount(file)
        val text = json.encodeToString(BackupFile.serializer(), file)
        if (text.encodeToByteArray().size.toLong() > MAX_BYTES) {
            throw BackupException(BackupFailure.TOO_LARGE)
        }
        return text
    }

    /** Decodes the payload, mapping a parse failure to [BackupFailure.MALFORMED]. */
    fun decode(text: String): BackupFile {
        if (text.encodeToByteArray().size.toLong() > MAX_BYTES) {
            throw BackupException(BackupFailure.TOO_LARGE)
        }
        requireDepthAndStrings(text)
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw BackupException(BackupFailure.MALFORMED, e.message)
        }
        val obj = element as? JsonObject
            ?: throw BackupException(BackupFailure.MALFORMED, "the backup is not a JSON object")
        val version = (obj["formatVersion"] as? JsonPrimitive)?.intOrNull
        val requiredKeys = requiredTopLevelKeys - buildSet {
            if (version != null && version < 5) add("restPreferences")
            if (version != null && version < 6) add("trainingGuidance")
            if (version != null && version < 7) {
                add("planTargetExplanations")
                add("planTargetExplanationStates")
            }
            if (version != null && version < 8) add("entryTargetExplanations")
            if (version != null && version < 9) add("exerciseNotes")
            if (version != null && version < 10) add("sessionNotes")
            if (version != null && version < SETUP_FORMAT_VERSION) {
                add("setup")
                add("preferredWeekdays")
            }
            if (version != null && version < REMINDER_FORMAT_VERSION) add("workoutReminders")
            if (version != null && version < BODY_WEIGHT_FORMAT_VERSION) {
                add("bodyWeightMeasurements")
                add("bodyWeightGoalKg")
            }
            if (version != null && version < EQUIPMENT_PROFILE_FORMAT_VERSION) {
                add("equipmentProfiles")
                add("activeEquipmentProfileId")
            }
            if (version != null && version < IMPORT_PROVENANCE_FORMAT_VERSION) {
                add("importProvenance")
            }
            if (version != null && version < IMPORT_BUCKET_FORMAT_VERSION) add("importBuckets")
        }
        val missing = requiredKeys - obj.keys
        if (missing.isNotEmpty()) {
            throw BackupException(BackupFailure.MALFORMED, "missing fields: $missing")
        }
        requireTimingFields(obj)
        requireSettingsFields(obj)
        if (version != null && version >= EQUIPMENT_PROFILE_FORMAT_VERSION) {
            requireEquipmentProfileFields(obj)
        }
        if (version != null && version >= SETUP_FORMAT_VERSION) {
            val setup = obj["setup"] as? JsonObject
                ?: throw BackupException(BackupFailure.MALFORMED, "setup is not an object")
            if (!setup.keys.containsAll(setOf("version", "disposition", "step"))) {
                throw BackupException(BackupFailure.MALFORMED, "setup is incomplete")
            }
        }
        val file = try {
            json.decodeFromJsonElement(BackupFile.serializer(), obj)
        } catch (e: Exception) {
            throw BackupException(BackupFailure.MALFORMED, e.message)
        }
        requireRecordCount(file)
        return file
    }

    private fun requireEquipmentProfileFields(root: JsonObject) {
        val profiles = root["equipmentProfiles"] as? JsonArray
            ?: throw BackupException(BackupFailure.MALFORMED)
        profiles.forEach { element ->
            val profile = element as? JsonObject
                ?: throw BackupException(BackupFailure.MALFORMED)
            val inventory = profile["loadingInventory"] as? JsonObject
                ?: throw BackupException(BackupFailure.MALFORMED)
            if (!inventory.keys.containsAll(setOf("unit", "plates", "bars", "dumbbells"))) {
                throw BackupException(BackupFailure.MALFORMED)
            }
            val bars = inventory["bars"] as? JsonArray
                ?: throw BackupException(BackupFailure.MALFORMED)
            bars.forEach {
                val bar = it as? JsonObject ?: throw BackupException(BackupFailure.MALFORMED)
                if ("collarTicks" !in bar) throw BackupException(BackupFailure.MALFORMED)
            }
            val dumbbells = inventory["dumbbells"] as? JsonArray
                ?: throw BackupException(BackupFailure.MALFORMED)
            dumbbells.forEach {
                val dumbbell = it as? JsonObject ?: throw BackupException(BackupFailure.MALFORMED)
                val required = setOf(
                    "fixedTicks",
                    "minimumTicks",
                    "maximumTicks",
                    "incrementTicks"
                )
                if (!dumbbell.keys.containsAll(required)) {
                    throw BackupException(BackupFailure.MALFORMED)
                }
            }
        }
    }

    private fun requireSettingsFields(root: JsonObject) {
        val version = (root["formatVersion"] as? JsonPrimitive)?.intOrNull ?: return
        if (version !in 2..BACKUP_FORMAT_VERSION) return
        val settings = root["settings"] as? JsonObject ?: return
        if ("guidedWorkoutEnabled" !in settings) {
            throw BackupException(
                BackupFailure.MALFORMED,
                "settings is missing guidedWorkoutEnabled"
            )
        }
    }

    private fun requireTimingFields(root: JsonObject) {
        val version = (root["formatVersion"] as? JsonPrimitive)?.intOrNull ?: return
        if (version < 3) return
        val required = if (version == 3) {
            setOf("timingProvenance")
        } else {
            setOf("timingProvenance", "startedAtElapsedMillis", "completedAtElapsedMillis")
        }
        val sets = root["workoutSets"] as? JsonArray ?: return
        sets.forEachIndexed { index, element ->
            val record = element as? JsonObject
                ?: throw BackupException(
                    BackupFailure.MALFORMED,
                    "workoutSets[$index] is not an object"
                )
            val missing = required - record.keys
            if (missing.isNotEmpty()) {
                throw BackupException(
                    BackupFailure.MALFORMED,
                    "workoutSets[$index] is missing timing fields: $missing"
                )
            }
        }
    }

    /**
     * A cheap pre-parse scan that bounds nesting depth and per-string length without trusting the
     * recursive parser to survive a hostile input. String contents and escapes are tracked so a
     * bracket or quote inside a value does not confuse the count.
     */
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
                        if (stringLength > BackupLimits.MAX_STRING_LENGTH) {
                            throw BackupException(BackupFailure.OVER_LIMIT)
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
                        if (depth > BackupLimits.MAX_DEPTH) {
                            throw BackupException(BackupFailure.OVER_LIMIT)
                        }
                    }
                    '}', ']' -> if (depth > 0) depth--
                }
            }
        }
    }

    private fun requireRecordCount(file: BackupFile) {
        if (recordCount(file) > BackupLimits.MAX_RECORDS) {
            throw BackupException(BackupFailure.OVER_LIMIT)
        }
    }

    private fun recordCount(file: BackupFile): Int =
        file.customExercises.size + file.exerciseOverrides.size + file.equipment.size +
            file.selectedEquipment.size + file.workoutSets.size + file.workoutSessions.size +
            file.plans.size + file.planDays.size + file.planEntries.size +
            file.volumeExplanations.size + file.volumeExplanationStates.size +
            file.planTargetExplanations.size + file.planTargetExplanationStates.size +
            file.entryTargetExplanations.size +
            file.exerciseNotes.size +
            file.sessionNotes.size +
            file.routines.size + file.routineWorkouts.size + file.routineEntries.size +
            file.activations.size + file.activationWorkouts.size + file.activationEntries.size +
            file.occurrences.size + file.occurrenceEntries.size + file.personalRecords.size +
            file.preferences.size + file.exclusions.size + file.restPreferences.size +
            file.importProvenance.size +
            file.importBuckets.sumOf { 1 + it.importedSessionIds.size } +
            file.bodyWeightMeasurements.size + file.equipmentProfiles.sumOf {
                1 + it.selectedEquipment.size + it.ceilings.size + it.loadingInventory.plates.size +
                    it.loadingInventory.bars.size +
                    it.loadingInventory.dumbbells.sumOf { dumbbell -> 1 + dumbbell.fixedTicks.size }
            }
}

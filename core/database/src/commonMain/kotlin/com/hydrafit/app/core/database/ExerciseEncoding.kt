package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

internal fun encodeEquipment(tags: Set<EquipmentTag>): String =
    tags.joinToString(separator = ",") { it.id }

internal fun decodeEquipment(value: String): Set<EquipmentTag> =
    if (value.isEmpty()) emptySet() else value.split(',').map { EquipmentTag(it) }.toSet()

internal fun encodeMuscles(muscles: Set<MuscleGroup>): String =
    muscles.joinToString(separator = ",") { it.name }

internal fun decodeMuscles(value: String): Set<MuscleGroup> = value.toEnumSet(MuscleGroup::valueOf)

/**
 * Encodes per-muscle involvement weights as `MUSCLE:weight` pairs. An empty map encodes as the empty
 * string, which is distinct from a null column: null means "not set" (fall back to the seed), while
 * `""` means "explicitly cleared".
 */
internal fun encodeInvolvements(involvements: Map<MuscleGroup, Double>): String = involvements
    .entries
    .sortedBy { it.key.name }
    .joinToString(separator = ",") { "${it.key.name}:${it.value}" }

private val LEGACY_MUSCLE_EXPANSION: Map<String, Map<MuscleGroup, Double>> = mapOf(
    "CHEST" to mapOf(MuscleGroup.CHEST_UPPER to 0.5, MuscleGroup.CHEST_LOWER to 0.5),
    "BACK" to mapOf(
        MuscleGroup.LATS to 0.5,
        MuscleGroup.UPPER_BACK to 0.35,
        MuscleGroup.LOWER_BACK to 0.15
    ),
    "SHOULDERS" to mapOf(
        MuscleGroup.FRONT_DELTS to 0.3,
        MuscleGroup.SIDE_DELTS to 0.4,
        MuscleGroup.REAR_DELTS to 0.3
    ),
    "CORE" to mapOf(MuscleGroup.ABS to 0.7, MuscleGroup.OBLIQUES to 0.3)
)

internal fun decodeInvolvements(value: String?): Map<MuscleGroup, Double> {
    if (value.isNullOrEmpty()) return emptyMap()
    val resolved = mutableMapOf<MuscleGroup, Double>()
    value.split(',').forEach { entry ->
        val parts = entry.split(':')
        if (parts.size != 2) return@forEach
        val weight = parts[1].toDoubleOrNull() ?: return@forEach
        val legacy = LEGACY_MUSCLE_EXPANSION[parts[0]]
        if (legacy != null) {
            legacy.forEach { (muscle, fraction) ->
                resolved[muscle] = (resolved[muscle] ?: 0.0) + weight * fraction
            }
            return@forEach
        }
        val muscle = runCatching { MuscleGroup.valueOf(parts[0]) }.getOrNull() ?: return@forEach
        resolved[muscle] = (resolved[muscle] ?: 0.0) + weight
    }
    return resolved
}

internal fun decodeMovementPattern(value: String): MovementPattern =
    runCatching { MovementPattern.valueOf(value) }.getOrDefault(MovementPattern.CORE)

private inline fun <T> String.toEnumSet(transform: (String) -> T): Set<T> =
    if (isEmpty()) emptySet() else split(',').map(transform).toSet()

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

/** Encodes per-muscle involvement weights as `MUSCLE:weight` pairs, or null when empty. */
internal fun encodeInvolvements(involvements: Map<MuscleGroup, Double>): String? = involvements
    .entries
    .sortedBy { it.key.name }
    .joinToString(separator = ",") { "${it.key.name}:${it.value}" }
    .takeIf { it.isNotEmpty() }

internal fun decodeInvolvements(value: String?): Map<MuscleGroup, Double> {
    if (value.isNullOrEmpty()) return emptyMap()
    return value.split(',').mapNotNull { entry ->
        val parts = entry.split(':')
        if (parts.size != 2) return@mapNotNull null
        val muscle = runCatching { MuscleGroup.valueOf(parts[0]) }.getOrNull()
            ?: return@mapNotNull null
        val weight = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        muscle to weight
    }.toMap()
}

internal fun decodeMovementPattern(value: String): MovementPattern =
    runCatching { MovementPattern.valueOf(value) }.getOrDefault(MovementPattern.CORE)

private inline fun <T> String.toEnumSet(transform: (String) -> T): Set<T> =
    if (isEmpty()) emptySet() else split(',').map(transform).toSet()

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

internal fun decodeMovementPattern(value: String): MovementPattern =
    runCatching { MovementPattern.valueOf(value) }.getOrDefault(MovementPattern.CORE)

private inline fun <T> String.toEnumSet(transform: (String) -> T): Set<T> =
    if (isEmpty()) emptySet() else split(',').map(transform).toSet()

package com.hydrafit.app.core.domain.equipment

/** Allocates custom identities without consuming catalog-owned or already referenced identities. */
object EquipmentIdentity {
    fun availableCustomId(base: String, unavailable: Set<String>): String {
        if (base !in unavailable) return base
        var suffix = 2
        while ("${base}_$suffix" in unavailable) suffix++
        return "${base}_$suffix"
    }

    fun collisionMapping(
        customIds: Set<String>,
        occupiedIds: Set<String>,
        reservedIds: Set<String>
    ): Map<String, String> {
        val unavailable = (occupiedIds + reservedIds).toMutableSet()
        return customIds.intersect(reservedIds).sorted().associateWith { id ->
            availableCustomId(id, unavailable).also { unavailable += it }
        }
    }

    /** Replaces whole stored CSV tokens only; unrelated identities and their order stay intact. */
    fun remapCsv(value: String, mapping: Map<String, String>): String =
        value.split(',').joinToString(",") { mapping[it] ?: it }
}

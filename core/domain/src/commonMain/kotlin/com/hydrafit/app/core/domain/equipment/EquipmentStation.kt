package com.hydrafit.app.core.domain.equipment

/** What a recorded station load represents. */
enum class StationLoadBasis {
    /** The number is the total load for the implement or station. */
    TOTAL,

    /** The number is one dumbbell or one hand's load. */
    PER_HAND,

    /** The number is one side's load on a bilateral implement. */
    PER_SIDE;

    companion object {
        /** Strict persisted decoding; an unknown stored value is not silently reinterpreted. */
        fun fromStored(value: String?): StationLoadBasis? = entries.firstOrNull { it.name == value }
    }
}

/**
 * One user-named physical station inside an equipment profile.
 *
 * A station is deliberately not an [EquipmentTag]: several exercises share one cable or machine
 * tag, but their loads are not comparable across different physical stacks. The optional grid is
 * either entirely absent or a complete min/max/increment triple. A zero [id] is a draft that the
 * repository has not inserted yet; persisted identities are positive.
 */
data class EquipmentStation(
    val id: Long = 0,
    val profileId: Long,
    val equipmentId: EquipmentTag,
    val name: String,
    val loadBasis: StationLoadBasis = StationLoadBasis.TOTAL,
    val minWeightKg: Double? = null,
    val maxWeightKg: Double? = null,
    val incrementKg: Double? = null,
    val retired: Boolean = false
) {
    init {
        requireValid()
    }

    val hasGrid: Boolean
        get() = minWeightKg != null && maxWeightKg != null && incrementKg != null

    /** The frozen attribution a performed set carries; drafts have no persisted identity yet. */
    fun attribution(): StationAttribution? = if (id > 0L) {
        StationAttribution(id = id, name = name, loadBasis = loadBasis)
    } else {
        null
    }

    fun requireValid() {
        require(id >= 0L) { "A station id cannot be negative" }
        require(profileId > 0L) { "A station belongs to a persisted profile" }
        require(equipmentId.id.isNotBlank()) { "A station needs an equipment identity" }
        require(
            name.isNotBlank() &&
                name == name.trim() &&
                name.length <= MAX_STATION_NAME_LENGTH
        ) {
            "A station name must be trimmed, non-blank and at most $MAX_STATION_NAME_LENGTH characters"
        }
        val gridParts = listOf(minWeightKg, maxWeightKg, incrementKg)
        require(gridParts.all { it == null } || gridParts.all { it != null }) {
            "A station grid needs minimum, maximum and increment together"
        }
        if (hasGrid) {
            val minimum = requireNotNull(minWeightKg)
            val maximum = requireNotNull(maxWeightKg)
            val increment = requireNotNull(incrementKg)
            require(
                minimum.isFinite() &&
                    maximum.isFinite() &&
                    increment.isFinite() &&
                    minimum >= 0.0 &&
                    maximum >= minimum &&
                    increment > 0.0
            ) {
                "A station grid must be finite with 0 <= minimum <= maximum and a positive increment"
            }
            require(maximum <= MAX_STATION_WEIGHT_KG) {
                "A station grid cannot exceed $MAX_STATION_WEIGHT_KG kg"
            }
        }
    }

    companion object {
        const val MAX_STATION_NAME_LENGTH: Int = 80

        /** Keeps the text editor and the station grid inside the existing loading-scale range. */
        const val MAX_STATION_WEIGHT_KG: Double = 2000.0
    }
}

/**
 * The station meaning frozen onto a performed set. It is intentionally independent of the mutable
 * station row: deleting, renaming or retiring a station never rewrites recorded history.
 */
data class StationAttribution(
    /**
     * A positive local station id, or `0` for a frozen label imported by an add-only merge whose
     * equipment profiles are intentionally not merged. Zero never matches a station filter.
     */
    val id: Long,
    val name: String,
    val loadBasis: StationLoadBasis
) {
    init {
        require(id >= 0L) { "A performed station id cannot be negative" }
        require(
            name.isNotBlank() &&
                name == name.trim() &&
                name.length <= EquipmentStation.MAX_STATION_NAME_LENGTH
        ) {
            "A performed station name must be trimmed, non-blank and within the station name limit"
        }
    }
}

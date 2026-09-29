package com.hydrafit.app.core.domain.equipment

/**
 * Identifies a piece of equipment. Built-ins are stable constants; users may add their own ids via
 * the equipment catalog. Equality is by id, so a tag decoded from the database compares equal to a
 * built-in constant. Display names for custom equipment live in the catalog.
 */
data class EquipmentTag(val id: String) {
    val displayName: String
        get() = BUILT_IN_NAMES[id] ?: id

    companion object {
        val BARBELL = EquipmentTag("BARBELL")
        val DUMBBELL = EquipmentTag("DUMBBELL")
        val KETTLEBELL = EquipmentTag("KETTLEBELL")
        val BENCH = EquipmentTag("BENCH")
        val PULL_UP_BAR = EquipmentTag("PULL_UP_BAR")
        val RESISTANCE_BAND = EquipmentTag("RESISTANCE_BAND")
        val CABLE_MACHINE = EquipmentTag("CABLE_MACHINE")
        val BODYWEIGHT = EquipmentTag("BODYWEIGHT")

        val BUILT_IN: List<EquipmentTag> = listOf(
            BARBELL,
            DUMBBELL,
            KETTLEBELL,
            BENCH,
            PULL_UP_BAR,
            RESISTANCE_BAND,
            CABLE_MACHINE,
            BODYWEIGHT
        )

        val BUILT_IN_NAMES: Map<String, String> = mapOf(
            BARBELL.id to "Barbell",
            DUMBBELL.id to "Dumbbells",
            KETTLEBELL.id to "Kettlebell",
            BENCH.id to "Bench",
            PULL_UP_BAR.id to "Pull-up bar",
            RESISTANCE_BAND.id to "Resistance bands",
            CABLE_MACHINE.id to "Cable machine",
            BODYWEIGHT.id to "Bodyweight"
        )
    }
}

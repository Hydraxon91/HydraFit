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
        val EZ_BAR = EquipmentTag("EZ_BAR")
        val TRAP_BAR = EquipmentTag("TRAP_BAR")
        val AB_ROLLER = EquipmentTag("AB_ROLLER")
        val LEG_EXTENSION_MACHINE = EquipmentTag("LEG_EXTENSION_MACHINE")
        val LEG_PRESS_MACHINE = EquipmentTag("LEG_PRESS_MACHINE")
        val LEG_CURL_MACHINE = EquipmentTag("LEG_CURL_MACHINE")

        val BUILT_IN: List<EquipmentTag> = listOf(
            BARBELL,
            DUMBBELL,
            KETTLEBELL,
            BENCH,
            PULL_UP_BAR,
            RESISTANCE_BAND,
            CABLE_MACHINE,
            BODYWEIGHT,
            EZ_BAR,
            TRAP_BAR,
            AB_ROLLER,
            LEG_EXTENSION_MACHINE,
            LEG_PRESS_MACHINE,
            LEG_CURL_MACHINE
        )

        val BUILT_IN_NAMES: Map<String, String> = mapOf(
            BARBELL.id to "Barbell",
            DUMBBELL.id to "Dumbbells",
            KETTLEBELL.id to "Kettlebell",
            BENCH.id to "Bench",
            PULL_UP_BAR.id to "Pull-up bar",
            RESISTANCE_BAND.id to "Resistance bands",
            CABLE_MACHINE.id to "Cable machine",
            BODYWEIGHT.id to "Bodyweight",
            EZ_BAR.id to "EZ bar",
            TRAP_BAR.id to "Trap bar",
            AB_ROLLER.id to "Ab roller",
            LEG_EXTENSION_MACHINE.id to "Leg extension machine",
            LEG_PRESS_MACHINE.id to "Leg press machine",
            LEG_CURL_MACHINE.id to "Leg curl machine"
        )
    }
}

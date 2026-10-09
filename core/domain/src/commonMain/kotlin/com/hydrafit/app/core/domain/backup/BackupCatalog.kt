package com.hydrafit.app.core.domain.backup

/**
 * The catalog identities the receiving install owns: the seeded exercise ids and the built-in
 * equipment ids. Used by validation to accept a backup's references without guessing by name.
 */
interface BackupCatalog {
    fun seedExerciseIds(): Set<String>

    fun builtInEquipmentIds(): Set<String>

    /**
     * The normalized name keys of the non-CAT-P7 seeded exercises that startup
     * `CustomExerciseDedupe` would merge a same-named custom into. CAT-P7 seed names are excluded
     * because their pre-existing same-name customs are intentionally preserved.
     */
    fun dedupeSeedKeys(): Set<String> = emptySet()

    /** The receiving install's startup-dedupe name key for [name] (trim, collapse, lowercase). */
    fun dedupeNameKey(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase()

    /**
     * A stable profile string per seeded exercise id, covering the fields that affect planning and
     * fatigue. Used to detect a catalog whose definitions changed under the same ids. An install may
     * hold additional seeds; those are allowed.
     */
    fun seedProfiles(): Map<String, String> = emptyMap()
}

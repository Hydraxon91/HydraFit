package com.hydrafit.app.core.domain.backup

/**
 * The catalog identities the receiving install owns: the seeded exercise ids and the built-in
 * equipment ids. Used by validation to accept a backup's references without guessing by name.
 */
interface BackupCatalog {
    fun seedExerciseIds(): Set<String>

    fun builtInEquipmentIds(): Set<String>
}

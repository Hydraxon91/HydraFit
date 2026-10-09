package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.backup.BackupCatalog
import com.hydrafit.app.core.domain.equipment.EquipmentTag

/** The catalog identities this install owns, read from the compiled seed rather than user rows. */
class SqlDelightBackupCatalog : BackupCatalog {
    override fun seedExerciseIds(): Set<String> = DefaultExercises.all.map { it.id }.toSet()

    override fun builtInEquipmentIds(): Set<String> = EquipmentTag.BUILT_IN.map { it.id }.toSet()
}

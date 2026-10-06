package com.hydrafit.app.core.database

class CustomExerciseDedupe(private val database: HydraFitDatabase) {
    fun run() {
        val canonicalByName = DefaultExercises.all.associateBy { it.name }
        val customRows = database.exerciseQueries.selectAll().executeAsList()
            .filter { it.isCustom == 1L }
        val merges = customRows.mapNotNull { row ->
            val canonicalId = canonicalByName[row.name]?.id?.takeIf { it != row.id }
                ?: return@mapNotNull null
            row.id to canonicalId
        }
        if (merges.isEmpty()) return
        database.transaction {
            merges.forEach { (customId, canonicalId) ->
                database.workoutLogQueries.updateSetExerciseId(
                    newId = canonicalId,
                    oldId = customId
                )
                database.planHistoryQueries.updateEntryExerciseId(
                    newId = canonicalId,
                    oldId = customId
                )
                val customRecord =
                    database.personalRecordQueries.selectById(customId).executeAsOneOrNull()
                if (customRecord != null) {
                    val canonicalRecord =
                        database.personalRecordQueries.selectById(canonicalId).executeAsOneOrNull()
                    if (canonicalRecord != null) {
                        database.personalRecordQueries.deleteById(customId)
                    } else {
                        database.personalRecordQueries.updateExerciseId(
                            newId = canonicalId,
                            oldId = customId
                        )
                    }
                }
                database.exerciseOverrideQueries.deleteById(customId)
                database.exerciseQueries.deleteById(customId)
            }
        }
    }
}

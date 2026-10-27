package com.hydrafit.app.core.domain.backup

import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The format-28 cluster section is written, read back and validated as one unit: the parent's three
 * identity fields and the additional segment rows must survive an export/import round trip without
 * recomputation.
 */
class ClusterBackupTest {

    private val catalog = object : BackupCatalog {
        override fun seedExerciseIds(): Set<String> = setOf("back-squat")

        override fun builtInEquipmentIds(): Set<String> = setOf("BARBELL")

        override fun dedupeSeedKeys(): Set<String> = setOf("back squat")

        override fun seedProfiles(): Map<String, String> =
            mapOf("back-squat" to "back-squat|BARBELL|SQUAT|false|EXTERNAL|")
    }

    private val validator = BackupValidator(catalog)

    @Test
    fun format28RoundTripPreservesACompleteAndAPartialCluster() {
        val file = emptyBackupFile().copy(
            formatVersion = CLUSTER_FORMAT_VERSION,
            catalog = BackupCatalogManifest(listOf("back-squat"), seedProfiles = emptyList()),
            workoutSessions = listOf(
                BackupWorkoutSessionRecord(
                    id = "s1",
                    startedAtMillis = 1L,
                    endedAtMillis = 2L,
                    localEpochDay = 1L,
                    occurrenceId = null
                )
            ),
            workoutSets = listOf(
                parent(1L, kind = "DROP_SET", planned = 3, performed = 3),
                parent(2L, kind = "REST_PAUSE", planned = 4, performed = 2)
            ),
            workoutClusterSegments = listOf(
                BackupClusterSegmentRecord(1L, 1, 10, 90.0, 1),
                BackupClusterSegmentRecord(1L, 2, 8, 80.0, 0),
                BackupClusterSegmentRecord(2L, 1, 6, 60.0, null)
            )
        )

        val restored = BackupJson.decode(BackupJson.encode(file))
        validator.validate(restored)

        assertEquals(CLUSTER_FORMAT_VERSION, restored.formatVersion)
        assertEquals(
            listOf("DROP_SET", "REST_PAUSE"),
            restored.workoutSets.map { it.clusterKind }
        )
        assertEquals(
            listOf(3 to 3, 4 to 2),
            restored.workoutSets.map {
                it.clusterPlannedSegments to it.clusterPerformedSegments
            }
        )
        assertEquals(
            listOf(1L to 1, 1L to 2, 2L to 1),
            restored.workoutClusterSegments.map { it.setId to it.position }
        )
        assertEquals(90.0, restored.workoutClusterSegments.first().weightKg)
        assertEquals(1, restored.workoutClusterSegments.first().rir)
    }

    private fun parent(id: Long, kind: String, planned: Int, performed: Int) =
        BackupWorkoutSetRecord(
            id = id,
            exerciseId = "back-squat",
            reps = 10,
            weightKg = 100.0,
            performedAtMillis = 10L + id,
            isWarmup = false,
            involvements = null,
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = "s1",
            occurrenceId = null,
            occurrenceEntryId = null,
            loadKind = LoadKind.EXTERNAL.name,
            clusterKind = kind,
            clusterPlannedSegments = planned,
            clusterPerformedSegments = performed
        )
}

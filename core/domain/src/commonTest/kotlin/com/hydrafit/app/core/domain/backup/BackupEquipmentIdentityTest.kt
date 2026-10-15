package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class BackupEquipmentIdentityTest {
    private val catalog = object : BackupCatalog {
        override fun seedExerciseIds(): Set<String> = emptySet()
        override fun builtInEquipmentIds(): Set<String> = setOf("EZ_BAR", "BARBELL")
    }
    private val preview = PreviewBackupUseCase(BackupValidator(catalog))

    @Test
    fun previewPreservesCustomOwnershipAndValuesAndIsIdempotent() = runTest {
        val input = collisionFile()
        val repaired = preview(BackupJson.encode(input))

        assertEquals("EZ_BAR_3", repaired.equipment.first().id)
        assertEquals(input.equipment.first().copy(id = "EZ_BAR_3"), repaired.equipment.first())
        assertEquals(listOf("EZ_BAR_3", "BARBELL"), repaired.selectedEquipment)
        assertEquals("EZ_BAR_3,BARBELL", repaired.customExercises.single().requiredEquipment)
        assertEquals("EZ_BAR_3", repaired.exerciseOverrides.single().requiredEquipment)
        assertEquals(repaired, preview(BackupJson.encode(repaired)))
        assertEquals("EZ_BAR", input.equipment.first().id)
    }

    @Test
    fun unknownSuffixReferenceCannotBecomeValidThroughRepair() = runTest {
        val input = collisionFile().copy(selectedEquipment = listOf("EZ_BAR_3"))
        assertEquals(
            BackupFailure.UNKNOWN_CATALOG_ID,
            assertFailsWith<BackupException> { preview(BackupJson.encode(input)) }.failure
        )
    }

    @Test
    fun duplicateEquipmentIsRejectedBeforeRepair() = runTest {
        val input = collisionFile()
        assertEquals(
            BackupFailure.DUPLICATE_ID,
            assertFailsWith<BackupException> {
                val duplicate = input.copy(equipment = input.equipment + input.equipment.first())
                preview(BackupJson.encode(duplicate))
            }.failure
        )
    }

    @Test
    fun unknownExerciseAndBrokenReferencesRemainRejected() = runTest {
        val input = collisionFile().copy(
            exerciseOverrides = listOf(
                collisionFile().exerciseOverrides.single().copy(exerciseId = "unknown")
            )
        )
        assertEquals(
            BackupFailure.UNKNOWN_CATALOG_ID,
            assertFailsWith<BackupException> { preview(BackupJson.encode(input)) }.failure
        )
        val dangling = collisionFile().copy(
            sessionNotes = listOf(BackupSessionNoteRecord("missing-session", "Synthetic note"))
        )
        assertEquals(
            BackupFailure.INVALID_REFERENCE,
            assertFailsWith<BackupException> { preview(BackupJson.encode(dangling)) }.failure
        )
    }

    @Test
    fun unchangedLegacyFileRetainsItsFormatAndDefaults() = runTest {
        val input = emptyBackupFile().copy(
            formatVersion = 1,
            restPreferences = emptyList(),
            equipmentProfiles = emptyList(),
            activeEquipmentProfileId = null
        )
        assertEquals(input, preview(BackupJson.encode(input)))
    }

    private fun collisionFile() = emptyBackupFile().copy(
        formatVersion = 13,
        equipmentProfiles = emptyList(),
        activeEquipmentProfileId = null,
        equipment = listOf(
            BackupEquipmentRecord("EZ_BAR", "My curved bar", false, 35.0),
            BackupEquipmentRecord("EZ_BAR_2", "Another bar", false, 20.0)
        ),
        selectedEquipment = listOf("EZ_BAR", "BARBELL"),
        customExercises = listOf(
            BackupExerciseRecord(
                "user-row",
                "My row",
                "EZ_BAR,BARBELL",
                "HORIZONTAL_PULL",
                false,
                "EXTERNAL",
                null
            )
        ),
        exerciseOverrides = listOf(
            BackupExerciseOverrideRecord("user-row", null, "EZ_BAR", null, null, null, null)
        )
    )
}

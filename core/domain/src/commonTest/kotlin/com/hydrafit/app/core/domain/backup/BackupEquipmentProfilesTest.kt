package com.hydrafit.app.core.domain.backup

import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.OwnedPlate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupEquipmentProfilesTest {
    private val catalog = object : BackupCatalog {
        override fun seedExerciseIds() = emptySet<String>()
        override fun builtInEquipmentIds() = setOf("BARBELL")
        override fun dedupeSeedKeys() = emptySet<String>()
    }
    private val validator = BackupValidator(catalog)

    private fun file(): BackupFile = emptyBackupFile().copy(
        formatVersion = 14,
        equipment = listOf(BackupEquipmentRecord("BARBELL", "Barbell", true, 120.0)),
        selectedEquipment = listOf("BARBELL"),
        activeEquipmentProfileId = 5L,
        equipmentProfiles = listOf(
            BackupEquipmentProfileRecord(
                5L,
                "Home",
                listOf("BARBELL"),
                listOf(BackupEquipmentCeilingRecord("BARBELL", 120.0)),
                LoadingInventory(plates = listOf(OwnedPlate(125, 3)))
            ),
            BackupEquipmentProfileRecord(7L, "Travel", emptyList(), emptyList(), LoadingInventory())
        )
    )

    @Test
    fun completeProfilesRoundTripAndLegacySectionsAreRejected() {
        val file = file()
        validator.validate(file)
        assertEquals(file, BackupJson.decode(BackupJson.encode(file)))
        val exception = assertFailsWith<BackupException> {
            validator.validate(file.copy(formatVersion = 13))
        }
        assertEquals(BackupFailure.INVALID_VALUE, exception.failure)
    }

    @Test
    fun missingProfilesOrActiveIdentityAndDisagreeingLegacyProjectionsAreRejected() {
        val file = file()
        val invalid = listOf(
            file.copy(equipmentProfiles = emptyList()),
            file.copy(activeEquipmentProfileId = null),
            file.copy(activeEquipmentProfileId = 99L),
            file.copy(equipmentProfiles = file.equipmentProfiles + file.equipmentProfiles.first()),
            file.copy(selectedEquipment = emptyList()),
            file.copy(equipment = file.equipment.map { it.copy(maxWeightKg = 99.0) }),
            file.copy(
                equipmentProfiles = file.equipmentProfiles.map {
                    it.copy(selectedEquipment = listOf("missing"))
                }
            ),
            file.copy(
                equipmentProfiles = file.equipmentProfiles.map {
                    it.copy(
                        loadingInventory = LoadingInventory(plates = listOf(OwnedPlate(1, 101)))
                    )
                }
            )
        )
        invalid.forEach { candidate ->
            assertFailsWith<BackupException> { validator.validate(candidate) }
        }
    }

    @Test
    fun profileSectionKeysAreRequiredAtTheFormatBoundary() {
        val text = BackupJson.encode(file())
        assertFailsWith<BackupException> {
            BackupJson.decode(text.replace(",\"activeEquipmentProfileId\":5", ""))
        }
        assertFailsWith<BackupException> {
            BackupJson.decode(text.replace("\"unit\":\"KG\",", ""))
        }
    }
}

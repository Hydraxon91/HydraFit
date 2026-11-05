package com.hydrafit.app.feature.equipment

import hydrafit.feature.equipment.generated.resources.Res
import hydrafit.feature.equipment.generated.resources.equipment_section_baselines_title
import hydrafit.feature.equipment.generated.resources.equipment_section_catalog_title
import hydrafit.feature.equipment.generated.resources.equipment_section_exclusions_title
import hydrafit.feature.equipment.generated.resources.equipment_section_inventory_title
import hydrafit.feature.equipment.generated.resources.equipment_section_profile_title
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The route manifest the Equipment graph is built from. These checks cover the manifest itself:
 * they do not prove runtime Back behavior or ViewModelStoreOwner sharing, which need an emulator
 * or navigation-instrumented check.
 */
class EquipmentNavigationTest {

    private val expectedTitles: Map<EquipmentSection, String> = mapOf(
        EquipmentSection.PROFILE_LOADING to Res.string.equipment_section_profile_title.key,
        EquipmentSection.INVENTORY_STATIONS to Res.string.equipment_section_inventory_title.key,
        EquipmentSection.EXERCISE_CATALOG to Res.string.equipment_section_catalog_title.key,
        EquipmentSection.EXCLUSIONS to Res.string.equipment_section_exclusions_title.key,
        EquipmentSection.BASELINE_RECORDS to Res.string.equipment_section_baselines_title.key
    )

    @Test
    fun hubAndSectionsHaveUniqueNestedRoutes() {
        assertEquals("equipment", equipmentRoute)
        assertEquals("equipment/home", equipmentHomeRoute)

        val routes = listOf(equipmentHomeRoute) + EquipmentSection.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size, "routes must not overlap")

        EquipmentSection.entries.forEach { section ->
            assertTrue(
                section.route.startsWith("$equipmentRoute/"),
                "${section.name} must be nested under $equipmentRoute"
            )
            assertTrue(section.route != equipmentHomeRoute && section.route != equipmentRoute)
        }
    }

    @Test
    fun eachSectionMapsToItsOwnTitle() {
        assertEquals(EquipmentSection.entries.toSet(), expectedTitles.keys)
        EquipmentSection.entries.forEach { section ->
            assertEquals(expectedTitles.getValue(section), section.title.key)
        }
    }

    @Test
    fun sectionMembershipDoesNotOverlap() {
        EquipmentSection.entries.forEach { section ->
            assertTrue(section.contentTag.isNotBlank())
            assertTrue(section.description.key.isNotBlank())
        }
        assertEquals(
            EquipmentSection.entries.size,
            EquipmentSection.entries.map { it.contentTag }.toSet().size,
            "each section must render exactly one distinct content group"
        )
        assertEquals(
            EquipmentSection.entries.size,
            EquipmentSection.entries.map { it.description.key }.toSet().size
        )
    }
}

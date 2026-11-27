package com.hydrafit.app.core.exercisecontent

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.setResourceReaderAndroidContext
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class EquipmentNameResourcesTest {

    @OptIn(ExperimentalResourceApi::class)
    @BeforeTest
    fun setUp() {
        setResourceReaderAndroidContext(RuntimeEnvironment.getApplication())
    }

    @Test
    fun everyBuiltInHasANameResourceAndCustomEquipmentHasNone() {
        EquipmentTag.BUILT_IN.forEach { tag ->
            assertNotNull(EquipmentNames.resourceFor(tag.id), "${tag.id} has no name resource")
        }
        assertNull(EquipmentNames.resourceFor("CUSTOM_HEX_BAR"))
    }

    @Test
    fun englishNamesAreTheStoredBuiltInNames() = runTest {
        assertEquals(EquipmentTag.BUILT_IN_NAMES, namesIn(Locale.ENGLISH))
    }

    @Test
    fun everyLanguageHasADistinctNameForEveryBuiltIn() = runTest {
        val english = namesIn(Locale.ENGLISH)
        TRANSLATED_LANGUAGES.forEach { tag ->
            val names = namesIn(Locale.forLanguageTag(tag))
            names.forEach { (id, name) -> assertTrue(name.isNotBlank(), "$tag: $id is blank") }
            val duplicates = names.values.groupBy {
                it.lowercase()
            }.filterValues { it.size > 1 }.keys
            assertTrue(duplicates.isEmpty(), "$tag repeats names: $duplicates")
            assertNotEquals(english, names, "$tag falls back to English")
        }
    }

    private suspend fun namesIn(locale: Locale): Map<String, String> {
        val previous = Locale.getDefault()
        return try {
            Locale.setDefault(locale)
            EquipmentTag.BUILT_IN_NAMES.keys.associateWith { id ->
                getString(requireNotNull(EquipmentNames.resourceFor(id)))
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    private companion object {
        val TRANSLATED_LANGUAGES = listOf("de", "cs", "hu", "nl")
    }
}

package com.hydrafit.app.core.userdata.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppLanguageTest {
    @Test
    fun theDeviceLanguageHasNoTagAndEveryOtherEntryHasADistinctOne() {
        assertNull(AppLanguage.SYSTEM.tag)
        val tags = AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.map { it.tag }
        assertEquals(tags.size, tags.toSet().size)
        assertEquals(listOf("en", "de", "cs", "hu", "nl"), tags)
    }

    @Test
    fun aStoredKeyRoundTripsAndAnUnknownOrMissingKeyReadsAsTheDeviceLanguage() {
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.fromKey(it.name)) }
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromKey(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromKey("KLINGON"))
    }

    @Test
    fun theInMemoryStoreStartsOnTheChoiceItWasGiven() {
        assertEquals(AppLanguage.SYSTEM, InMemoryAppLanguageStore().language.value)
        assertEquals(AppLanguage.CZECH, InMemoryAppLanguageStore(AppLanguage.CZECH).language.value)
    }
}

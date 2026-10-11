package com.hydrafit.app

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AppColorSchemeTest {

    @Test
    fun lightModePreservesTheDefaultMaterialPalette() {
        val expected = lightColorScheme()
        val actual = appColorScheme(darkTheme = false)
        assertEquals(expected.primary, actual.primary)
        assertEquals(expected.surface, actual.surface)
        assertEquals(expected.onSurface, actual.onSurface)
        assertEquals(expected.background, actual.background)
        assertEquals(expected.onBackground, actual.onBackground)
    }

    @Test
    fun darkModeUsesTheDarkMaterialPalette() {
        val expected = darkColorScheme()
        val actual = appColorScheme(darkTheme = true)
        assertEquals(expected.primary, actual.primary)
        assertEquals(expected.surface, actual.surface)
        assertEquals(expected.onSurface, actual.onSurface)
        assertEquals(expected.background, actual.background)
        assertEquals(expected.onBackground, actual.onBackground)
        assertNotEquals(
            appColorScheme(darkTheme = false).surface,
            appColorScheme(darkTheme = true).surface
        )
    }
}

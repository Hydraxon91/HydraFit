package com.hydrafit.app.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.hydrafit.app.core.userdata.settings.AppThemeId
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppPalettesTest {

    @Test
    fun everyThemeMeetsNormalTextContrastInBothVariants() {
        val failures = mutableListOf<String>()
        AppThemeId.entries.forEach { theme ->
            listOf(false, true).forEach { dark ->
                val scheme = appColorScheme(theme, dark)
                contentPairs(scheme).forEach { (foreground, background, name) ->
                    val ratio = contrast(foreground, background)
                    if (ratio < 4.5) failures += "$theme dark=$dark $name: $ratio"
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun outlinesAndPrimaryMarksStayVisibleOnOrdinarySurfaces() {
        AppThemeId.entries.forEach { theme ->
            listOf(false, true).forEach { dark ->
                val scheme = appColorScheme(theme, dark)
                listOf(scheme.background, scheme.surface, scheme.surfaceContainerHighest)
                    .forEach { surface ->
                        assertTrue(
                            contrast(scheme.outline, surface) >= 3.0,
                            "$theme dark=$dark outline"
                        )
                        assertTrue(
                            contrast(scheme.primary, surface) >= 3.0,
                            "$theme dark=$dark primary"
                        )
                    }
            }
        }
    }

    @Test
    fun defaultThemeKeepsTheOriginalQuietStrengthValues() {
        assertEquals(Color(0xFF79D5C5), appColorScheme(AppThemeId.QUIET_STRENGTH, true).primary)
        assertEquals(Color(0xFF182326), appColorScheme(AppThemeId.QUIET_STRENGTH, true).background)
        assertEquals(Color(0xFF176B63), appColorScheme(AppThemeId.QUIET_STRENGTH, false).primary)
        assertEquals(Color(0xFFFAFAF8), appColorScheme(AppThemeId.QUIET_STRENGTH, false).background)
    }

    @Test
    fun everyThemeLooksDifferentFromTheOthers() {
        listOf(false, true).forEach { dark ->
            val primaries = AppThemeId.entries.map { appColorScheme(it, dark).primary }
            assertEquals(primaries.size, primaries.toSet().size, "dark=$dark")
            AppThemeId.entries.filter { it != AppThemeId.QUIET_STRENGTH }.forEach { theme ->
                assertNotEquals(
                    appColorScheme(AppThemeId.QUIET_STRENGTH, dark).background,
                    appColorScheme(theme, dark).background,
                    "$theme dark=$dark background"
                )
            }
        }
    }

    private data class Pair3(val foreground: Color, val background: Color, val name: String)

    private fun contentPairs(scheme: ColorScheme): List<Pair3> {
        val pairs = mutableListOf(
            Pair3(scheme.onPrimary, scheme.primary, "onPrimary"),
            Pair3(scheme.onPrimaryContainer, scheme.primaryContainer, "onPrimaryContainer"),
            Pair3(scheme.onSecondary, scheme.secondary, "onSecondary"),
            Pair3(scheme.onSecondaryContainer, scheme.secondaryContainer, "onSecondaryContainer"),
            Pair3(scheme.onTertiary, scheme.tertiary, "onTertiary"),
            Pair3(scheme.onTertiaryContainer, scheme.tertiaryContainer, "onTertiaryContainer"),
            Pair3(scheme.onBackground, scheme.background, "onBackground"),
            Pair3(scheme.onSurfaceVariant, scheme.surfaceVariant, "onSurfaceVariant"),
            Pair3(scheme.onError, scheme.error, "onError"),
            Pair3(scheme.onErrorContainer, scheme.errorContainer, "onErrorContainer"),
            Pair3(scheme.inverseOnSurface, scheme.inverseSurface, "inverseOnSurface"),
            Pair3(scheme.inversePrimary, scheme.inverseSurface, "inversePrimary"),
            Pair3(scheme.onPrimaryFixed, scheme.primaryFixed, "onPrimaryFixed"),
            Pair3(scheme.onPrimaryFixedVariant, scheme.primaryFixed, "onPrimaryFixedVariant"),
            Pair3(scheme.onSecondaryFixed, scheme.secondaryFixed, "onSecondaryFixed"),
            Pair3(scheme.onSecondaryFixedVariant, scheme.secondaryFixed, "onSecondaryFixedVariant"),
            Pair3(scheme.onTertiaryFixed, scheme.tertiaryFixed, "onTertiaryFixed"),
            Pair3(scheme.onTertiaryFixedVariant, scheme.tertiaryFixed, "onTertiaryFixedVariant")
        )
        listOf(
            scheme.surface,
            scheme.surfaceBright,
            scheme.surfaceDim,
            scheme.surfaceContainerLowest,
            scheme.surfaceContainerLow,
            scheme.surfaceContainer,
            scheme.surfaceContainerHigh,
            scheme.surfaceContainerHighest
        ).forEach { surface ->
            pairs += Pair3(scheme.onSurface, surface, "onSurface")
            pairs += Pair3(scheme.onSurfaceVariant, surface, "onSurfaceVariant on surface")
        }
        return pairs
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}

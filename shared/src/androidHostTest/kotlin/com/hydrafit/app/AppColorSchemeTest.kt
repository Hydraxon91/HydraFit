package com.hydrafit.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppColorSchemeTest {

    @Test
    fun lightModeUsesQuietStrengthIdentity() {
        val actual = appColorScheme(darkTheme = false)
        assertEquals(Color(0xFF176B63), actual.primary)
        assertEquals(Color(0xFFFFFFFF), actual.onPrimary)
        assertEquals(Color(0xFFFAFAF8), actual.background)
        assertEquals(Color(0xFFF0F3F1), actual.surface)
        assertEquals(Color(0xFFE3E9E6), actual.surfaceVariant)
        assertEquals(Color(0xFF182326), actual.onSurface)
        assertEquals(Color(0xFF4B5C5D), actual.onSurfaceVariant)
        assertEquals(Color(0xFF748381), actual.outline)
    }

    @Test
    fun darkModeUsesQuietStrengthIdentity() {
        val actual = appColorScheme(darkTheme = true)
        assertEquals(Color(0xFF79D5C5), actual.primary)
        assertEquals(Color(0xFF073B35), actual.onPrimary)
        assertEquals(Color(0xFF182326), actual.background)
        assertEquals(Color(0xFF223135), actual.surface)
        assertEquals(Color(0xFF2B3B3F), actual.surfaceVariant)
        assertEquals(Color(0xFFF2F6F4), actual.onSurface)
        assertEquals(Color(0xFFBAC9C5), actual.onSurfaceVariant)
        assertEquals(Color(0xFF839894), actual.outline)
        assertNotEquals(
            appColorScheme(darkTheme = false).surface,
            appColorScheme(darkTheme = true).surface
        )
    }

    @Test
    fun bothThemesMeetNormalTextContrastAcrossTheirContentRoles() {
        listOf(false, true).forEach { dark ->
            val scheme = appColorScheme(dark)
            val pairs = listOf(
                scheme.onPrimary to scheme.primary,
                scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onSecondary to scheme.secondary,
                scheme.onSecondaryContainer to scheme.secondaryContainer,
                scheme.onTertiary to scheme.tertiary,
                scheme.onTertiaryContainer to scheme.tertiaryContainer,
                scheme.onBackground to scheme.background,
                scheme.onSurfaceVariant to scheme.surfaceVariant,
                scheme.onError to scheme.error,
                scheme.onErrorContainer to scheme.errorContainer,
                scheme.inverseOnSurface to scheme.inverseSurface,
                scheme.inversePrimary to scheme.inverseSurface,
                scheme.onPrimaryFixed to scheme.primaryFixed,
                scheme.onPrimaryFixedVariant to scheme.primaryFixed,
                scheme.onSecondaryFixed to scheme.secondaryFixed,
                scheme.onSecondaryFixedVariant to scheme.secondaryFixed,
                scheme.onTertiaryFixed to scheme.tertiaryFixed,
                scheme.onTertiaryFixedVariant to scheme.tertiaryFixed
            ) + listOf(
                scheme.surface,
                scheme.surfaceBright,
                scheme.surfaceDim,
                scheme.surfaceContainerLowest,
                scheme.surfaceContainerLow,
                scheme.surfaceContainer,
                scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest
            ).flatMap { surface ->
                listOf(scheme.onSurface to surface, scheme.onSurfaceVariant to surface)
            }
            pairs.forEach { (foreground, background) ->
                assertTrue(
                    contrast(foreground, background) >= 4.5,
                    "dark=$dark: $foreground on $background"
                )
            }
        }
    }

    @Test
    fun fieldOutlinesAndChartMarksRemainVisibleOnOrdinarySurfaces() {
        listOf(false, true).forEach { dark ->
            val scheme = appColorScheme(dark)
            listOf(
                scheme.background,
                scheme.surface,
                scheme.surfaceContainerHighest
            ).forEach { surface ->
                assertTrue(contrast(scheme.outline, surface) >= 3.0, "outline, dark=$dark")
                assertTrue(contrast(scheme.primary, surface) >= 3.0, "primary, dark=$dark")
            }
        }
    }

    @Test
    fun typographyKeepsReadableBodyAndStrongScreenHeadings() {
        assertEquals(24.sp, appTypography.headlineSmall.fontSize)
        assertEquals(FontWeight.Bold, appTypography.headlineSmall.fontWeight)
        assertEquals(16.sp, appTypography.bodyMedium.fontSize)
        assertEquals(14.sp, appTypography.bodySmall.fontSize)
        assertEquals(40.sp, appTypography.displayMedium.fontSize)
    }

    @Test
    fun shapesKeepModestFieldsAndGroupedSurfaces() {
        assertEquals(RoundedCornerShape(8.dp), appShapes.small)
        assertEquals(RoundedCornerShape(12.dp), appShapes.medium)
        assertEquals(RoundedCornerShape(12.dp), appShapes.large)
    }

    @Test
    fun fixedRolesStayTealAcrossThemesInsteadOfMaterialDefaults() {
        val light = appColorScheme(darkTheme = false)
        val dark = appColorScheme(darkTheme = true)
        assertEquals(Color(0xFFD3E9E3), light.primaryFixed)
        assertEquals(Color(0xFF79D5C5), light.primaryFixedDim)
        assertEquals(Color(0xFF073B35), light.onPrimaryFixed)
        listOf(
            light.primaryFixed to dark.primaryFixed,
            light.primaryFixedDim to dark.primaryFixedDim,
            light.onPrimaryFixed to dark.onPrimaryFixed,
            light.onPrimaryFixedVariant to dark.onPrimaryFixedVariant,
            light.secondaryFixed to dark.secondaryFixed,
            light.secondaryFixedDim to dark.secondaryFixedDim,
            light.onSecondaryFixed to dark.onSecondaryFixed,
            light.onSecondaryFixedVariant to dark.onSecondaryFixedVariant,
            light.tertiaryFixed to dark.tertiaryFixed,
            light.tertiaryFixedDim to dark.tertiaryFixedDim,
            light.onTertiaryFixed to dark.onTertiaryFixed,
            light.onTertiaryFixedVariant to dark.onTertiaryFixedVariant
        ).forEach { (lightRole, darkRole) ->
            assertEquals(lightRole, darkRole, "fixed roles are theme-invariant")
        }

        val defaults = lightColorScheme()
        assertNotEquals(defaults.primaryFixed, light.primaryFixed)
        assertNotEquals(defaults.onPrimaryFixedVariant, light.onPrimaryFixedVariant)
        assertNotEquals(defaults.tertiaryFixed, light.tertiaryFixed)
    }

    private fun contrast(first: Color, second: Color): Double {
        val a = first.luminance().toDouble()
        val b = second.luminance().toDouble()
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}

package com.hydrafit.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun compactBarKeepsOneRowWhenMeasuredLabelsFit() {
        // Approximate normal-scale measured widths for Train/Progress/Library/Settings.
        assertFalse(navigationRequiresTwoRows(393f, listOf(38f, 62f, 48f, 60f)))
    }

    @Test
    fun compactBarUsesTwoRowsWhenFullLabelsOverflowAtLargeText() {
        assertTrue(navigationRequiresTwoRows(393f, listOf(64f, 142f, 78f, 132f)))
    }

    @Test
    fun unequalLabelsAreCheckedAgainstEqualPerItemAllocation() {
        // Summed widths fit below 393, but Progress/Settings exceed the 92.25dp equal allocation.
        assertTrue(navigationRequiresTwoRows(393f, listOf(50f, 120f, 70f, 110f)))
    }

    @Test
    fun boundaryUsesEqualAllocationWithRealInterItemSpacing() {
        // (4*90)+24 = 384.
        assertFalse(navigationRequiresTwoRows(384f, listOf(90f, 90f, 90f, 90f)))
        assertTrue(navigationRequiresTwoRows(383.9f, listOf(90f, 90f, 90f, 90f)))
        // Four narrow labels still reserve the 48dp minimum per item: (4*48)+24 = 216.
        assertTrue(navigationRequiresTwoRows(215.9f, listOf(10f, 10f, 10f, 10f)))
        assertFalse(navigationRequiresTwoRows(216f, listOf(10f, 10f, 10f, 10f)))
    }

    @Test
    fun emptyLabelsNeverForceTwoRows() {
        assertFalse(navigationRequiresTwoRows(0f, emptyList()))
    }

    @Test
    fun reselectionFallsBackToTheTabRootWhenNoExistingRootPops() {
        assertEquals(
            TabReselectionAction.POP_TO_ROOT,
            tabReselectionAction(true, "equipment", "routines/home")
        )
        assertEquals(
            TabReselectionAction.NONE,
            tabReselectionAction(true, "routines/home", "routines/home")
        )
        assertEquals(
            TabReselectionAction.NAVIGATE_TO_ROOT,
            tabReselectionAction(false, "equipment", "routines/home")
        )
        // Progress is a leaf start route with pushed children: root means "progress".
        assertEquals(
            TabReselectionAction.POP_TO_ROOT,
            tabReselectionAction(true, "progress/bodyweight", "progress")
        )
        assertEquals(
            TabReselectionAction.NONE,
            tabReselectionAction(true, "progress", "progress")
        )
        assertEquals(
            TabReselectionAction.NONE,
            tabReselectionAfterFailedPop(popSucceeded = true)
        )
        assertEquals(
            TabReselectionAction.NAVIGATE_TO_ROOT,
            tabReselectionAfterFailedPop(popSucceeded = false)
        )
    }

    @Test
    fun imeVisibleDropsOnlyTheBottomBarInset() {
        val inner = PaddingValues(start = 4.dp, top = 12.dp, end = 6.dp, bottom = 80.dp)
        val hidden = contentPaddingWithIme(inner, imeVisible = false, LayoutDirection.Ltr)
        assertEquals(80.dp, hidden.calculateBottomPadding())

        val visible = contentPaddingWithIme(inner, imeVisible = true, LayoutDirection.Ltr)
        assertEquals(0.dp, visible.calculateBottomPadding())
        assertEquals(4.dp, visible.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(12.dp, visible.calculateTopPadding())
        assertEquals(6.dp, visible.calculateEndPadding(LayoutDirection.Ltr))
    }

    @Test
    fun imeVisiblePreservesRtlHorizontalPadding() {
        val inner = PaddingValues(start = 10.dp, top = 2.dp, end = 3.dp, bottom = 20.dp)
        val visible = contentPaddingWithIme(inner, imeVisible = true, LayoutDirection.Rtl)
        assertEquals(
            inner.calculateLeftPadding(LayoutDirection.Rtl),
            visible.calculateLeftPadding(LayoutDirection.Rtl)
        )
        assertEquals(
            inner.calculateRightPadding(LayoutDirection.Rtl),
            visible.calculateRightPadding(LayoutDirection.Rtl)
        )
        assertEquals(2.dp, visible.calculateTopPadding())
        assertEquals(0.dp, visible.calculateBottomPadding())
    }

    @Test
    fun bundledFontFamilyIsAppliedToEveryTypographyRole() {
        val family = FontFamily.SansSerif
        val branded = withFontFamily(appTypography, family)
        listOf(
            branded.displayLarge,
            branded.displayMedium,
            branded.displaySmall,
            branded.headlineLarge,
            branded.headlineMedium,
            branded.headlineSmall,
            branded.titleLarge,
            branded.titleMedium,
            branded.titleSmall,
            branded.bodyLarge,
            branded.bodyMedium,
            branded.bodySmall,
            branded.labelLarge,
            branded.labelMedium,
            branded.labelSmall
        ).forEach { role ->
            assertEquals(family, role.fontFamily)
        }
        // The family swap must not alter the Quiet Strength size and weight scale.
        assertEquals(24.sp, branded.headlineSmall.fontSize)
        assertEquals(FontWeight.Bold, branded.headlineSmall.fontWeight)
        assertEquals(40.sp, branded.displayMedium.fontSize)
        assertEquals(16.sp, branded.bodyMedium.fontSize)
    }

    @Test
    fun workoutHeroAndReversedActionKeepTextContrast() {
        listOf(false, true).forEach { dark ->
            val scheme = appColorScheme(dark)
            assertTrue(
                contrast(scheme.onPrimary, scheme.primary) >= 4.5,
                "hero foreground, dark=$dark"
            )
            assertTrue(
                contrast(scheme.primary, scheme.onPrimary) >= 4.5,
                "reversed start action label, dark=$dark"
            )
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

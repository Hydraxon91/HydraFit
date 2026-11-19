package com.hydrafit.app.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.hydrafit.app.core.userdata.settings.AppThemeId
import kotlin.math.abs

/**
 * The colour scheme for [theme] in the light or dark variant. Every theme fills the same Material
 * roles, so no screen needs to know which one is active; contrast of the content roles is held by
 * a test for every theme and both variants.
 */
fun appColorScheme(theme: AppThemeId, darkTheme: Boolean): ColorScheme = when (theme) {
    AppThemeId.QUIET_STRENGTH -> quietStrengthScheme(darkTheme)
    else -> derivedScheme(requireNotNull(derivedHues[theme]), darkTheme)
}

/**
 * Fixed roles stay theme-invariant by Material contract, so both schemes share the Quiet Strength
 * tonal values instead of the default palette's purple.
 */
private val fixedPrimary = Color(0xFFD3E9E3)
private val fixedPrimaryDim = Color(0xFF79D5C5)
private val fixedOnPrimary = Color(0xFF073B35)
private val fixedOnPrimaryVariant = Color(0xFF176B63)
private val fixedSecondary = Color(0xFFDCE9E2)
private val fixedSecondaryDim = Color(0xFFBAC9C5)
private val fixedOnSecondary = Color(0xFF203B34)
private val fixedOnSecondaryVariant = Color(0xFF48615B)
private val fixedTertiary = Color(0xFFD2E9FA)
private val fixedTertiaryDim = Color(0xFFB3CDDE)
private val fixedOnTertiary = Color(0xFF173342)
private val fixedOnTertiaryVariant = Color(0xFF3F6073)

/** The original Quiet Strength look, kept value for value as the default theme. */
private fun quietStrengthScheme(darkTheme: Boolean): ColorScheme = if (darkTheme) {
    darkColorScheme(
        primary = Color(0xFF79D5C5),
        onPrimary = Color(0xFF073B35),
        primaryContainer = Color(0xFF234D48),
        onPrimaryContainer = Color(0xFFB9F1E6),
        inversePrimary = Color(0xFF176B63),
        secondary = Color(0xFFBAC9C5),
        onSecondary = Color(0xFF223135),
        secondaryContainer = Color(0xFF354B46),
        onSecondaryContainer = Color(0xFFE0EEE8),
        tertiary = Color(0xFFB3CDDE),
        onTertiary = Color(0xFF173342),
        tertiaryContainer = Color(0xFF304B5B),
        onTertiaryContainer = Color(0xFFD2E9FA),
        background = Color(0xFF182326),
        onBackground = Color(0xFFF2F6F4),
        surface = Color(0xFF223135),
        onSurface = Color(0xFFF2F6F4),
        surfaceVariant = Color(0xFF2B3B3F),
        onSurfaceVariant = Color(0xFFBAC9C5),
        surfaceTint = Color(0xFF79D5C5),
        inverseSurface = Color(0xFFE3E9E6),
        inverseOnSurface = Color(0xFF182326),
        outline = Color(0xFF839894),
        outlineVariant = Color(0xFF657A75),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        scrim = Color.Black,
        surfaceBright = Color(0xFF3B4B4E),
        surfaceDim = Color(0xFF182326),
        surfaceContainerLowest = Color(0xFF121C1F),
        surfaceContainerLow = Color(0xFF1C2A2D),
        surfaceContainer = Color(0xFF223135),
        surfaceContainerHigh = Color(0xFF2B3B3F),
        surfaceContainerHighest = Color(0xFF35464A),
        primaryFixed = fixedPrimary,
        primaryFixedDim = fixedPrimaryDim,
        onPrimaryFixed = fixedOnPrimary,
        onPrimaryFixedVariant = fixedOnPrimaryVariant,
        secondaryFixed = fixedSecondary,
        secondaryFixedDim = fixedSecondaryDim,
        onSecondaryFixed = fixedOnSecondary,
        onSecondaryFixedVariant = fixedOnSecondaryVariant,
        tertiaryFixed = fixedTertiary,
        tertiaryFixedDim = fixedTertiaryDim,
        onTertiaryFixed = fixedOnTertiary,
        onTertiaryFixedVariant = fixedOnTertiaryVariant
    )
} else {
    lightColorScheme(
        primary = Color(0xFF176B63),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD3E9E3),
        onPrimaryContainer = Color(0xFF124D47),
        inversePrimary = Color(0xFF79D5C5),
        secondary = Color(0xFF48615B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDCE9E2),
        onSecondaryContainer = Color(0xFF203B34),
        tertiary = Color(0xFF3F6073),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD2E9FA),
        onTertiaryContainer = Color(0xFF173342),
        background = Color(0xFFFAFAF8),
        onBackground = Color(0xFF182326),
        surface = Color(0xFFF0F3F1),
        onSurface = Color(0xFF182326),
        surfaceVariant = Color(0xFFE3E9E6),
        onSurfaceVariant = Color(0xFF4B5C5D),
        surfaceTint = Color(0xFF176B63),
        inverseSurface = Color(0xFF223135),
        inverseOnSurface = Color(0xFFF2F6F4),
        outline = Color(0xFF748381),
        outlineVariant = Color(0xFF8B9995),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        scrim = Color.Black,
        surfaceBright = Color(0xFFFAFAF8),
        surfaceDim = Color(0xFFD7DEDA),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF5F7F4),
        surfaceContainer = Color(0xFFF0F3F1),
        surfaceContainerHigh = Color(0xFFE9EEEA),
        surfaceContainerHighest = Color(0xFFE3E9E6),
        primaryFixed = fixedPrimary,
        primaryFixedDim = fixedPrimaryDim,
        onPrimaryFixed = fixedOnPrimary,
        onPrimaryFixedVariant = fixedOnPrimaryVariant,
        secondaryFixed = fixedSecondary,
        secondaryFixedDim = fixedSecondaryDim,
        onSecondaryFixed = fixedOnSecondary,
        onSecondaryFixedVariant = fixedOnSecondaryVariant,
        tertiaryFixed = fixedTertiary,
        tertiaryFixedDim = fixedTertiaryDim,
        onTertiaryFixed = fixedOnTertiary,
        onTertiaryFixedVariant = fixedOnTertiaryVariant
    )
}

/**
 * Three accent hues and one neutral tint. Everything else is derived by fixed lightness steps,
 * which is what keeps the content roles legible whatever the hue.
 */
private class ThemeHues(
    val primary: Float,
    val primarySat: Float,
    val secondary: Float,
    val secondarySat: Float,
    val tertiary: Float,
    val tertiarySat: Float,
    val neutral: Float,
    val neutralSat: Float
)

private val derivedHues: Map<AppThemeId, ThemeHues> = mapOf(
    AppThemeId.MATA_FIRE to ThemeHues(6f, 0.78f, 26f, 0.62f, 44f, 0.70f, 8f, 0.34f),
    AppThemeId.MATA_WATER to ThemeHues(214f, 0.72f, 196f, 0.52f, 232f, 0.50f, 214f, 0.38f),
    AppThemeId.MATA_AIR to ThemeHues(112f, 0.58f, 150f, 0.42f, 78f, 0.55f, 105f, 0.26f),
    AppThemeId.MATA_EARTH to ThemeHues(208f, 0.16f, 220f, 0.12f, 24f, 0.50f, 215f, 0.07f),
    AppThemeId.MATA_STONE to ThemeHues(28f, 0.50f, 40f, 0.34f, 14f, 0.46f, 30f, 0.30f),
    AppThemeId.MATA_ICE to ThemeHues(200f, 0.42f, 212f, 0.26f, 186f, 0.52f, 202f, 0.30f),
    AppThemeId.MAKUTA to ThemeHues(282f, 0.56f, 268f, 0.30f, 98f, 0.68f, 280f, 0.34f),
    AppThemeId.GOLD_NUVA to ThemeHues(45f, 0.86f, 38f, 0.42f, 212f, 0.38f, 42f, 0.32f)
)

private fun derivedScheme(t: ThemeHues, darkTheme: Boolean): ColorScheme {
    val p = t.primary
    val ps = t.primarySat
    val s = t.secondary
    val ss = t.secondarySat
    val a = t.tertiary
    val ast = t.tertiarySat
    val n = t.neutral
    val ns = t.neutralSat
    return if (darkTheme) {
        darkColorScheme(
            primary = hsl(p, ps, 0.72f),
            onPrimary = hsl(p, ps, 0.14f),
            primaryContainer = hsl(p, ps * 0.6f, 0.26f),
            onPrimaryContainer = hsl(p, ps * 0.8f, 0.90f),
            inversePrimary = hsl(p, ps, 0.24f),
            secondary = hsl(s, ss, 0.74f),
            onSecondary = hsl(s, ss, 0.14f),
            secondaryContainer = hsl(s, ss * 0.6f, 0.26f),
            onSecondaryContainer = hsl(s, ss * 0.8f, 0.90f),
            tertiary = hsl(a, ast, 0.74f),
            onTertiary = hsl(a, ast, 0.14f),
            tertiaryContainer = hsl(a, ast * 0.6f, 0.26f),
            onTertiaryContainer = hsl(a, ast * 0.8f, 0.90f),
            background = hsl(n, ns, 0.09f),
            onBackground = hsl(n, ns, 0.94f),
            surface = hsl(n, ns, 0.13f),
            onSurface = hsl(n, ns, 0.94f),
            surfaceVariant = hsl(n, ns, 0.18f),
            onSurfaceVariant = hsl(n, ns, 0.76f),
            surfaceTint = hsl(p, ps, 0.72f),
            inverseSurface = hsl(n, ns, 0.90f),
            inverseOnSurface = hsl(n, ns, 0.10f),
            outline = hsl(n, ns, 0.58f),
            outlineVariant = hsl(n, ns, 0.40f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            scrim = Color.Black,
            surfaceBright = hsl(n, ns, 0.26f),
            surfaceDim = hsl(n, ns, 0.09f),
            surfaceContainerLowest = hsl(n, ns, 0.07f),
            surfaceContainerLow = hsl(n, ns, 0.11f),
            surfaceContainer = hsl(n, ns, 0.13f),
            surfaceContainerHigh = hsl(n, ns, 0.18f),
            surfaceContainerHighest = hsl(n, ns, 0.22f),
            primaryFixed = hsl(p, ps * 0.6f, 0.90f),
            primaryFixedDim = hsl(p, ps * 0.7f, 0.72f),
            onPrimaryFixed = hsl(p, ps, 0.15f),
            onPrimaryFixedVariant = hsl(p, ps, 0.26f),
            secondaryFixed = hsl(s, ss * 0.6f, 0.90f),
            secondaryFixedDim = hsl(s, ss * 0.7f, 0.72f),
            onSecondaryFixed = hsl(s, ss, 0.15f),
            onSecondaryFixedVariant = hsl(s, ss, 0.26f),
            tertiaryFixed = hsl(a, ast * 0.6f, 0.90f),
            tertiaryFixedDim = hsl(a, ast * 0.7f, 0.72f),
            onTertiaryFixed = hsl(a, ast, 0.15f),
            onTertiaryFixedVariant = hsl(a, ast, 0.26f)
        )
    } else {
        lightColorScheme(
            primary = hsl(p, ps, 0.28f),
            onPrimary = Color.White,
            primaryContainer = hsl(p, ps * 0.6f, 0.88f),
            onPrimaryContainer = hsl(p, ps, 0.16f),
            inversePrimary = hsl(p, ps, 0.74f),
            secondary = hsl(s, ss, 0.30f),
            onSecondary = Color.White,
            secondaryContainer = hsl(s, ss * 0.6f, 0.90f),
            onSecondaryContainer = hsl(s, ss, 0.16f),
            tertiary = hsl(a, ast, 0.30f),
            onTertiary = Color.White,
            tertiaryContainer = hsl(a, ast * 0.6f, 0.88f),
            onTertiaryContainer = hsl(a, ast, 0.16f),
            background = hsl(n, ns, 0.975f),
            onBackground = hsl(n, ns, 0.10f),
            surface = hsl(n, ns, 0.955f),
            onSurface = hsl(n, ns, 0.10f),
            surfaceVariant = hsl(n, ns, 0.91f),
            onSurfaceVariant = hsl(n, ns, 0.28f),
            surfaceTint = hsl(p, ps, 0.28f),
            inverseSurface = hsl(n, ns, 0.16f),
            inverseOnSurface = hsl(n, ns, 0.95f),
            outline = hsl(n, ns, 0.40f),
            outlineVariant = hsl(n, ns, 0.58f),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            scrim = Color.Black,
            surfaceBright = hsl(n, ns, 0.975f),
            surfaceDim = hsl(n, ns, 0.86f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hsl(n, ns, 0.965f),
            surfaceContainer = hsl(n, ns, 0.95f),
            surfaceContainerHigh = hsl(n, ns, 0.93f),
            surfaceContainerHighest = hsl(n, ns, 0.91f),
            primaryFixed = hsl(p, ps * 0.6f, 0.90f),
            primaryFixedDim = hsl(p, ps * 0.7f, 0.72f),
            onPrimaryFixed = hsl(p, ps, 0.15f),
            onPrimaryFixedVariant = hsl(p, ps, 0.26f),
            secondaryFixed = hsl(s, ss * 0.6f, 0.90f),
            secondaryFixedDim = hsl(s, ss * 0.7f, 0.72f),
            onSecondaryFixed = hsl(s, ss, 0.15f),
            onSecondaryFixedVariant = hsl(s, ss, 0.26f),
            tertiaryFixed = hsl(a, ast * 0.6f, 0.90f),
            tertiaryFixedDim = hsl(a, ast * 0.7f, 0.72f),
            onTertiaryFixed = hsl(a, ast, 0.15f),
            onTertiaryFixedVariant = hsl(a, ast, 0.26f)
        )
    }
}

/** Standard HSL-to-RGB conversion; [hue] in degrees, [saturation] and [lightness] in 0..1. */
private fun hsl(hue: Float, saturation: Float, lightness: Float): Color {
    val chroma = (1f - abs(2f * lightness - 1f)) * saturation
    val x = chroma * (1f - abs((hue / 60f) % 2f - 1f))
    val m = lightness - chroma / 2f
    val (r, g, b) = when {
        hue < 60f -> Triple(chroma, x, 0f)
        hue < 120f -> Triple(x, chroma, 0f)
        hue < 180f -> Triple(0f, chroma, x)
        hue < 240f -> Triple(0f, x, chroma)
        hue < 300f -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

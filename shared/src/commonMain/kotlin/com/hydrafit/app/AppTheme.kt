package com.hydrafit.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

/** Quiet Strength roles are shared through MaterialTheme, never feature-to-shell dependencies. */
internal fun appColorScheme(darkTheme: Boolean): ColorScheme = if (darkTheme) {
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

internal val appTypography: Typography = Typography().let { defaults ->
    defaults.copy(
        displayMedium = defaults.displayMedium.copy(fontSize = 40.sp, lineHeight = 48.sp),
        displaySmall = defaults.displaySmall.copy(fontSize = 32.sp, lineHeight = 40.sp),
        headlineSmall = defaults.headlineSmall.copy(
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold
        ),
        titleLarge = defaults.titleLarge.copy(
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold
        ),
        titleMedium = defaults.titleMedium.copy(
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold
        ),
        bodyLarge = defaults.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = defaults.bodyMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodySmall = defaults.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = defaults.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = defaults.labelMedium.copy(fontSize = 14.sp, lineHeight = 20.sp)
    )
}

internal val appShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

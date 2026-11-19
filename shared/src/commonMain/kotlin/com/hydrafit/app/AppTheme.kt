package com.hydrafit.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrafit.app.core.ui.appColorScheme
import com.hydrafit.app.core.userdata.settings.AppThemeId
import hydrafit.shared.generated.resources.Res
import hydrafit.shared.generated.resources.inter_bold
import hydrafit.shared.generated.resources.inter_regular
import hydrafit.shared.generated.resources.inter_semibold
import org.jetbrains.compose.resources.Font

/** The default Quiet Strength scheme; the full theme set lives in `:core:ui`. */
internal fun appColorScheme(darkTheme: Boolean): ColorScheme =
    appColorScheme(AppThemeId.QUIET_STRENGTH, darkTheme)

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

/** The bundled Inter faces, loaded from compose resources so the app never fetches a font. */
@Composable
internal fun appFontFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold)
)

/**
 * Applies [family] to every Material typography role. Pure so a host test can prove the family
 * reaches all roles without instantiating the composable resource loader.
 */
internal fun withFontFamily(typography: Typography, family: FontFamily): Typography =
    typography.copy(
        displayLarge = typography.displayLarge.copy(fontFamily = family),
        displayMedium = typography.displayMedium.copy(fontFamily = family),
        displaySmall = typography.displaySmall.copy(fontFamily = family),
        headlineLarge = typography.headlineLarge.copy(fontFamily = family),
        headlineMedium = typography.headlineMedium.copy(fontFamily = family),
        headlineSmall = typography.headlineSmall.copy(fontFamily = family),
        titleLarge = typography.titleLarge.copy(fontFamily = family),
        titleMedium = typography.titleMedium.copy(fontFamily = family),
        titleSmall = typography.titleSmall.copy(fontFamily = family),
        bodyLarge = typography.bodyLarge.copy(fontFamily = family),
        bodyMedium = typography.bodyMedium.copy(fontFamily = family),
        bodySmall = typography.bodySmall.copy(fontFamily = family),
        labelLarge = typography.labelLarge.copy(fontFamily = family),
        labelMedium = typography.labelMedium.copy(fontFamily = family),
        labelSmall = typography.labelSmall.copy(fontFamily = family)
    )

internal val appShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

package app.pwhs.universalantisplit.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.pwhs.universalantisplit.domain.AppThemePreset

val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
)

val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
)

val OrangeLightColorScheme = LightColorScheme
val OrangeDarkColorScheme = DarkColorScheme

val BlueLightColorScheme = LightColorScheme.copy(
    primary = Color(0xFF0284C7),
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF0F766E),
    secondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFF6366F1),
)

val BlueDarkColorScheme = DarkColorScheme.copy(
    primary = Color(0xFF38BDF8),
    primaryContainer = Color(0xFF0369A1),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF2DD4BF),
    secondaryContainer = Color(0xFF0F766E),
    tertiary = Color(0xFF818CF8),
)

val GreenLightColorScheme = LightColorScheme.copy(
    primary = Color(0xFF16A34A),
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF14532D),
    secondary = Color(0xFF0891B2),
    secondaryContainer = Color(0xFFECFEFF),
    tertiary = Color(0xFFCA8A04),
)

val GreenDarkColorScheme = DarkColorScheme.copy(
    primary = Color(0xFF4ADE80),
    primaryContainer = Color(0xFF15803D),
    onPrimaryContainer = Color(0xFFDCFCE7),
    secondary = Color(0xFF22D3EE),
    secondaryContainer = Color(0xFF0891B2),
    tertiary = Color(0xFFFDE047),
)

val RedLightColorScheme = LightColorScheme.copy(
    primary = Color(0xFFDC2626),
    primaryContainer = Color(0xFFFEE2E2),
    onPrimaryContainer = Color(0xFF7F1D1D),
    secondary = Color(0xFFD97706),
    secondaryContainer = Color(0xFFFEF3C7),
    tertiary = Color(0xFF2563EB),
)

val RedDarkColorScheme = DarkColorScheme.copy(
    primary = Color(0xFFFCA5A5),
    primaryContainer = Color(0xFFB91C1C),
    onPrimaryContainer = Color(0xFFFEE2E2),
    secondary = Color(0xFFF59E0B),
    secondaryContainer = Color(0xFF78350F),
    tertiary = Color(0xFF60A5FA),
)

val PurpleLightColorScheme = LightColorScheme.copy(
    primary = Color(0xFF7C3AED),
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Color(0xFFDB2777),
    secondaryContainer = Color(0xFFFCE7F3),
    tertiary = Color(0xFF059669),
)

val PurpleDarkColorScheme = DarkColorScheme.copy(
    primary = Color(0xFFC084FC),
    primaryContainer = Color(0xFF6B21A8),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = Color(0xFFF472B6),
    secondaryContainer = Color(0xFF831843),
    tertiary = Color(0xFF34D399),
)

fun getPresetColorScheme(darkTheme: Boolean, preset: AppThemePreset): ColorScheme {
    return when (preset) {
        AppThemePreset.Orange -> if (darkTheme) OrangeDarkColorScheme else OrangeLightColorScheme
        AppThemePreset.Blue -> if (darkTheme) BlueDarkColorScheme else BlueLightColorScheme
        AppThemePreset.Green -> if (darkTheme) GreenDarkColorScheme else GreenLightColorScheme
        AppThemePreset.Red -> if (darkTheme) RedDarkColorScheme else RedLightColorScheme
        AppThemePreset.Purple -> if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
    }
}

// M3 Expressive shapes: generous corner radii
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// ── Extended colors not covered by Material 3 ──────────
@Immutable
data class ExtendedColors(
    val warning: Color = Color.Unspecified,
    val onWarning: Color = Color.Unspecified,
    val warningContainer: Color = Color.Unspecified,
    val success: Color = Color.Unspecified,
)

val LocalExtendedColors = staticCompositionLocalOf { ExtendedColors() }

val LightExtendedColors = ExtendedColors(
    warning = WarningLight,
    onWarning = Color(0xFF7C2D12),
    warningContainer = Color(0xFFFFF3E0),
    success = SuccessLight,
)

val DarkExtendedColors = ExtendedColors(
    warning = WarningDark,
    onWarning = Color(0xFF3E2700),
    warningContainer = Color(0xFF4E3600),
    success = SuccessDark,
)

@Composable
fun UniversalAntiSplitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    amoledMode: Boolean = false,
    themePreset: AppThemePreset = AppThemePreset.Orange,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            getPresetColorScheme(darkTheme, themePreset)
        }
    }.let { baseScheme ->
        if (darkTheme && amoledMode) {
            baseScheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color(0xFF0F0F0F),
                surfaceContainer = Color(0xFF141414),
                surfaceContainerHigh = Color(0xFF1C1C1C),
                surfaceContainerHighest = Color(0xFF262626)
            )
        } else {
            baseScheme
        }
    }

    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    val currentDensity = LocalDensity.current
    val customDensity = remember(currentDensity) {
        object : Density by currentDensity {
            override val fontScale: Float
                get() = currentDensity.fontScale.coerceAtMost(1.15f)
        }
    }

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors,
        LocalDensity provides customDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = ExpressiveShapes,
            content = content
        )
    }
}

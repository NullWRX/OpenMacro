package io.github.nullwrx.openmacro.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.nullwrx.openmacro.data.settings.ThemeMode

// A deep "garden" green as the brand, with warm neutrals. Every role is set so nothing falls back
// to Material's default purple.
private val Light = lightColorScheme(
    primary = Color(0xFF1F6B4A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA8F2C9),
    onPrimaryContainer = Color(0xFF002113),
    inversePrimary = Color(0xFF8CD5AE),
    secondary = Color(0xFF4E6356),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8D7),
    onSecondaryContainer = Color(0xFF0B1F15),
    tertiary = Color(0xFF3C6472),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC0E9F9),
    onTertiaryContainer = Color(0xFF001F28),
    background = Color(0xFFF6FBF5),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF6FBF5),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDCE5DC),
    onSurfaceVariant = Color(0xFF404943),
    surfaceTint = Color(0xFF1F6B4A),
    inverseSurface = Color(0xFF2C322E),
    inverseOnSurface = Color(0xFFEDF2EC),
    outline = Color(0xFF707972),
    outlineVariant = Color(0xFFC0C9C0),
    surfaceBright = Color(0xFFF6FBF5),
    surfaceDim = Color(0xFFD6DBD5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5EF),
    surfaceContainer = Color(0xFFEAEFE9),
    surfaceContainerHigh = Color(0xFFE4EAE4),
    surfaceContainerHighest = Color(0xFFDFE4DE),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8CD5AE),
    onPrimary = Color(0xFF003823),
    primaryContainer = Color(0xFF005235),
    onPrimaryContainer = Color(0xFFA8F2C9),
    inversePrimary = Color(0xFF1F6B4A),
    secondary = Color(0xFFB5CCBB),
    onSecondary = Color(0xFF213529),
    secondaryContainer = Color(0xFF374B3F),
    onSecondaryContainer = Color(0xFFD0E8D7),
    tertiary = Color(0xFFA4CDDD),
    onTertiary = Color(0xFF053542),
    tertiaryContainer = Color(0xFF234C59),
    onTertiaryContainer = Color(0xFFC0E9F9),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFDFE4DE),
    surface = Color(0xFF0F1511),
    onSurface = Color(0xFFDFE4DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C0),
    surfaceTint = Color(0xFF8CD5AE),
    inverseSurface = Color(0xFFDFE4DE),
    inverseOnSurface = Color(0xFF2C322E),
    outline = Color(0xFF8A938B),
    outlineVariant = Color(0xFF404943),
    surfaceBright = Color(0xFF353B36),
    surfaceDim = Color(0xFF0F1511),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1B211D),
    surfaceContainerHigh = Color(0xFF252B27),
    surfaceContainerHighest = Color(0xFF303632),
)

/** Fixed colors for the three macros so they read the same on every screen. */
@Immutable
data class MacroColors(val protein: Color, val carbs: Color, val fat: Color, val over: Color)

private val LightMacros = MacroColors(
    protein = Color(0xFF2F6FB0),
    carbs = Color(0xFFB07818),
    fat = Color(0xFFB04A64),
    over = Color(0xFFBA1A1A),
)
private val DarkMacros = MacroColors(
    protein = Color(0xFF8FC2FF),
    carbs = Color(0xFFF2C46B),
    fat = Color(0xFFF59AB0),
    over = Color(0xFFFFB4AB),
)

val LocalMacroColors = staticCompositionLocalOf { LightMacros }

private val AppTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Tabular figures keep columns of numbers aligned. */
val NumberStyle = TextStyle(fontFeatureSettings = "tnum", fontSize = 14.sp)

@Composable
fun OpenMacroTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme: ColorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) Dark else Light
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalMacroColors provides if (dark) DarkMacros else LightMacros,
    ) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}

package dev.virtualvolume.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import dev.virtualvolume.app.core.data.ThemeMode

private val DarkColors = darkColorScheme(
    primary = BrandMint,
    onPrimary = Color(0xFF04211D),
    primaryContainer = Color(0xFF14453F),
    onPrimaryContainer = BrandMintBright,
    inversePrimary = BrandMintDeep,
    secondary = Color(0xFF8FA3C0),
    onSecondary = Color(0xFF0E1420),
    secondaryContainer = Color(0xFF1E2735),
    onSecondaryContainer = Color(0xFFCFDAEA),
    tertiary = Color(0xFF9A8CF5),
    onTertiary = Color(0xFF140F2B),
    tertiaryContainer = Color(0xFF241C46),
    onTertiaryContainer = Color(0xFFD9D2FF),
    background = BrandInk,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = BrandMint,
    inverseSurface = Color(0xFFE3E9F2),
    inverseOnSurface = Color(0xFF0D1219),
    outline = DarkOutline,
    outlineVariant = Color(0xFF202836),
    error = ErrorRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = ErrorRedContainer,
    onErrorContainer = Color(0xFFFFD5D6),
    scrim = Color(0xCC000000),
)

private val LightColors = lightColorScheme(
    primary = BrandMintDeep,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F0E6),
    onPrimaryContainer = Color(0xFF04211D),
    inversePrimary = BrandMint,
    secondary = Color(0xFF4A5A72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDEE6F2),
    onSecondaryContainer = Color(0xFF1B2534),
    tertiary = Color(0xFF5B4BB7),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE5E0FF),
    onTertiaryContainer = Color(0xFF1B1440),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = BrandMintDeep,
    inverseSurface = Color(0xFF141A24),
    inverseOnSurface = Color(0xFFE9EFF8),
    outline = LightOutline,
    outlineVariant = Color(0xFFDDE3EC),
    error = Color(0xFFC62A2F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD9),
    onErrorContainer = Color(0xFF410004),
    scrim = Color(0x99000000),
)

/**
 * Extra brand tokens Material does not model: the accent gradient used behind hero cards
 * and the glow colour shared with the overlay control.
 */
data class BrandPalette(
    val accentGradient: Brush,
    val glow: Color,
    val controlFill: Color,
    val controlTrack: Color,
    val isDark: Boolean,
)

private val DarkBrand = BrandPalette(
    accentGradient = Brush.linearGradient(
        listOf(Color(0xFF133E3A), Color(0xFF0E1622), Color(0xFF16122E)),
    ),
    glow = BrandMint,
    controlFill = BrandMint,
    controlTrack = Color(0x33FFFFFF),
    isDark = true,
)

private val LightBrand = BrandPalette(
    accentGradient = Brush.linearGradient(
        listOf(Color(0xFFD9F6F0), Color(0xFFE9EEF7), Color(0xFFE6E2FA)),
    ),
    glow = BrandMintDeep,
    controlFill = BrandMintDeep,
    controlTrack = Color(0x22000000),
    isDark = false,
)

val LocalBrand = staticCompositionLocalOf { DarkBrand }

val VirtualVolumeShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

@Composable
fun VirtualVolumeTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val brand = if (darkTheme) DarkBrand else LightBrand

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalBrand provides brand) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VirtualVolumeTypography,
            shapes = VirtualVolumeShapes,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

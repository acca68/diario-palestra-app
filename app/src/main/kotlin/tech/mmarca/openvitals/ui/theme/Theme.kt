package tech.mmarca.openvitals.ui.theme

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import tech.mmarca.openvitals.domain.preferences.AppThemeMode

// Diario Palestra: the whole app uses the diary's palette (after the "Athlete AI" reference):
// warm cream or warm near-black ground, royal blue accents, orange as the second accent.
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7D97FF),
    onPrimary = Color(0xFF0A1A5C),
    primaryContainer = Color(0xFF263C99),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Color(0xFFB9B5AB),
    onSecondary = Color(0xFF26241F),
    secondaryContainer = Color(0xFF34312B),
    onSecondaryContainer = Color(0xFFEDE8DE),
    tertiary = Color(0xFFFF8A4C),
    onTertiary = Color(0xFF3A1600),
    tertiaryContainer = Color(0xFF6B2E0C),
    onTertiaryContainer = Color(0xFFFFDBC9),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF141311),
    onBackground = Color(0xFFF1EDE5),
    surface = Color(0xFF141311),
    onSurface = Color(0xFFF1EDE5),
    surfaceVariant = Color(0xFF2A2824),
    onSurfaceVariant = Color(0xFFA9A398),
    outline = Color(0xFF5A564E),
    outlineVariant = Color(0xFF34322D),
    surfaceContainerLowest = Color(0xFF181715),
    surfaceContainerLow = Color(0xFF1D1C19),
    surfaceContainer = Color(0xFF201F1C),
    surfaceContainerHigh = Color(0xFF292723),
    surfaceContainerHighest = Color(0xFF34322D),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3B5BDB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF0D1F66),
    secondary = Color(0xFF5B5E6B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E5DB),
    onSecondaryContainer = Color(0xFF1C1B18),
    tertiary = Color(0xFFEE6C2B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBC9),
    onTertiaryContainer = Color(0xFF3A1600),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color.White,
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF3EFE7),
    onBackground = Color(0xFF131313),
    surface = Color(0xFFF3EFE7),
    onSurface = Color(0xFF131313),
    surfaceVariant = Color(0xFFEAE5DA),
    onSurfaceVariant = Color(0xFF6E6A62),
    outline = Color(0xFFCFC8BA),
    outlineVariant = Color(0xFFE4DED2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFDF9),
    surfaceContainer = Color(0xFFFFFDF9),
    surfaceContainerHigh = Color(0xFFF8F5EE),
    surfaceContainerHighest = Color(0xFFEFEAE0),
)

/** The shape scale, one name per [Radii] step. `medium` is the 12dp card corner. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.xs),
    small = RoundedCornerShape(Radii.sm),
    medium = RoundedCornerShape(Radii.md),
    large = RoundedCornerShape(Radii.lg),
    extraLarge = RoundedCornerShape(Radii.xl),
)

@Composable
fun OpenVitalsTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemInDarkTheme = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> systemInDarkTheme
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK,
        AppThemeMode.AMOLED -> true
    }
    val colorScheme = when {
        themeMode == AppThemeMode.AMOLED -> {
            val baseColorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(LocalContext.current)
            } else {
                DarkColorScheme
            }
            baseColorScheme.toAmoledColorScheme()
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Resolved once, so every screen reads the same answer.
    val reducedMotion = !animatorsEnabled()

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

private fun ColorScheme.toAmoledColorScheme(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF030303),
        surfaceContainer = Color(0xFF080808),
        surfaceContainerHigh = Color(0xFF101010),
        surfaceContainerHighest = Color(0xFF181818),
        surfaceVariant = Color(0xFF242424),
        outlineVariant = Color(0xFF3A3A3A),
    )

/** Whether the running scheme is the AMOLED one, told from its defining property. */
@Composable
fun isAmoledColorScheme(): Boolean {
    val colorScheme = MaterialTheme.colorScheme
    return colorScheme.background == Color.Black && colorScheme.surface == Color.Black
}

@Composable
fun accentSurfaceContainerColor(
    accentColor: Color,
    amoledAlpha: Float = 0.10f,
    fallback: Color? = null,
): Color =
    if (isAmoledColorScheme()) {
        accentColor.copy(alpha = amoledAlpha).compositeOver(Color.Black)
    } else {
        fallback ?: MaterialTheme.colorScheme.surfaceContainer
    }

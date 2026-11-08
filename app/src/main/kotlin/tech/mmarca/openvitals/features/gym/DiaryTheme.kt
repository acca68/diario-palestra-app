package tech.mmarca.openvitals.features.gym

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Diario Palestra: how the diary looks, independent of the rest of the app. */
enum class DiaryThemeMode { AUTO, LIGHT, DARK }

internal object DiaryThemePrefs {
    private const val FILE = "diario_palestra_prefs"
    private const val KEY = "theme"

    fun load(context: Context): DiaryThemeMode =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { name -> DiaryThemeMode.entries.firstOrNull { it.name == name } } ?: DiaryThemeMode.AUTO

    fun save(context: Context, mode: DiaryThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
    }
}

/**
 * The palette, after the "Athlete AI" reference: warm cream ground, white cards, a vivid
 * royal blue for accents and the lead card, near-black bold type, orange for charts.
 */
internal object DiaryColors {
    val blue = Color(0xFF3B5BDB)
    val heroStart = Color(0xFF3557D6)
    val heroEnd = Color(0xFF4A6CF0)
    val onHero = Color.White
    val onHeroMuted = Color(0xFFD3DCFF)
    val orange = Color(0xFFEE6C2B)
    val onOrange = Color.White
    val pill = Color.White
    val onPill = blue
    val good = Color(0xFF2FA36B)
    val warn = Color(0xFFE08A2C)

    val light = lightColorScheme(
        primary = blue,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDCE3FF),
        onPrimaryContainer = Color(0xFF0D1F66),
        secondary = Color(0xFF5B5E6B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE9E5DB),
        onSecondaryContainer = Color(0xFF1C1B18),
        tertiary = orange,
        onTertiary = Color.White,
        background = Color(0xFFF3EFE7),
        onBackground = Color(0xFF131313),
        surface = Color(0xFFF3EFE7),
        onSurface = Color(0xFF131313),
        surfaceVariant = Color(0xFFEAE5DA),
        onSurfaceVariant = Color(0xFF6E6A62),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFFFDF9),
        surfaceContainer = Color(0xFFFFFDF9),
        surfaceContainerHigh = Color(0xFFF8F5EE),
        surfaceContainerHighest = Color(0xFFEFEAE0),
        outline = Color(0xFFCFC8BA),
        outlineVariant = Color(0xFFE4DED2),
    )

    val dark = darkColorScheme(
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
        background = Color(0xFF141311),
        onBackground = Color(0xFFF1EDE5),
        surface = Color(0xFF141311),
        onSurface = Color(0xFFF1EDE5),
        surfaceVariant = Color(0xFF2A2824),
        onSurfaceVariant = Color(0xFFA9A398),
        surfaceContainerLowest = Color(0xFF181715),
        surfaceContainerLow = Color(0xFF1D1C19),
        surfaceContainer = Color(0xFF201F1C),
        surfaceContainerHigh = Color(0xFF292723),
        surfaceContainerHighest = Color(0xFF34322D),
        outline = Color(0xFF5A564E),
        outlineVariant = Color(0xFF34322D),
    )

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )
}

@Composable
internal fun DiaryTheme(mode: DiaryThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        DiaryThemeMode.AUTO -> isSystemInDarkTheme()
        DiaryThemeMode.LIGHT -> false
        DiaryThemeMode.DARK -> true
    }
    val scheme = if (dark) DiaryColors.dark else DiaryColors.light
    val ground = if (dark) {
        listOf(Color(0xFF141311), Color(0xFF141311))
    } else {
        listOf(Color(0xFFF3EFE7), Color(0xFFF3EFE7))
    }
    MaterialTheme(colorScheme = scheme, shapes = DiaryColors.shapes, typography = MaterialTheme.typography) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(ground))) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface) { content() }
        }
    }
}

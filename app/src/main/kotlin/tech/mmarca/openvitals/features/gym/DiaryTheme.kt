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

/** The palette: mint ground, white cards, deep teal for the lead card, orange for the main action. */
internal object DiaryColors {
    val heroStart = Color(0xFF123F3B)
    val heroEnd = Color(0xFF1D6B62)
    val onHero = Color.White
    val onHeroMuted = Color(0xFFB7D9D2)
    val orange = Color(0xFFF2762E)
    val onOrange = Color.White
    val good = Color(0xFF2FA36B)
    val warn = Color(0xFFE08A2C)

    val light = lightColorScheme(
        primary = Color(0xFF0F7A6E),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFCDEFE7),
        onPrimaryContainer = Color(0xFF07332E),
        secondary = Color(0xFF3E6A63),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDCEEEA),
        onSecondaryContainer = Color(0xFF0E2C28),
        tertiary = orange,
        onTertiary = Color.White,
        background = Color(0xFFEAF4F1),
        onBackground = Color(0xFF10201E),
        surface = Color(0xFFF6FBFA),
        onSurface = Color(0xFF10201E),
        surfaceVariant = Color(0xFFE2EFEB),
        onSurfaceVariant = Color(0xFF5A6E6A),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color.White,
        surfaceContainer = Color.White,
        surfaceContainerHigh = Color(0xFFF1F7F5),
        surfaceContainerHighest = Color(0xFFE3EEEB),
        outline = Color(0xFFB7CAC5),
        outlineVariant = Color(0xFFD6E4E0),
    )

    val dark = darkColorScheme(
        primary = Color(0xFF4FD1BC),
        onPrimary = Color(0xFF02302A),
        primaryContainer = Color(0xFF14524A),
        onPrimaryContainer = Color(0xFFC9F3EA),
        secondary = Color(0xFF9CC8BF),
        onSecondary = Color(0xFF0B2A26),
        secondaryContainer = Color(0xFF213F3B),
        onSecondaryContainer = Color(0xFFD5ECE7),
        tertiary = Color(0xFFFF8C4A),
        onTertiary = Color(0xFF3A1600),
        background = Color(0xFF0B1A19),
        onBackground = Color(0xFFE2F0EC),
        surface = Color(0xFF0E201F),
        onSurface = Color(0xFFE2F0EC),
        surfaceVariant = Color(0xFF1E3532),
        onSurfaceVariant = Color(0xFF9AB3AE),
        surfaceContainerLowest = Color(0xFF0E201F),
        surfaceContainerLow = Color(0xFF132927),
        surfaceContainer = Color(0xFF16302D),
        surfaceContainerHigh = Color(0xFF1C3835),
        surfaceContainerHighest = Color(0xFF26443F),
        outline = Color(0xFF4B6A65),
        outlineVariant = Color(0xFF2B4743),
    )

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(22.dp),
        large = RoundedCornerShape(28.dp),
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
        listOf(Color(0xFF0B1A19), Color(0xFF0F2422))
    } else {
        listOf(Color(0xFFDDF0EA), Color(0xFFF3F8F6))
    }
    MaterialTheme(colorScheme = scheme, shapes = DiaryColors.shapes, typography = MaterialTheme.typography) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(ground))) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface) { content() }
        }
    }
}

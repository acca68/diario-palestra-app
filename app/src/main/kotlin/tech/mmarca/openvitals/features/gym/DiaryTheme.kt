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

/**
 * The two looks of the diary: BLUE (cream and royal blue, tabs on top) and PASTEL (soft
 * section colours, thin large titles, a home page of tiles, black pill buttons).
 */
enum class DiaryStyle { BLUE, PASTEL }

/** The soft colours a section of the pastel style is washed with. */
@androidx.compose.runtime.Immutable
data class DiaryTint(val first: Color, val second: Color, val firstDark: Color, val secondDark: Color) {
    companion object {
        val home = DiaryTint(Color(0xFFF8E3DA), Color(0xFFDCE5FA), Color(0xFF3A2A25), Color(0xFF232C40))
        val gym = DiaryTint(Color(0xFFD8E2FA), Color(0xFFE9EEFC), Color(0xFF1F2A44), Color(0xFF1A2133))
        val body = DiaryTint(Color(0xFFD5F0DF), Color(0xFFEAF7EF), Color(0xFF1C3326), Color(0xFF17261D))
        val trainer = DiaryTint(Color(0xFFFCE0CC), Color(0xFFFFF0E3), Color(0xFF3D2A1C), Color(0xFF2C2119))
        val nutrition = DiaryTint(Color(0xFFFBE7B5), Color(0xFFFFF7DF), Color(0xFF3A3119), Color(0xFF2A2516))
        val health = DiaryTint(Color(0xFFEDE3FA), Color(0xFFFBE3EC), Color(0xFF2E2540), Color(0xFF3A2430))
    }
}

/** Which style is drawn: the diary's pieces read it to pick their look. */
internal val LocalDiaryStyle = androidx.compose.runtime.staticCompositionLocalOf { DiaryStyle.BLUE }

internal object DiaryThemePrefs {
    private const val FILE = "diario_palestra_prefs"
    private const val KEY = "theme"
    private const val STYLE = "style"

    fun loadStyle(context: Context): DiaryStyle =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(STYLE, null)
            ?.let { name -> DiaryStyle.entries.firstOrNull { it.name == name } } ?: DiaryStyle.BLUE

    fun saveStyle(context: Context, style: DiaryStyle) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(STYLE, style.name).apply()
    }

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

    /** The pastel style: off-white or near-black ground, white cards, black (or white) actions. */
    val pastelLight = lightColorScheme(
        primary = Color(0xFF151515),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE6EBF7),
        onPrimaryContainer = Color(0xFF151515),
        secondary = Color(0xFF5E5E5A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEDEDE9),
        onSecondaryContainer = Color(0xFF151515),
        tertiary = Color(0xFFE8834A),
        onTertiary = Color.White,
        background = Color(0xFFF7F7F5),
        onBackground = Color(0xFF151515),
        surface = Color(0xFFF7F7F5),
        onSurface = Color(0xFF151515),
        surfaceVariant = Color(0xFFEDEDE9),
        onSurfaceVariant = Color(0xFF77776F),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color.White,
        surfaceContainer = Color.White,
        surfaceContainerHigh = Color(0xFFFBFBFA),
        surfaceContainerHighest = Color(0xFFF1F1EE),
        outline = Color(0xFFDADAD5),
        outlineVariant = Color(0xFFE8E8E4),
    )

    val pastelDark = darkColorScheme(
        primary = Color(0xFFF2F2F0),
        onPrimary = Color(0xFF151515),
        primaryContainer = Color(0xFF2C3242),
        onPrimaryContainer = Color(0xFFF2F2F0),
        secondary = Color(0xFFB5B5AE),
        onSecondary = Color(0xFF151515),
        secondaryContainer = Color(0xFF2A2A28),
        onSecondaryContainer = Color(0xFFF2F2F0),
        tertiary = Color(0xFFF29A66),
        onTertiary = Color(0xFF2A1404),
        background = Color(0xFF111111),
        onBackground = Color(0xFFF2F2F0),
        surface = Color(0xFF111111),
        onSurface = Color(0xFFF2F2F0),
        surfaceVariant = Color(0xFF262624),
        onSurfaceVariant = Color(0xFF9E9E97),
        surfaceContainerLowest = Color(0xFF161616),
        surfaceContainerLow = Color(0xFF1B1B1B),
        surfaceContainer = Color(0xFF1E1E1E),
        surfaceContainerHigh = Color(0xFF242424),
        surfaceContainerHighest = Color(0xFF2E2E2C),
        outline = Color(0xFF4A4A46),
        outlineVariant = Color(0xFF2E2E2C),
    )

    val pastelShapes = Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(22.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp),
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
internal fun isDiaryDark(mode: DiaryThemeMode): Boolean = when (mode) {
    DiaryThemeMode.AUTO -> isSystemInDarkTheme()
    DiaryThemeMode.LIGHT -> false
    DiaryThemeMode.DARK -> true
}

@Composable
internal fun DiaryTheme(
    mode: DiaryThemeMode,
    style: DiaryStyle = DiaryStyle.BLUE,
    tint: DiaryTint = DiaryTint.home,
    content: @Composable () -> Unit,
) {
    val dark = isDiaryDark(mode)
    if (style == DiaryStyle.PASTEL) {
        val scheme = if (dark) DiaryColors.pastelDark else DiaryColors.pastelLight
        val a = if (dark) tint.firstDark else tint.first
        val b = if (dark) tint.secondDark else tint.second
        MaterialTheme(colorScheme = scheme, shapes = DiaryColors.pastelShapes, typography = MaterialTheme.typography) {
            Box(modifier = Modifier.fillMaxSize().background(scheme.background)) {
                // Two soft washes of the section's colours over the top of the page.
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.radialGradient(listOf(a, Color.Transparent), center = androidx.compose.ui.geometry.Offset(150f, 350f), radius = 1100f),
                    ),
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.radialGradient(listOf(b, Color.Transparent), center = androidx.compose.ui.geometry.Offset(1050f, 650f), radius = 1000f),
                    ),
                )
                CompositionLocalProvider(LocalContentColor provides scheme.onSurface, LocalDiaryStyle provides style) { content() }
            }
        }
        return
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

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
enum class DiaryStyle { BLUE, PASTEL, GLASS }

/** The accent colours of the blue style. */
enum class DiaryAccent(val color: Color, @param:androidx.annotation.StringRes val label: Int) {
    BLUE(Color(0xFF3B5BDB), tech.mmarca.openvitals.R.string.accent_blue),
    LIME(Color(0xFF6FAE16), tech.mmarca.openvitals.R.string.accent_lime),
    GREEN(Color(0xFF2E9E62), tech.mmarca.openvitals.R.string.accent_green),
    ORANGE(Color(0xFFE2702A), tech.mmarca.openvitals.R.string.accent_orange),
    PURPLE(Color(0xFF7C4DDB), tech.mmarca.openvitals.R.string.accent_purple),
    RED(Color(0xFFD23C45), tech.mmarca.openvitals.R.string.accent_red),
    TEAL(Color(0xFF1C92A0), tech.mmarca.openvitals.R.string.accent_teal),
}

/** The accent in use (the blue style's lead card, pill and highlights read it). */
internal val LocalDiaryAccent = androidx.compose.runtime.staticCompositionLocalOf { DiaryAccent.BLUE.color }

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

/** The soft styles (pastel, glass): thin type, a home page of tiles, a circle as the lead. */
internal val DiaryStyle.soft: Boolean get() = this != DiaryStyle.BLUE

/** Weight of the figures in cards: bold in the blue style, light in the soft ones. */
@Composable
internal fun figureWeight(): androidx.compose.ui.text.font.FontWeight =
    if (LocalDiaryStyle.current.soft) androidx.compose.ui.text.font.FontWeight.Light else androidx.compose.ui.text.font.FontWeight.SemiBold

internal object DiaryThemePrefs {
    private const val FILE = "diario_palestra_prefs"
    private const val KEY = "theme"
    private const val STYLE = "style"
    private const val ACCENT = "accent"

    fun loadAccent(context: Context): DiaryAccent =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(ACCENT, null)
            ?.let { name -> DiaryAccent.entries.firstOrNull { it.name == name } } ?: DiaryAccent.BLUE

    fun saveAccent(context: Context, accent: DiaryAccent) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(ACCENT, accent.name).apply()
    }

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

    /** The glass style: steel-blue ground, frosted see-through cards. */
    val glassDark = darkColorScheme(
        primary = Color(0xFF9DC1FF),
        onPrimary = Color(0xFF0F1E30),
        primaryContainer = Color(0x40A9C8F2),
        onPrimaryContainer = Color(0xFFF2F6FB),
        secondary = Color(0xFFB4C4D8),
        onSecondary = Color(0xFF0F1E30),
        secondaryContainer = Color(0x33FFFFFF),
        onSecondaryContainer = Color(0xFFF2F6FB),
        tertiary = Color(0xFFFFA36B),
        onTertiary = Color(0xFF2A1404),
        background = Color(0xFF13243A),
        onBackground = Color(0xFFF2F6FB),
        surface = Color(0xFF13243A),
        onSurface = Color(0xFFF2F6FB),
        surfaceVariant = Color(0x29FFFFFF),
        onSurfaceVariant = Color(0xFFAFC2D8),
        surfaceContainerLowest = Color(0x14FFFFFF),
        surfaceContainerLow = Color(0x1FFFFFFF),
        surfaceContainer = Color(0x24FFFFFF),
        surfaceContainerHigh = Color(0x2EFFFFFF),
        surfaceContainerHighest = Color(0x38FFFFFF),
        outline = Color(0x66FFFFFF),
        outlineVariant = Color(0x33FFFFFF),
    )

    val glassLight = lightColorScheme(
        primary = Color(0xFF284B73),
        onPrimary = Color.White,
        primaryContainer = Color(0x6694B4DA),
        onPrimaryContainer = Color(0xFF12263D),
        secondary = Color(0xFF4F6580),
        onSecondary = Color.White,
        secondaryContainer = Color(0x99FFFFFF),
        onSecondaryContainer = Color(0xFF12263D),
        tertiary = Color(0xFFD9692B),
        onTertiary = Color.White,
        background = Color(0xFFDCE6F2),
        onBackground = Color(0xFF12263D),
        surface = Color(0xFFDCE6F2),
        onSurface = Color(0xFF12263D),
        surfaceVariant = Color(0x80FFFFFF),
        onSurfaceVariant = Color(0xFF55697F),
        surfaceContainerLowest = Color(0xB3FFFFFF),
        surfaceContainerLow = Color(0x99FFFFFF),
        surfaceContainer = Color(0x8CFFFFFF),
        surfaceContainerHigh = Color(0xA6FFFFFF),
        surfaceContainerHighest = Color(0xBFFFFFFF),
        outline = Color(0x8055697F),
        outlineVariant = Color(0x4055697F),
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
    accent: DiaryAccent = DiaryAccent.BLUE,
    content: @Composable () -> Unit,
) {
    val dark = isDiaryDark(mode)
    if (style == DiaryStyle.GLASS) {
        val scheme = if (dark) DiaryColors.glassDark else DiaryColors.glassLight
        val ground = if (dark) listOf(Color(0xFF0E1B2C), Color(0xFF2B4A70)) else listOf(Color(0xFFC6D6EA), Color(0xFFF1F5FA))
        val glow = if (dark) tint.firstDark.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f)
        MaterialTheme(colorScheme = scheme, shapes = DiaryColors.pastelShapes, typography = MaterialTheme.typography) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(ground))) {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.radialGradient(listOf(glow, Color.Transparent), center = androidx.compose.ui.geometry.Offset(900f, 300f), radius = 1000f),
                    ),
                )
                CompositionLocalProvider(LocalContentColor provides scheme.onSurface, LocalDiaryStyle provides style) { content() }
            }
        }
        return
    }
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
    val base = if (dark) DiaryColors.dark else DiaryColors.light
    val a = accent.color
    val scheme = if (accent == DiaryAccent.BLUE) {
        base
    } else {
        base.copy(
            primary = if (dark) androidx.compose.ui.graphics.lerp(a, Color.White, 0.3f) else a,
            onPrimary = if (dark) Color(0xFF101010) else Color.White,
            primaryContainer = if (dark) androidx.compose.ui.graphics.lerp(a, Color.Black, 0.55f) else androidx.compose.ui.graphics.lerp(a, Color.White, 0.8f),
            onPrimaryContainer = if (dark) androidx.compose.ui.graphics.lerp(a, Color.White, 0.85f) else androidx.compose.ui.graphics.lerp(a, Color.Black, 0.6f),
        )
    }
    val ground = if (dark) {
        listOf(Color(0xFF141311), Color(0xFF141311))
    } else {
        listOf(Color(0xFFF3EFE7), Color(0xFFF3EFE7))
    }
    MaterialTheme(colorScheme = scheme, shapes = DiaryColors.shapes, typography = MaterialTheme.typography) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(ground))) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface, LocalDiaryAccent provides a) { content() }
        }
    }
}

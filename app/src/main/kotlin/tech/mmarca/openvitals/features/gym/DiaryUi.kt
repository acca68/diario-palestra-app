package tech.mmarca.openvitals.features.gym

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import tech.mmarca.openvitals.ui.components.OpenVitalsCard

/**
 * Diario Palestra: the section title that splits each tab into clear parts, with the
 * section's own actions on the right ("+ New", a period switch).
 */
@Composable
internal fun DiarySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    if (LocalDiaryStyle.current == DiaryStyle.PASTEL) {
        Row(
            modifier = modifier.fillMaxWidth().heightIn(min = 40.dp).padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title.uppercase(),
                style = DiaryType.pastelLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            action?.invoke(this)
        }
        return
    }
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 44.dp).padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title.uppercase(),
                style = DiaryType.label,
                color = MaterialTheme.colorScheme.primary,
            )
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        action?.invoke(this)
    }
}

/** The reference's type: monospaced, spaced capitals for labels; heavy, tight figures. */
internal object DiaryType {
    val label = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 2.5.sp,
    )
    val smallLabel = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
    )
    val figure = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (-0.5).sp)
    val figureSmall = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, letterSpacing = (-0.5).sp)
    val title = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = (-0.6).sp)

    // The pastel style: large, thin type and plain spaced capitals.
    val pastelLabel = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 3.sp)
    val pastelTitle = TextStyle(fontWeight = FontWeight.Light, fontSize = 38.sp, letterSpacing = (-1).sp)
    val pastelHuge = TextStyle(fontWeight = FontWeight.Light, fontSize = 56.sp, letterSpacing = (-2).sp, lineHeight = 60.sp)
    val pastelFigure = TextStyle(fontWeight = FontWeight.Light, fontSize = 24.sp, letterSpacing = (-0.6).sp)
    val pastelGreeting = TextStyle(fontWeight = FontWeight.Light, fontSize = 44.sp, letterSpacing = (-1.5).sp, lineHeight = 48.sp)
}

/** A few headline figures side by side in one card. */
@Composable
internal fun DiaryStatsCard(stats: List<Pair<String, String>>) {
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
            stats.forEach { (value, label) ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val pastel = LocalDiaryStyle.current == DiaryStyle.PASTEL
                    Text(value, style = if (pastel) DiaryType.pastelFigure else DiaryType.figure)
                    Text(
                        text = if (pastel) label else label.uppercase(),
                        style = if (pastel) MaterialTheme.typography.bodySmall else DiaryType.smallLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Two- or three-way switch shown in a section header. */
@Composable
internal fun <T> DiaryChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    options.forEach { (value, label) ->
        FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
    }
}

/** A short muted line, for empty states. */
@Composable
internal fun DiaryNote(text: String, modifier: Modifier = Modifier) {
    OpenVitalsCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * The lead card of a tab, royal blue with white text, as in the reference: a title, a
 * line under it, up to four headline figures and the tab's main action as an orange pill.
 */
@Composable
internal fun DiaryHeroCard(
    title: String,
    subtitle: String? = null,
    stats: List<Pair<String, String>> = emptyList(),
    action: (@Composable () -> Unit)? = null,
) {
    if (LocalDiaryStyle.current == DiaryStyle.PASTEL) {
        PastelHero(title, subtitle, stats, action)
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(DiaryColors.heroStart, DiaryColors.heroEnd)))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = DiaryType.title, color = DiaryColors.onHero)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = DiaryColors.onHeroMuted)
                }
            }
            action?.invoke()
        }
        if (stats.isNotEmpty()) {
            Row {
                stats.forEach { (value, label) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = value,
                            style = if (stats.size >= 3) DiaryType.figureSmall else DiaryType.figure,
                            color = DiaryColors.onHero,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Text(label.uppercase(), style = DiaryType.smallLabel, color = DiaryColors.onHeroMuted)
                    }
                }
            }
        }
    }
}

/**
 * The pastel lead: a thin large title, a line under it, a big soft circle with the first
 * figure and the main action, and the other figures in a row below.
 */
@Composable
private fun PastelHero(
    title: String,
    subtitle: String?,
    stats: List<Pair<String, String>>,
    action: (@Composable () -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = DiaryType.pastelTitle, textAlign = TextAlign.Center)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.size(12.dp))
        Box(
            modifier = Modifier
                .size(250.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (dark) 0.06f else 0.45f))
                .border(14.dp, Color.White.copy(alpha = if (dark) 0.08f else 0.6f), CircleShape)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                stats.firstOrNull()?.let { (value, label) ->
                    Text(
                        value,
                        style = if (value.length > 5) DiaryType.pastelHuge.copy(fontSize = 40.sp, lineHeight = 44.sp) else DiaryType.pastelHuge,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                if (action != null) {
                    Spacer(Modifier.size(6.dp))
                    action()
                }
            }
        }
        val rest = stats.drop(1)
        if (rest.isNotEmpty()) {
            Spacer(Modifier.size(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                rest.forEach { (value, label) ->
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(value, style = DiaryType.pastelFigure, maxLines = 1, softWrap = false)
                        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** The tab's main action: a white pill on the blue lead card, a black one in the pastel style. */
@Composable
internal fun DiaryPill(text: String, icon: ImageVector? = null, onClick: () -> Unit) {
    val pastel = LocalDiaryStyle.current == DiaryStyle.PASTEL
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = if (pastel) {
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        } else {
            ButtonDefaults.buttonColors(containerColor = DiaryColors.pill, contentColor = DiaryColors.onPill)
        },
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

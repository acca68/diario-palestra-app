package tech.mmarca.openvitals.features.gym

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import tech.mmarca.openvitals.R
import tech.mmarca.openvitals.ui.components.OpenVitalsCard

private class Outline(val group: MuscleGroup?, val points: FloatArray)

private fun parse(views: List<Pair<String, List<String>>>): List<Outline> =
    views.flatMap { (name, polygons) ->
        polygons.map { raw ->
            Outline(
                group = GymMuscles.outlineGroups[name],
                points = raw.trim().split(Regex("\\s+")).map { it.toFloat() }.toFloatArray(),
            )
        }
    }

private val frontOutlines: List<Outline> by lazy { parse(MuscleMapData.front) }
private val backOutlines: List<Outline> by lazy { parse(MuscleMapData.back) }

private fun Double.sets(): String {
    val rounded = (this * 2).roundToInt() / 2.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString().replace('.', ',')
}

@Composable
internal fun GymMuscleCard(
    periodDays: Int,
    setsPerMuscle: Map<MuscleGroup, Double>,
    trainedDays: Set<LocalDate>,
    onPeriod: (Int) -> Unit,
) {
    val max = setsPerMuscle.values.maxOrNull() ?: 0.0
    val trained = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.surfaceContainerHighest
    val body = MaterialTheme.colorScheme.outlineVariant
    fun colorOf(group: MuscleGroup?): Color {
        val sets = group?.let { setsPerMuscle[it] } ?: 0.0
        return when {
            group == null -> body
            sets <= 0.0 || max <= 0.0 -> idle
            else -> trained.copy(alpha = (0.3 + 0.7 * sets / max).toFloat())
        }
    }
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.gym_muscles_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = periodDays == 7,
                    onClick = { onPeriod(7) },
                    label = { Text(stringResource(R.string.gym_muscles_days, 7)) },
                )
                Box(Modifier.width(6.dp))
                FilterChip(
                    selected = periodDays == 30,
                    onClick = { onPeriod(30) },
                    label = { Text(stringResource(R.string.gym_muscles_days, 30)) },
                )
            }
            if (periodDays == 7) WeekStrip(trainedDays)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    frontOutlines to R.string.gym_muscles_front,
                    backOutlines to R.string.gym_muscles_back,
                ).forEach { (outlines, label) ->
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        BodyFigure(outlines, ::colorOf, MaterialTheme.colorScheme.surfaceContainer)
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val ranked = setsPerMuscle.entries.filter { it.value > 0 }.sortedByDescending { it.value }
            if (ranked.isEmpty()) {
                Text(
                    text = stringResource(R.string.gym_muscles_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.gym_muscles_sets_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                ranked.forEach { (group, sets) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(group.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(110.dp),
                        )
                        Box(modifier = Modifier.weight(1f).height(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = (sets / max).toFloat().coerceIn(0.04f, 1f))
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(trained),
                            )
                        }
                        Text(
                            text = sets.sets(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(40.dp),
                        )
                    }
                }
                val untrained = MuscleGroup.entries.filter { (setsPerMuscle[it] ?: 0.0) <= 0.0 }
                if (untrained.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.gym_muscles_untrained,
                            untrained.map { stringResource(it.labelRes) }.joinToString(", "),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = stringResource(R.string.gym_muscles_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BodyFigure(outlines: List<Outline>, colorOf: (MuscleGroup?) -> Color, gap: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(MuscleMapData.WIDTH / MuscleMapData.HEIGHT),
    ) {
        val scale = size.width / MuscleMapData.WIDTH
        outlines.forEach { outline ->
            val p = outline.points
            if (p.size < 6) return@forEach
            val path = Path().apply {
                moveTo(p[0] * scale, p[1] * scale)
                var i = 2
                while (i + 1 < p.size) {
                    lineTo(p[i] * scale, p[i + 1] * scale)
                    i += 2
                }
                close()
            }
            drawPath(path, colorOf(outline.group))
            drawPath(path, gap, style = Stroke(width = 1.5f))
        }
    }
}

@Composable
private fun WeekStrip(trainedDays: Set<LocalDate>) {
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (6 downTo 0).forEach { back ->
            val day = today.minusDays(back.toLong())
            val on = day in trainedDays
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

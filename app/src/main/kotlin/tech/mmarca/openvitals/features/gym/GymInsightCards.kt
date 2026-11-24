package tech.mmarca.openvitals.features.gym

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import tech.mmarca.openvitals.R
import tech.mmarca.openvitals.ui.components.OpenVitalsCard

private val shortDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** A thin rounded progress bar. */
@Composable
internal fun DiaryProgressBar(progress: Float, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/**
 * A half-circle gauge: [value] between [min] and [max], with the [normal] band drawn in green
 * and the needle as a dot on the arc.
 */
@Composable
internal fun DiaryGauge(value: Double, min: Double, max: Double, normal: ClosedFloatingPointRange<Double>, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val band = Color(0xFF3FA66B)
    val high = MaterialTheme.colorScheme.tertiary
    val dot = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.fillMaxWidth().height(120.dp)) {
        val stroke = 16.dp.toPx()
        val diameter = minOf(size.width, size.height * 2) - stroke
        val topLeft = Offset((size.width - diameter) / 2, stroke / 2)
        val arc = Size(diameter, diameter)
        fun angle(v: Double) = 180f + 180f * ((v.coerceIn(min, max) - min) / (max - min)).toFloat()
        drawArc(track, 180f, 180f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(band, angle(normal.start), angle(normal.endInclusive) - angle(normal.start), false, topLeft, arc, style = Stroke(stroke))
        drawArc(high, angle(normal.endInclusive), 360f - angle(normal.endInclusive), false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        val a = Math.toRadians(angle(value).toDouble())
        val r = diameter / 2
        val center = Offset(topLeft.x + r, topLeft.y + r)
        drawCircle(dot, radius = stroke * 0.55f, center = Offset(center.x + (r * kotlin.math.cos(a)).toFloat(), center.y + (r * kotlin.math.sin(a)).toFloat()))
    }
}

@Composable
internal fun GymSummaryCard(period: GymPeriod, onSelect: (GymPeriod) -> Unit, summary: GymSummary) {
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GymPeriod.entries.forEach { p ->
                    FilterChip(selected = p == period, onClick = { onSelect(p) }, label = { Text(stringResource(p.label)) })
                }
            }
            Row {
                Figure(summary.sessions.toString(), stringResource(R.string.gym_sum_sessions), Modifier.weight(1f))
                Figure(hours(summary.minutes), stringResource(R.string.gym_sum_hours), Modifier.weight(1f))
                Figure(tonnes(summary.volumeKg), stringResource(R.string.gym_sum_tonnes), Modifier.weight(1f))
                Figure(if (summary.kcal > 0) summary.kcal.roundToInt().toString() else "—", stringResource(R.string.gym_sum_kcal), Modifier.weight(1f))
            }
            GymInsights.comparison(summary.volumeKg)?.let { c ->
                Text(
                    stringResource(R.string.gym_lift_compare, String.format(Locale.getDefault(), "%.1f", c.times), stringResource(c.thing)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun hours(minutes: Long): String = if (minutes <= 0) "—" else String.format(Locale.getDefault(), "%.1f", minutes / 60.0)

private fun tonnes(kg: Double): String = if (kg <= 0) "—" else String.format(Locale.getDefault(), "%.1f", kg / 1000.0)

@Composable
private fun Figure(value: String, label: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = figureWeight(), maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Weeks in a row with medals, the best run, and the next milestone of sessions. */
@Composable
internal fun GymStreakCard(streak: Int, best: Int, milestone: Milestone) {
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(30.dp))
                Text(" $streak", style = MaterialTheme.typography.headlineMedium, fontWeight = figureWeight())
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(stringResource(R.string.gym_streak_weeks), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.gym_streak_best, best), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                val medalColors = listOf(Color(0xFFCD7F32), Color(0xFF9EA7B0), Color(0xFFE2B33C), Color(0xFF6FB7D6), Color(0xFFB06FD6))
                GymInsights.MEDALS.forEachIndexed { i, weeks ->
                    val won = best >= weeks
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(46.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.EmojiEvents,
                                contentDescription = null,
                                tint = if (won) medalColors[i] else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            )
                        }
                        Text(
                            stringResource(R.string.gym_streak_medal, weeks),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (won) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val next = GymInsights.MEDALS.firstOrNull { it > best }
            if (next != null) {
                Text(
                    stringResource(R.string.gym_streak_next_medal, next, (next - streak).coerceAtLeast(0)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row {
                Text(stringResource(R.string.gym_milestone, milestone.next), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${milestone.current} / ${milestone.next}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DiaryProgressBar(milestone.progress)
        }
    }
}

@Composable
internal fun GymChallengesCard(challenges: List<Challenge>) {
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            challenges.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(c.label, number(c.target)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (c.done) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF3FA66B), modifier = Modifier.size(18.dp))
                    }
                    Text(
                        " ${number(c.current)} / ${number(c.target)}${if (c.unit.isNotEmpty()) " " + c.unit else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DiaryProgressBar(c.progress, if (c.done) Color(0xFF3FA66B) else MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

private fun number(v: Double): String =
    if (v == Math.floor(v) && v < 10_000) v.toInt().toString() else String.format(Locale.getDefault(), "%.1f", v)

@Composable
internal fun GymRecoveryCard(hints: List<RecoveryHint>) {
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            hints.take(4).forEach { h ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.SelfImprovement, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(R.string.gym_recovery_line, stringResource(h.group.labelRes), h.days),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                    )
                    Text(h.lastTrained.format(shortDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

package tech.mmarca.openvitals.features.gym

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import tech.mmarca.openvitals.R
import tech.mmarca.openvitals.ui.components.OpenVitalsCard

private fun Double.kg(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString().replace('.', ',')
}

@Composable
internal fun GymCalendarCard(
    month: YearMonth,
    sessionsByDay: Map<LocalDate, List<GymSession>>,
    selectedDay: LocalDate?,
    trainerDays: Set<LocalDate>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
) {
    val locale = Locale.getDefault()
    val today = LocalDate.now()
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = stringResource(R.string.gym_previous_month))
                }
                Text(
                    text = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                        .replaceFirstChar { it.titlecase(locale) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNextMonth, enabled = month < YearMonth.now()) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = stringResource(R.string.gym_next_month))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                DayOfWeek.values().forEach { day ->
                    Text(
                        text = day.getDisplayName(TextStyle.SHORT, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val offset = month.atDay(1).dayOfWeek.value - 1
            val cells = offset + month.lengthOfMonth()
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val dayNumber = r * 7 + c - offset + 1
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            if (dayNumber in 1..month.lengthOfMonth()) {
                                val date = month.atDay(dayNumber)
                                GymCalendarDay(
                                    date = date,
                                    sessions = sessionsByDay[date].orEmpty(),
                                    isToday = date == today,
                                    isSelected = date == selectedDay,
                                    withTrainer = date in trainerDays,
                                    isFuture = date.isAfter(today),
                                    onClick = { onSelectDay(date) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GymCalendarDay(
    date: LocalDate,
    sessions: List<GymSession>,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    withTrainer: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val trained = sessions.isNotEmpty()
    val gym = sessions.any { !it.isClass }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (trained) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 3.dp),
    ) {
        var circle = Modifier.size(34.dp).clip(CircleShape)
        circle = when {
            gym -> circle.background(colors.primary)
            trained -> circle.background(colors.primaryContainer)
            isToday -> circle.border(1.5.dp, colors.primary, CircleShape)
            else -> circle
        }
        if (withTrainer) circle = circle.border(2.5.dp, colors.tertiary, CircleShape)
        if (isSelected) circle = circle.border(2.dp, colors.onSurface, CircleShape)
        Box(modifier = circle, contentAlignment = Alignment.Center) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (trained || isToday) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    gym -> colors.onPrimary
                    trained -> colors.onPrimaryContainer
                    isToday -> colors.primary
                    isFuture -> colors.onSurface.copy(alpha = 0.35f)
                    else -> colors.onSurface
                },
            )
        }
        Text(
            text = (sessions.firstOrNull { !it.isClass } ?: sessions.firstOrNull())?.displayTitle().orEmpty(),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** The session's name: its own title, else the watch activity (Yoga, HIIT…) for a course. */
@Composable
internal fun GymSession.displayTitle(): String = title.ifBlank {
    activityType?.let { tech.mmarca.openvitals.features.activity.exerciseTypeLabel(it) }
        ?: stringResource(R.string.gym_session_untitled)
}

@Composable
internal fun GymSessionCard(
    session: GymSession,
    withTrainer: Boolean = false,
    trainerName: String = "",
    onToggleTrainer: (() -> Unit)? = null,
) {
    val locale = Locale.getDefault()
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = session.displayTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = session.start.format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", locale)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            session.activityType?.let { type ->
                Text(
                    text = tech.mmarca.openvitals.features.activity.exerciseTypeLabel(type).uppercase(),
                    style = DiaryType.smallLabel,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            val chips = buildList {
                session.durationMinutes?.let { add(stringResource(R.string.gym_minutes, it)) }
                session.caloriesKcal?.let { add(stringResource(R.string.gym_kcal, it.roundToInt())) }
                session.averageHeartRateBpm?.let { add(stringResource(R.string.gym_bpm, it)) }
                if (session.volumeKg > 0) add(stringResource(R.string.gym_volume, session.volumeKg.roundToInt()))
            }
            if (chips.isNotEmpty()) {
                Text(
                    text = chips.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (session.exercises.isEmpty() && !session.isClass) {
                Text(
                    text = stringResource(R.string.gym_session_no_sets),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            session.exercises.forEach { exercise ->
                Row {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    Text(
                        text = exercise.setsLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }
            if (onToggleTrainer != null && !session.isClass) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.gym_with_trainer, trainerName.ifBlank { stringResource(R.string.trainer_title) }),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (withTrainer) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = withTrainer, onCheckedChange = { onToggleTrainer() })
                }
            }
        }
    }
}

private fun ImportedExercise.setsLabel(): String {
    if (isCardio) {
        return sets.joinToString(", ") { set ->
            listOfNotNull(
                set.seconds?.let { if (it >= 60) "${(it + 30) / 60} min" else "$it s" },
                set.km?.let { "${it.kg()} km" },
            ).joinToString(" · ")
        }
    }
    val first = sets.firstOrNull() ?: return ""
    val same = sets.all { it.kg == first.kg && it.reps == first.reps }
    return if (same) {
        "${sets.size}×${first.reps ?: "?"}" + (first.kg?.takeIf { it > 0 }?.let { " @ ${it.kg()} kg" } ?: "")
    } else {
        sets.joinToString(", ") { set ->
            (set.kg?.takeIf { it > 0 }?.let { "${it.kg()}×" } ?: "") + (set.reps?.toString() ?: "?")
        }
    }
}

@Composable
internal fun GymProgressCard(
    exercises: List<String>,
    selected: String?,
    points: List<GymProgressPoint>,
    onSelect: (String) -> Unit,
) {
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (exercises.isEmpty()) {
                Text(
                    text = stringResource(R.string.gym_progress_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                exercises.forEach { name ->
                    FilterChip(
                        selected = name == selected,
                        onClick = { onSelect(name) },
                        label = { Text(name, maxLines = 1) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.gym_progress_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (points.isNotEmpty()) {
                val best = points.maxBy { it.oneRepMaxKg }
                val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) }
                Text(
                    text = stringResource(R.string.gym_progress_best, best.oneRepMaxKg.kg(), best.date.format(dateFormat)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                GymProgressChart(points)
                Row {
                    Text(
                        text = points.first().date.format(dateFormat),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = points.last().date.format(dateFormat),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun GymProgressChart(points: List<GymProgressPoint>, unit: String = "kg") {
    val line = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val minValue = points.minOf { it.oneRepMaxKg }
    val maxValue = points.maxOf { it.oneRepMaxKg }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.height(160.dp).padding(end = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${maxValue.kg()} $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${minValue.kg()} $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(modifier = Modifier.weight(1f).height(160.dp)) {
            val pad = 8.dp.toPx()
            val first = points.first().date.toEpochDay().toFloat()
            val span = (points.last().date.toEpochDay() - points.first().date.toEpochDay()).toFloat().coerceAtLeast(1f)
            val range = (maxValue - minValue).toFloat().coerceAtLeast(1f)
            fun x(p: GymProgressPoint) =
                if (points.size == 1) size.width / 2 else pad + (p.date.toEpochDay() - first) / span * (size.width - 2 * pad)
            fun y(p: GymProgressPoint) = pad + (1f - ((p.oneRepMaxKg - minValue).toFloat() / range)) * (size.height - 2 * pad)
            drawLine(grid, Offset(0f, pad), Offset(size.width, pad), strokeWidth = 1f)
            drawLine(grid, Offset(0f, size.height - pad), Offset(size.width, size.height - pad), strokeWidth = 1f)
            val path = Path()
            points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(x(p), y(p)) else path.lineTo(x(p), y(p))
            }
            if (points.size > 1) drawPath(path, line, style = Stroke(width = 2.5.dp.toPx()))
            points.forEachIndexed { i, p ->
                drawCircle(line, radius = if (i == points.lastIndex) 5.dp.toPx() else 3.dp.toPx(), center = Offset(x(p), y(p)))
            }
        }
    }
}

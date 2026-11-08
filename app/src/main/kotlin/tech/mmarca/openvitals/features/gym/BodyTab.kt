package tech.mmarca.openvitals.features.gym

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong
import tech.mmarca.openvitals.R
import tech.mmarca.openvitals.ui.components.OpenVitalsCard

internal fun Double.bodyNumber(): String {
    val rounded = (this * 100).roundToLong() / 100.0
    val text = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    return text.replace('.', ',')
}

private val dateFormat: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/** Diario Palestra: the scale reports, the body map with right and left, the trends. */
@Composable
fun BodyTab(viewModel: BodyViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    val messageText = state.message?.let { message ->
        when (message) {
            BodyMessage.SAVED -> stringResource(R.string.body_saved)
            BodyMessage.SAVED_APP_ONLY -> stringResource(R.string.body_saved_app_only)
            BodyMessage.DELETED -> stringResource(R.string.body_deleted)
            BodyMessage.IMPORTED -> stringResource(R.string.body_imported, state.importedCount)
            BodyMessage.IMPORT_FAILED -> stringResource(R.string.body_import_failed)
        }
    }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            Toast.makeText(context, messageText, Toast.LENGTH_LONG).show()
            viewModel.dismissMessage()
        }
    }
    if (state.isEditorOpen) {
        BodyEditorDialog(
            initial = state.editing,
            onDismiss = viewModel::closeEditor,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val latest = state.latest
        item {
            val labels = listOf(BodyField.WEIGHT, BodyField.FAT_PERCENT, BodyField.MUSCLE_MASS)
            DiaryHeroCard(
                title = stringResource(R.string.diario_tab_body),
                subtitle = latest?.let { stringResource(R.string.body_latest, it.date.format(dateFormat)) }
                    ?: stringResource(R.string.body_empty_short),
                stats = labels.mapNotNull { field ->
                    val change = BodyReports.change(state.reports, field) ?: return@mapNotNull null
                    val delta = change.delta?.takeIf { it != 0.0 }
                        ?.let { " · " + (if (it > 0) "+" else "−") + kotlin.math.abs(it).bodyNumber() }
                        .orEmpty()
                    "${change.value.bodyNumber()} ${field.unit}".trim() to stringResource(field.labelRes) + delta
                },
                action = { DiaryPill(stringResource(R.string.diary_add), Icons.Outlined.Add, viewModel::newReport) },
            )
        }
        if (latest == null) {
            if (!state.isLoading) item { DiaryNote(stringResource(R.string.body_empty)) }
            item {
                TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.body_import))
                }
            }
            return@LazyColumn
        }
        item { DiarySectionHeader(stringResource(R.string.body_details)) }
        item { BodySummaryCard(state.reports) }
        item {
            DiarySectionHeader(stringResource(R.string.body_map_title)) {
                DiaryChoice(
                    options = listOf(
                        BodyFieldGroup.CIRCUMFERENCE to stringResource(R.string.body_map_circumferences),
                        BodyFieldGroup.SEGMENTAL to stringResource(R.string.body_map_segmental),
                    ),
                    selected = state.mapGroup,
                    onSelect = viewModel::showMap,
                )
            }
        }
        item { BodyMapCard(state.reports, state.mapGroup) }
        item { DiarySectionHeader(stringResource(R.string.body_trend_title)) }
        item { BodyTrendCard(state.reports, state.trendField, viewModel::showTrend) }
        item {
            DiarySectionHeader(stringResource(R.string.body_history)) {
                TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.body_import))
                }
            }
        }
        items(state.reports, key = { it.date.toString() }) { report ->
            OpenVitalsCard(modifier = Modifier.fillMaxWidth().clickable { viewModel.edit(report) }) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = report.date.format(dateFormat),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = listOfNotNull(
                            report[BodyField.WEIGHT]?.let { "${it.bodyNumber()} kg" },
                            report[BodyField.FAT_PERCENT]?.let { "${it.bodyNumber()} %" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun deltaColor(field: BodyField, delta: Double): Color = when {
    delta == 0.0 || field.goodUp == null -> MaterialTheme.colorScheme.onSurfaceVariant
    (delta > 0) == field.goodUp -> Color(0xFF3FA66B)
    else -> Color(0xFFD08A2E)
}

@Composable
private fun DeltaText(field: BodyField, change: BodyChange?) {
    val delta = change?.delta ?: return
    val previousDate = change.previousDate ?: return
    Text(
        text = stringResource(
            R.string.body_delta,
            (if (delta > 0) "+" else if (delta < 0) "−" else "") + abs(delta).bodyNumber(),
            previousDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())),
        ),
        style = MaterialTheme.typography.labelSmall,
        color = deltaColor(field, delta),
    )
}

@Composable
private fun BodySummaryCard(reports: List<BodyReport>) {
    val fields = listOf(
        BodyField.SKELETAL_MUSCLE, BodyField.LEAN_MASS, BodyField.WATER,
        BodyField.VISCERAL, BodyField.BMR, BodyField.SCORE,
    )
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            fields.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { field ->
                        val change = BodyReports.change(reports, field)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(field.labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Text(
                                text = change?.let { it.value.bodyNumber() + if (field.unit.isNotEmpty()) " ${field.unit}" else "" } ?: "—",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            DeltaText(field, change)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyMapCard(reports: List<BodyReport>, group: BodyFieldGroup) {
    val segmental = group == BodyFieldGroup.SEGMENTAL
    @Composable
    fun Cell(field: BodyField, label: Int, align: Alignment.Horizontal) {
        val change = BodyReports.change(reports, field)
        Column(horizontalAlignment = align, modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = change?.let { "${it.value.bodyNumber()} ${field.unit}" } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            DeltaText(field, change)
        }
    }
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val idle = MaterialTheme.colorScheme.surfaceContainerHighest
    val body = MaterialTheme.colorScheme.outlineVariant
    val segmentalGroups = setOf(
        MuscleGroup.BICEPS, MuscleGroup.TRICEPS, MuscleGroup.FOREARMS, MuscleGroup.SHOULDERS,
        MuscleGroup.QUADS, MuscleGroup.ADDUCTORS, MuscleGroup.CALVES,
        MuscleGroup.CHEST, MuscleGroup.ABS, MuscleGroup.OBLIQUES,
    )
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!segmental) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Cell(BodyField.NECK, R.string.body_field_neck, Alignment.CenterHorizontally)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    if (segmental) {
                        Cell(BodyField.SEG_ARM_RIGHT, R.string.body_field_arm_right, Alignment.End)
                        Cell(BodyField.SEG_LEG_RIGHT, R.string.body_field_leg_right, Alignment.End)
                    } else {
                        Cell(BodyField.ARM_RIGHT, R.string.body_field_arm_right, Alignment.End)
                        Cell(BodyField.CHEST, R.string.body_field_chest, Alignment.End)
                        Cell(BodyField.THIGH_RIGHT, R.string.body_field_thigh_right, Alignment.End)
                    }
                }
                Column(modifier = Modifier.weight(0.9f).padding(horizontal = 8.dp)) {
                    BodyFigure(
                        outlines = frontOutlines,
                        colorOf = { g ->
                            when {
                                g == null -> body
                                segmental && g in segmentalGroups -> highlight
                                else -> idle
                            }
                        },
                        gap = MaterialTheme.colorScheme.surfaceContainer,
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    if (segmental) {
                        Cell(BodyField.SEG_ARM_LEFT, R.string.body_field_arm_left, Alignment.Start)
                        Cell(BodyField.SEG_LEG_LEFT, R.string.body_field_leg_left, Alignment.Start)
                    } else {
                        Cell(BodyField.ARM_LEFT, R.string.body_field_arm_left, Alignment.Start)
                        Cell(BodyField.WAIST, R.string.body_field_waist, Alignment.Start)
                        Cell(BodyField.THIGH_LEFT, R.string.body_field_thigh_left, Alignment.Start)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (segmental) {
                    Cell(BodyField.SEG_TRUNK, R.string.body_field_trunk, Alignment.CenterHorizontally)
                } else {
                    Cell(BodyField.HIP, R.string.body_field_hip, Alignment.CenterHorizontally)
                }
            }
            Text(
                text = stringResource(R.string.body_map_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BodyTrendCard(reports: List<BodyReport>, field: BodyField, onField: (BodyField) -> Unit) {
    val choices = listOf(
        BodyField.WEIGHT, BodyField.FAT_PERCENT, BodyField.MUSCLE_MASS, BodyField.SKELETAL_MUSCLE,
        BodyField.WAIST, BodyField.VISCERAL, BodyField.BMR,
    )
    val series = BodyReports.series(reports, field)
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                choices.forEach { choice ->
                    FilterChip(
                        selected = choice == field,
                        onClick = { onField(choice) },
                        label = { Text(stringResource(choice.labelRes), maxLines = 1) },
                    )
                }
            }
            if (series.size < 2) {
                Text(
                    text = stringResource(R.string.body_trend_needs_two),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                GymProgressChart(series.map { (date, value) -> GymProgressPoint(date, value) }, unit = field.unit)
                Row {
                    Text(
                        text = series.first().first.format(dateFormat),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = series.last().first.format(dateFormat),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

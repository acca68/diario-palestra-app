package tech.mmarca.openvitals.features.gym

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.mmarca.openvitals.R
import tech.mmarca.openvitals.core.presentation.resolve
import tech.mmarca.openvitals.ui.components.FullScreenLoading
import tech.mmarca.openvitals.ui.components.OpenVitalsCard

/** Diario Palestra: calendar, sessions with their sets, and progress per exercise. */
@Composable
fun GymScreen(viewModel: GymViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importHevyCsv)
    }

    val importText = state.importMessage?.let { message ->
        when (message) {
            is GymImportMessage.Done -> stringResource(R.string.gym_import_done, message.workouts, message.sets, message.added)
            GymImportMessage.NotHevy -> stringResource(R.string.gym_import_not_hevy)
            GymImportMessage.Failed -> stringResource(R.string.gym_import_failed)
        }
    }
    LaunchedEffect(importText) {
        if (importText != null) {
            Toast.makeText(context, importText, Toast.LENGTH_LONG).show()
            viewModel.dismissImportMessage()
        }
    }

    if (state.isLoading && state.sessions.isEmpty()) {
        FullScreenLoading()
        return
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { GymStatsRow(state.stats) }
            item {
                GymImportCard(
                    importedCount = state.importedCount,
                    isImporting = state.isImporting,
                    onImport = { picker.launch(arrayOf("*/*")) },
                )
            }
            state.error?.let { error ->
                item {
                    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = error.resolve() ?: stringResource(R.string.unknown_error),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            item {
                GymCalendarCard(
                    month = state.month,
                    sessionsByDay = state.sessionsByDay,
                    selectedDay = state.selectedDay,
                    onPreviousMonth = viewModel::previousMonth,
                    onNextMonth = viewModel::nextMonth,
                    onSelectDay = viewModel::selectDay,
                )
            }
            if (state.selectedSessions.isNotEmpty()) {
                items(state.selectedSessions, key = { "sel-" + it.key }) { session ->
                    GymSessionCard(session)
                }
            }
            item {
                GymMuscleCard(
                    periodDays = state.musclePeriodDays,
                    setsPerMuscle = state.setsPerMuscle,
                    trainedDays = state.trainedDays,
                    onPeriod = viewModel::selectMusclePeriod,
                )
            }
            item {
                GymProgressCard(
                    exercises = state.progressExercises,
                    selected = state.selectedExercise,
                    points = state.progress,
                    onSelect = viewModel::selectExercise,
                )
            }
            if (state.sessions.isEmpty()) {
                item { GymEmptyCard() }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.gym_recent_sessions),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(state.sessions.take(RECENT_SESSIONS), key = { it.key }) { session ->
                    GymSessionCard(session)
                }
            }
        }
    }
}

private const val RECENT_SESSIONS = 20

@Composable
private fun GymStatsRow(stats: GymStats) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GymStat(stats.weekStreak.toString(), stringResource(R.string.gym_stat_week_streak), Modifier.weight(1f))
        GymStat(stats.restDays?.toString() ?: "–", stringResource(R.string.gym_stat_rest_days), Modifier.weight(1f))
        GymStat(stats.sessionsThisMonth.toString(), stringResource(R.string.gym_stat_month), Modifier.weight(1f))
    }
}

@Composable
private fun GymStat(value: String, label: String, modifier: Modifier) {
    OpenVitalsCard(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GymImportCard(importedCount: Int, isImporting: Boolean, onImport: () -> Unit) {
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.gym_import_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (importedCount > 0) {
                        stringResource(R.string.gym_import_count, importedCount)
                    } else {
                        stringResource(R.string.gym_import_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            } else {
                OutlinedButton(onClick = onImport) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.gym_import_button))
                }
            }
        }
    }
}

@Composable
private fun GymEmptyCard() {
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.gym_empty_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.gym_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

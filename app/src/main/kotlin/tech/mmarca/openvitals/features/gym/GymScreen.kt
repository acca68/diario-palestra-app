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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    // With more trainers, switching "Con trainer" on asks who gave the lesson.
    var choosingFor by remember { mutableStateOf<GymSession?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    choosingFor?.let { session ->
        AlertDialog(
            onDismissRequest = { choosingFor = null },
            title = { Text(stringResource(R.string.trainer_who)) },
            text = {
                Column {
                    state.trainers.forEach { name ->
                        TextButton(
                            onClick = {
                                viewModel.toggleTrainer(session, name)
                                choosingFor = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(name, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingFor = null }) { Text(stringResource(R.string.body_cancel)) } },
        )
    }
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
            item {
                DiaryHeroCard(
                    title = stringResource(R.string.diario_tab_gym),
                    subtitle = if (state.importedCount > 0) {
                        stringResource(R.string.gym_import_count, state.importedCount)
                    } else {
                        stringResource(R.string.gym_import_hint)
                    },
                    stats = listOf(
                        state.stats.weekStreak.toString() to stringResource(R.string.gym_stat_week_streak),
                        (state.stats.restDays?.toString() ?: "–") to stringResource(R.string.gym_stat_rest_days),
                        state.stats.sessionsThisMonth.toString() to stringResource(R.string.gym_stat_month),
                    ),
                    action = {
                        if (state.isImporting) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = DiaryColors.onHero)
                        } else {
                            DiaryPill(stringResource(R.string.gym_import_short), Icons.Outlined.FileUpload) { picker.launch(arrayOf("*/*")) }
                        }
                    },
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
            item { DiarySectionHeader(stringResource(R.string.gym_section_summary)) }
            item { GymSummaryCard(state.period, viewModel::selectPeriod, state.summary) }
            item { DiarySectionHeader(stringResource(R.string.gym_section_streak)) }
            item { GymStreakCard(state.stats.weekStreak, maxOf(state.bestStreak, state.stats.weekStreak), state.milestone) }
            if (state.challenges.isNotEmpty()) {
                item {
                    DiarySectionHeader(
                        stringResource(
                            R.string.gym_section_challenges,
                            java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("LLLL", java.util.Locale.getDefault())),
                        ),
                    )
                }
                item { GymChallengesCard(state.challenges) }
            }
            if (state.recovery.isNotEmpty()) {
                item { DiarySectionHeader(stringResource(R.string.gym_section_recovery)) }
                item { GymRecoveryCard(state.recovery) }
            }
            item { DiarySectionHeader(stringResource(R.string.diary_section_calendar)) }
            item {
                GymCalendarCard(
                    month = state.month,
                    sessionsByDay = state.sessionsByDay,
                    selectedDay = state.selectedDay,
                    trainerDays = state.trainerDays,
                    onPreviousMonth = viewModel::previousMonth,
                    onNextMonth = viewModel::nextMonth,
                    onSelectDay = viewModel::selectDay,
                )
            }
            if (state.selectedSessions.isNotEmpty()) {
                items(state.selectedSessions, key = { "sel-" + it.key }) { session ->
                    GymSessionCard(
                        session = session,
                        withTrainer = session.date in state.trainerDays,
                        trainerName = state.trainerByDay[session.date]
                            ?: state.trainers.singleOrNull().orEmpty(),
                        onToggleTrainer = {
                            if (session.date !in state.trainerDays && state.trainers.size > 1) {
                                choosingFor = session
                            } else {
                                viewModel.toggleTrainer(session)
                            }
                        },
                    )
                }
            }
            item {
                DiarySectionHeader(stringResource(R.string.gym_muscles_title)) {
                    DiaryChoice(
                        options = listOf(
                            7 to stringResource(R.string.gym_muscles_days, 7),
                            30 to stringResource(R.string.gym_muscles_days, 30),
                        ),
                        selected = state.musclePeriodDays,
                        onSelect = viewModel::selectMusclePeriod,
                    )
                }
            }
            item {
                GymMuscleCard(
                    periodDays = state.musclePeriodDays,
                    setsPerMuscle = state.setsPerMuscle,
                    trainedDays = state.trainedDays,
                )
            }
            item { DiarySectionHeader(stringResource(R.string.gym_progress_title)) }
            item {
                GymProgressCard(
                    exercises = state.progressExercises,
                    selected = state.selectedExercise,
                    points = state.progress,
                    onSelect = viewModel::selectExercise,
                )
            }
            item { DiarySectionHeader(stringResource(R.string.gym_recent_sessions)) }
            if (state.sessions.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(R.string.gym_search_hint)) },
                    )
                }
            }
            val found = GymInsights.search(state.sessions, query)
            if (state.sessions.isEmpty()) {
                item { GymEmptyCard() }
            } else if (found.isEmpty()) {
                item { DiaryNote(stringResource(R.string.gym_search_none)) }
            } else {
                items(found.take(if (query.isBlank()) RECENT_SESSIONS else SEARCH_RESULTS), key = { it.key }) { session ->
                    GymSessionCard(
                        session = session,
                        withTrainer = session.date in state.trainerDays,
                        trainerName = state.trainerByDay[session.date]
                            ?: state.trainers.singleOrNull().orEmpty(),
                        onToggleTrainer = {
                            if (session.date !in state.trainerDays && state.trainers.size > 1) {
                                choosingFor = session
                            } else {
                                viewModel.toggleTrainer(session)
                            }
                        },
                    )
                }
            }
        }
    }
}

private const val RECENT_SESSIONS = 20
private const val SEARCH_RESULTS = 100

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

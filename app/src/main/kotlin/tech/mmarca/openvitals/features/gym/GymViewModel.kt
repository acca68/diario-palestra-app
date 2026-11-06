package tech.mmarca.openvitals.features.gym

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.mmarca.openvitals.core.presentation.ScreenError
import tech.mmarca.openvitals.core.presentation.toScreenError
import tech.mmarca.openvitals.data.repository.contract.ActivityRepository

sealed interface GymImportMessage {
    data class Done(val workouts: Int, val sets: Int, val added: Int) : GymImportMessage
    data object NotHevy : GymImportMessage
    data object Failed : GymImportMessage
}

@Immutable
data class GymUiState(
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val sessions: List<GymSession> = emptyList(),
    val stats: GymStats = GymStats(),
    val month: YearMonth = YearMonth.now(),
    val selectedDay: LocalDate? = null,
    val progressExercises: List<String> = emptyList(),
    val selectedExercise: String? = null,
    val progress: List<GymProgressPoint> = emptyList(),
    val importedCount: Int = 0,
    val musclePeriodDays: Int = 7,
    val setsPerMuscle: Map<MuscleGroup, Double> = emptyMap(),
    val importMessage: GymImportMessage? = null,
    val error: ScreenError? = null,
) {
    val sessionsByDay: Map<LocalDate, List<GymSession>> by lazy { sessions.groupBy { it.date } }
    val trainedDays: Set<LocalDate> by lazy { sessions.map { it.date }.toSet() }
    val selectedSessions: List<GymSession>
        get() = selectedDay?.let { sessionsByDay[it] }.orEmpty()
}

@HiltViewModel
class GymViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val importStore: GymImportStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GymUiState())
    val uiState: StateFlow<GymUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val today = LocalDate.now()
                val imported = importStore.load()
                val firstImport = imported.minOfOrNull { it.start.toLocalDate() }
                // Health Connect from the first imported workout, at most two years back.
                val from = listOfNotNull(firstImport, today.minusDays(HEALTH_CONNECT_DAYS))
                    .max()
                    .coerceAtMost(today.minusDays(MIN_HEALTH_CONNECT_DAYS))
                val workouts = activityRepository.loadWorkoutsWithMetrics(from, today)
                withContext(Dispatchers.Default) {
                    GymSessions.build(workouts, imported) to imported.size
                }
            }.onSuccess { (sessions, importedCount) ->
                _uiState.update { state ->
                    val exercises = GymSessions.progressExercises(sessions)
                    val selected = state.selectedExercise?.takeIf { it in exercises } ?: exercises.firstOrNull()
                    state.copy(
                        isLoading = false,
                        sessions = sessions,
                        stats = GymSessions.stats(sessions),
                        progressExercises = exercises,
                        selectedExercise = selected,
                        progress = selected?.let { GymSessions.progress(sessions, it) }.orEmpty(),
                        importedCount = importedCount,
                        setsPerMuscle = musclesFor(sessions, state.musclePeriodDays),
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.toScreenError()) }
            }
        }
    }

    fun importHevyCsv(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importMessage = null) }
            val message = try {
                val summary = importStore.importCsv(uri)
                GymImportMessage.Done(summary.workouts, summary.sets, summary.added)
            } catch (e: HevyCsvParser.FormatException) {
                GymImportMessage.NotHevy
            } catch (e: Exception) {
                GymImportMessage.Failed
            }
            _uiState.update { it.copy(isImporting = false, importMessage = message) }
            load()
        }
    }

    fun selectMusclePeriod(days: Int) = _uiState.update { state ->
        state.copy(musclePeriodDays = days, setsPerMuscle = musclesFor(state.sessions, days))
    }

    private fun musclesFor(sessions: List<GymSession>, days: Int): Map<MuscleGroup, Double> {
        val today = LocalDate.now()
        return GymMuscles.setsPerMuscle(sessions, today.minusDays(days - 1L), today)
    }

    fun dismissImportMessage() = _uiState.update { it.copy(importMessage = null) }

    fun previousMonth() = _uiState.update { it.copy(month = it.month.minusMonths(1)) }

    fun nextMonth() = _uiState.update { state ->
        if (state.month < YearMonth.now()) state.copy(month = state.month.plusMonths(1)) else state
    }

    fun selectDay(day: LocalDate) = _uiState.update { state ->
        state.copy(selectedDay = if (state.selectedDay == day) null else day)
    }

    fun selectExercise(name: String) = _uiState.update { state ->
        state.copy(selectedExercise = name, progress = GymSessions.progress(state.sessions, name))
    }

    private companion object {
        const val HEALTH_CONNECT_DAYS = 730L
        const val MIN_HEALTH_CONNECT_DAYS = 90L
    }
}

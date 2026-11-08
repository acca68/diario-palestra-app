package tech.mmarca.openvitals.features.gym

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tech.mmarca.openvitals.data.repository.contract.ActivityRepository
import tech.mmarca.openvitals.data.repository.contract.NutritionRepository

/**
 * Diario Palestra: one file with everything the Diario keeps.
 *
 * - `files`: the app's own archives as they are on disk (Hevy sets, body reports,
 *   trainer, nutritionist). Restoring writes them back.
 * - `pc`: what the web Diario on the computer reads: the gym sessions already joined
 *   with the watch, and the daily meals from Health Connect.
 */
@Singleton
class DiaryBackup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val importStore: GymImportStore,
    private val activityRepository: ActivityRepository,
    private val nutritionRepository: NutritionRepository,
) {
    class RestoreSummary(val files: Int)
    class FormatException : IllegalArgumentException("not a Diario backup")

    private val archives = mapOf(
        "hevy" to "diario_palestra_hevy.json",
        "body" to BodyReportStore.FILE_NAME,
        "trainer" to "diario_palestra_trainer.json",
        "nutrition" to "diario_palestra_nutrizionista.json",
    )

    suspend fun export(uri: Uri) {
        val files = buildJsonObject {
            archives.forEach { (key, name) -> DiaryJsonFile(context, name).read()?.let { put(key, it) } }
        }
        val today = LocalDate.now()
        val imported = importStore.load()
        val from = (imported.minOfOrNull { it.start.toLocalDate() } ?: today.minusDays(PC_DAYS))
            .coerceAtLeast(today.minusDays(MAX_DAYS))
            .coerceAtMost(today.minusDays(PC_DAYS))
        val sessions = runCatching {
            GymSessions.build(activityRepository.loadWorkoutsWithMetrics(from, today), imported)
        }.getOrElse { GymSessions.build(emptyList(), imported) }
        val days = runCatching { nutritionRepository.loadDailyMacros(today.minusDays(PC_DAYS), today) }.getOrDefault(emptyList())

        val json = buildJsonObject {
            put("type", TYPE)
            put("version", 1)
            put("exportedAt", Instant.now().toString())
            put("files", files)
            put(
                "pc",
                buildJsonObject {
                    put(
                        "sessions",
                        buildJsonArray {
                            sessions.forEach { s ->
                                add(
                                    buildJsonObject {
                                        put("key", s.key)
                                        put("title", s.title)
                                        put("date", s.date.toString())
                                        put("start", s.start.toString())
                                        put("minutes", s.durationMinutes)
                                        put("kcal", s.caloriesKcal)
                                        put("bpm", s.averageHeartRateBpm)
                                        put(
                                            "exercises",
                                            buildJsonArray {
                                                s.exercises.forEach { e ->
                                                    add(
                                                        buildJsonObject {
                                                            put("name", e.name)
                                                            put(
                                                                "sets",
                                                                buildJsonArray {
                                                                    e.sets.forEach { set ->
                                                                        add(
                                                                            buildJsonObject {
                                                                                put("kg", set.kg)
                                                                                put("reps", set.reps)
                                                                                put("km", set.km)
                                                                                put("sec", set.seconds)
                                                                            },
                                                                        )
                                                                    }
                                                                },
                                                            )
                                                        },
                                                    )
                                                }
                                            },
                                        )
                                    },
                                )
                            }
                        },
                    )
                    put(
                        "days",
                        buildJsonArray {
                            days.filter { it.energyKcal > 0 }.forEach { d ->
                                add(
                                    buildJsonObject {
                                        put("date", d.date.toString())
                                        put("kcal", d.energyKcal)
                                        put("protein", d.proteinGrams)
                                        put("carbs", d.carbsGrams)
                                        put("fat", d.fatGrams)
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toString().toByteArray()) }
                ?: error("cannot write")
        }
    }

    suspend fun restore(uri: Uri): RestoreSummary {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } ?: throw FormatException()
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw FormatException()
        if (root.str("type") != TYPE) throw FormatException()
        val files = root["files"] as? JsonObject ?: throw FormatException()
        var restored = 0
        archives.forEach { (key, name) ->
            val content = files[key] as? JsonObject ?: return@forEach
            DiaryJsonFile(context, name).write(content)
            restored++
        }
        return RestoreSummary(restored)
    }

    private companion object {
        const val TYPE = "diario-palestra-backup"
        const val PC_DAYS = 180L
        const val MAX_DAYS = 730L
    }
}

sealed interface BackupMessage {
    data object Exported : BackupMessage
    data class Restored(val files: Int) : BackupMessage
    data object NotBackup : BackupMessage
    data object Failed : BackupMessage
}

data class BackupUiState(val busy: Boolean = false, val message: BackupMessage? = null)

@HiltViewModel
class DiaryBackupViewModel @Inject constructor(private val backup: DiaryBackup) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun export(uri: Uri) = launchBackup { backup.export(uri); BackupMessage.Exported }

    fun restore(uri: Uri, onRestored: () -> Unit) = launchBackup {
        val summary = backup.restore(uri)
        onRestored()
        BackupMessage.Restored(summary.files)
    }

    fun dismiss() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun launchBackup(block: suspend () -> BackupMessage) {
        viewModelScope.launch {
            _uiState.value = BackupUiState(busy = true)
            val message = try {
                block()
            } catch (e: DiaryBackup.FormatException) {
                BackupMessage.NotBackup
            } catch (e: Exception) {
                BackupMessage.Failed
            }
            _uiState.value = BackupUiState(busy = false, message = message)
        }
    }
}

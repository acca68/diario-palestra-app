package tech.mmarca.openvitals.features.gym

import android.content.Context
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Diario Palestra: a block of lessons bought from the personal trainer. */
@Immutable
data class TrainerPackage(
    val id: String,
    val date: LocalDate,
    val lessons: Int,
    val amountChf: Double? = null,
    val note: String? = null,
)

/** One lesson; [extra] lessons are paid apart and do not use up the package. */
@Immutable
data class TrainerLesson(
    val id: String,
    val date: LocalDate,
    val time: LocalTime? = null,
    val topic: String? = null,
    val durationMinutes: Int? = null,
    val costChf: Double? = null,
    val extra: Boolean = false,
    val note: String? = null,
)

@Immutable
data class TrainerData(
    val trainerName: String = "",
    val packages: List<TrainerPackage> = emptyList(),
    val lessons: List<TrainerLesson> = emptyList(),
) {
    val bought: Int get() = packages.sumOf { it.lessons }
    val used: Int get() = lessons.count { !it.extra }
    val remaining: Int? get() = if (packages.isEmpty()) null else bought - used
    val spentChf: Double get() = packages.sumOf { it.amountChf ?: 0.0 } + lessons.sumOf { it.costChf ?: 0.0 }
    val lessonDates: Set<LocalDate> get() = lessons.map { it.date }.toSet()
}

private const val DEFAULT_TRAINER = "Eddy"

@Singleton
class TrainerStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val file = DiaryJsonFile(context, "diario_palestra_trainer.json")
    private val mutex = Mutex()

    suspend fun load(): TrainerData = mutex.withLock { readUnlocked() }

    suspend fun update(change: (TrainerData) -> TrainerData) = mutex.withLock {
        write(change(readUnlocked()))
    }

    private suspend fun readUnlocked(): TrainerData {
        val root = file.read() as? JsonObject ?: return TrainerData(trainerName = DEFAULT_TRAINER)
        return TrainerData(
            trainerName = root.str("trainer").orEmpty(),
            packages = (root["packages"] as? JsonArray).orEmpty().mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                TrainerPackage(
                    id = o.str("id") ?: return@mapNotNull null,
                    date = o.date("date") ?: return@mapNotNull null,
                    lessons = o.num("lessons")?.toInt() ?: return@mapNotNull null,
                    amountChf = o.num("amount"),
                    note = o.str("note"),
                )
            },
            lessons = (root["lessons"] as? JsonArray).orEmpty().mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                TrainerLesson(
                    id = o.str("id") ?: return@mapNotNull null,
                    date = o.date("date") ?: return@mapNotNull null,
                    time = o.time("time"),
                    topic = o.str("topic"),
                    durationMinutes = o.num("minutes")?.toInt(),
                    costChf = o.num("cost"),
                    extra = o.bool("extra"),
                    note = o.str("note"),
                )
            },
        )
    }

    private suspend fun write(data: TrainerData) {
        file.write(
            buildJsonObject {
                put("version", 1)
                put("trainer", data.trainerName)
                put(
                    "packages",
                    buildJsonArray {
                        data.packages.sortedBy { it.date }.forEach { p ->
                            add(
                                buildJsonObject {
                                    put("id", p.id)
                                    put("date", p.date.toString())
                                    put("lessons", p.lessons)
                                    put("amount", p.amountChf)
                                    put("note", p.note)
                                },
                            )
                        }
                    },
                )
                put(
                    "lessons",
                    buildJsonArray {
                        data.lessons.sortedBy { it.date }.forEach { l ->
                            add(
                                buildJsonObject {
                                    put("id", l.id)
                                    put("date", l.date.toString())
                                    put("time", l.time?.toString())
                                    put("topic", l.topic)
                                    put("minutes", l.durationMinutes)
                                    put("cost", l.costChf)
                                    put("extra", l.extra)
                                    put("note", l.note)
                                },
                            )
                        }
                    },
                )
            },
        )
    }
}

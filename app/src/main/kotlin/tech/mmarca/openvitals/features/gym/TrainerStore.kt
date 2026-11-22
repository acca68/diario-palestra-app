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
    /** Who sold the package; null in files from before several trainers: the first one. */
    val trainer: String? = null,
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
    /** Who gave the lesson; null in files from before several trainers: the first one. */
    val trainer: String? = null,
)

@Immutable
data class TrainerData(
    /** The trainers, the first one being the usual one. */
    val trainers: List<String> = listOf(DEFAULT_TRAINER),
    val packages: List<TrainerPackage> = emptyList(),
    val lessons: List<TrainerLesson> = emptyList(),
) {
    val trainerName: String get() = trainers.firstOrNull().orEmpty()

    fun trainerOf(lesson: TrainerLesson): String = lesson.trainer ?: trainerName

    fun trainerOf(pack: TrainerPackage): String = pack.trainer ?: trainerName

    /** The figures of one trainer, or of all of them when [name] is null. */
    fun account(name: String?): TrainerAccount {
        val packs = packages.filter { name == null || trainerOf(it) == name }
        val done = lessons.filter { name == null || trainerOf(it) == name }
        val bought = packs.sumOf { it.lessons }
        val used = done.count { !it.extra }
        return TrainerAccount(
            bought = bought,
            used = used,
            remaining = if (packs.isEmpty()) null else bought - used,
            spentChf = packs.sumOf { it.amountChf ?: 0.0 } + done.sumOf { it.costChf ?: 0.0 },
        )
    }

    val bought: Int get() = account(null).bought
    val used: Int get() = account(null).used
    val remaining: Int? get() = account(null).remaining
    val spentChf: Double get() = account(null).spentChf
    val lessonDates: Set<LocalDate> get() = lessons.map { it.date }.toSet()

    /** Who gave the lesson of each day (the first one when there are more). */
    val trainerByDay: Map<LocalDate, String>
        get() = lessons.sortedBy { it.time }.groupBy { it.date }.mapValues { (_, l) -> trainerOf(l.first()) }

    /** Gives a new name to a trainer, in the list and in every lesson and package. */
    fun rename(old: String, new: String): TrainerData {
        if (new.isBlank() || new == old) return this
        return copy(
            trainers = trainers.map { if (it == old) new else it }.distinct(),
            packages = packages.map { if (trainerOf(it) == old) it.copy(trainer = new) else it },
            lessons = lessons.map { if (trainerOf(it) == old) it.copy(trainer = new) else it },
        )
    }

    /** True when the trainer has neither lessons nor packages, so it can be removed. */
    fun isUnused(name: String): Boolean =
        packages.none { trainerOf(it) == name } && lessons.none { trainerOf(it) == name }

    /**
     * "Con Eddy" on a gym session: removes the lesson on that day (the one with the session's
     * title first), or records a new one with the session's date, time, title and duration,
     * given by [trainer] (the usual one when null).
     */
    fun toggleLessonFor(session: GymSession, newId: String, trainer: String? = null): TrainerData {
        val sameDay = lessons.filter { it.date == session.date }
        if (sameDay.isNotEmpty()) {
            val target = sameDay.firstOrNull { it.topic == session.title } ?: sameDay.first()
            return copy(lessons = lessons - target)
        }
        val lesson = TrainerLesson(
            id = newId,
            date = session.date,
            time = session.start.toLocalTime().withSecond(0).withNano(0),
            topic = session.title.takeIf { it.isNotBlank() },
            durationMinutes = session.durationMinutes?.toInt(),
            trainer = trainer ?: trainerName,
        )
        return copy(lessons = lessons + lesson)
    }
}

@Immutable
data class TrainerAccount(val bought: Int, val used: Int, val remaining: Int?, val spentChf: Double)

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
        val root = file.read() as? JsonObject ?: return TrainerData()
        val names = (root["trainers"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim()?.takeIf { n -> n.isNotEmpty() } }
            .ifEmpty { listOf(root.str("trainer") ?: DEFAULT_TRAINER) }
        return TrainerData(
            trainers = names.distinct(),
            packages = (root["packages"] as? JsonArray).orEmpty().mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                TrainerPackage(
                    id = o.str("id") ?: return@mapNotNull null,
                    date = o.date("date") ?: return@mapNotNull null,
                    lessons = o.num("lessons")?.toInt() ?: return@mapNotNull null,
                    amountChf = o.num("amount"),
                    note = o.str("note"),
                    trainer = o.str("by"),
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
                    trainer = o.str("by"),
                )
            },
        )
    }

    private suspend fun write(data: TrainerData) {
        file.write(
            buildJsonObject {
                put("version", 2)
                // "trainer" stays for older readers (the web Diario): the usual trainer.
                put("trainer", data.trainerName)
                put("trainers", buildJsonArray { data.trainers.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
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
                                    put("by", p.trainer)
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
                                    put("by", l.trainer)
                                },
                            )
                        }
                    },
                )
            },
        )
    }
}

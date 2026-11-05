package tech.mmarca.openvitals.features.gym

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Diario Palestra: the workouts imported from Hevy's CSV, kept in one JSON file
 * in the app's private storage. Health Connect cannot hold sets and loads, so
 * this is data the app owns. A new import replaces workouts with the same start
 * minute, so importing the whole export again never duplicates a session.
 */
@Singleton
class GymImportStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    class ImportSummary(val workouts: Int, val sets: Int, val added: Int)

    private val mutex = Mutex()
    private val file: File get() = File(context.filesDir, FILE_NAME)

    suspend fun load(): List<ImportedWorkout> = withContext(Dispatchers.IO) {
        mutex.withLock { readUnlocked() }
    }

    suspend fun importCsv(uri: Uri): ImportSummary = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw HevyCsvParser.FormatException("unreadable")
        val parsed = HevyCsvParser.parse(text)
        if (parsed.workouts.isEmpty()) throw HevyCsvParser.FormatException("no_workouts")
        mutex.withLock {
            val existing = readUnlocked().associateBy { it.key }
            val merged = LinkedHashMap(existing)
            parsed.workouts.forEach { merged[it.key] = it }
            writeUnlocked(merged.values.sortedBy { it.start })
            ImportSummary(
                workouts = parsed.workouts.size,
                sets = parsed.setCount,
                added = parsed.workouts.count { it.key !in existing },
            )
        }
    }

    private fun readUnlocked(): List<ImportedWorkout> {
        val f = file
        if (!f.exists()) return emptyList()
        return runCatching {
            Json.parseToJsonElement(f.readText()).jsonObject["workouts"]?.jsonArray
                ?.mapNotNull { it.toWorkout() }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun writeUnlocked(workouts: List<ImportedWorkout>) {
        val json = buildJsonObject {
            put("version", 1)
            put("workouts", buildJsonArray { workouts.forEach { add(it.toJson()) } })
        }
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(json.toString())
            tmp.delete()
        }
    }

    private fun ImportedWorkout.toJson(): JsonObject = buildJsonObject {
        put("title", title)
        put("start", start.toString())
        put("end", end?.toString())
        put(
            "exercises",
            buildJsonArray {
                exercises.forEach { exercise ->
                    add(
                        buildJsonObject {
                            put("name", exercise.name)
                            put("sets", buildJsonArray { exercise.sets.forEach { add(it.toJson()) } })
                        },
                    )
                }
            },
        )
    }

    private fun ImportedSet.toJson(): JsonObject = buildJsonObject {
        put("kg", kg)
        put("reps", reps)
        put("km", km)
        put("sec", seconds)
        put("rpe", rpe)
        put("type", type)
    }

    private fun JsonElement.toWorkout(): ImportedWorkout? {
        val obj = this as? JsonObject ?: return null
        val start = obj.string("start")?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return null
        return ImportedWorkout(
            title = obj.string("title").orEmpty(),
            start = start,
            end = obj.string("end")?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
            exercises = (obj["exercises"] as? JsonArray).orEmpty().mapNotNull { element ->
                val e = element as? JsonObject ?: return@mapNotNull null
                ImportedExercise(
                    name = e.string("name") ?: return@mapNotNull null,
                    sets = (e["sets"] as? JsonArray).orEmpty().mapNotNull { s ->
                        val set = s as? JsonObject ?: return@mapNotNull null
                        ImportedSet(
                            kg = set.double("kg"),
                            reps = set.int("reps"),
                            km = set.double("km"),
                            seconds = set.int("sec"),
                            rpe = set.double("rpe"),
                            type = set.string("type"),
                        )
                    },
                )
            },
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull

    private companion object {
        const val FILE_NAME = "diario_palestra_hevy.json"
    }
}

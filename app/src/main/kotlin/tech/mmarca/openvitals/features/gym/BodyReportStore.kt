package tech.mmarca.openvitals.features.gym

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import tech.mmarca.openvitals.domain.model.BodyMeasurementType

/**
 * Diario Palestra: body-composition reports (BodyPedia and similar scales), one per
 * day, in a JSON file of the app. The same format is accepted as an import file, so a
 * report prepared elsewhere can be loaded with one tap:
 *
 * `{"reports":[{"date":"2026-08-29","time":"12:58","values":{"WEIGHT":93.0,"ARM_RIGHT":39.49}}]}`
 */
@Singleton
class BodyReportStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    class FormatException(message: String) : IllegalArgumentException(message)

    private val mutex = Mutex()
    private val file: File get() = File(context.filesDir, FILE_NAME)

    suspend fun load(): List<BodyReport> = withContext(Dispatchers.IO) { mutex.withLock { readUnlocked() } }

    suspend fun save(report: BodyReport) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val others = readUnlocked().filter { it.date != report.date }
            writeUnlocked(others + report)
        }
    }

    suspend fun delete(date: LocalDate) = withContext(Dispatchers.IO) {
        mutex.withLock { writeUnlocked(readUnlocked().filter { it.date != date }) }
    }

    /** Reads an import file; the caller saves each report so Health Connect is written too. */
    suspend fun readImport(uri: Uri): List<BodyReport> = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw FormatException("unreadable")
        val reports = runCatching { parse(text) }.getOrElse { throw FormatException("invalid") }
        if (reports.isEmpty()) throw FormatException("empty")
        reports
    }

    private fun readUnlocked(): List<BodyReport> {
        val f = file
        if (!f.exists()) return emptyList()
        return runCatching { parse(f.readText()) }.getOrDefault(emptyList())
    }

    private fun writeUnlocked(reports: List<BodyReport>) {
        val json = buildJsonObject {
            put("version", 1)
            put("reports", buildJsonArray { reports.sortedBy { it.date }.forEach { add(it.toJson()) } })
        }.toString()
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            file.writeText(json)
            tmp.delete()
        }
    }

    private fun BodyReport.toJson(): JsonObject = buildJsonObject {
        put("date", date.toString())
        put("time", time?.toString())
        put("values", buildJsonObject { values.forEach { (field, value) -> put(field.name, value) } })
        put("hc", buildJsonObject { healthConnectIds.forEach { (type, id) -> put(type.name, id) } })
    }

    internal companion object {
        const val FILE_NAME = "diario_palestra_corpo.json"

        fun parse(text: String): List<BodyReport> {
            val root = Json.parseToJsonElement(text.removePrefix("﻿"))
            val array = when (root) {
                is JsonArray -> root
                is JsonObject -> root["reports"] as? JsonArray ?: JsonArray(listOf(root))
                else -> JsonArray(emptyList())
            }
            return array.mapNotNull { it.toReport() }
        }

        private fun JsonElement.toReport(): BodyReport? {
            val obj = this as? JsonObject ?: return null
            val date = obj.text("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            val values = (obj["values"] as? JsonObject).orEmpty().mapNotNull { (key, value) ->
                val field = BodyField.entries.firstOrNull { it.name == key } ?: return@mapNotNull null
                val number = (value as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull ?: return@mapNotNull null
                field to number
            }.toMap()
            val ids = (obj["hc"] as? JsonObject).orEmpty().mapNotNull { (key, value) ->
                val type = BodyMeasurementType.entries.firstOrNull { it.name == key } ?: return@mapNotNull null
                val id = (value as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content
                    ?: return@mapNotNull null
                type to id
            }.toMap()
            return BodyReport(
                date = date,
                time = obj.text("time")?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                values = values,
                healthConnectIds = ids,
            )
        }

        private fun JsonObject.text(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content
    }
}

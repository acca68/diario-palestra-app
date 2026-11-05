package tech.mmarca.openvitals.features.gym

import java.time.LocalDateTime

/**
 * Reads Hevy's "Export workouts" CSV: one row per set, the workout repeated on
 * every row. Dates come in the phone's language ("2 nov 2026, 19:15"), in
 * English ("Nov 2, 2026, 7:15 PM") or as ISO.
 */
object HevyCsvParser {

    class Result(
        val workouts: List<ImportedWorkout>,
        val setCount: Int,
        val skippedRows: Int,
    )

    class FormatException(message: String) : IllegalArgumentException(message)

    fun parse(text: String): Result {
        val rows = parseCsv(text.removePrefix("﻿"))
        if (rows.size < 2) throw FormatException("empty")
        val header = rows.first().map { it.trim().lowercase() }
        fun col(name: String) = header.indexOf(name)
        val title = col("title")
        val start = col("start_time")
        val end = col("end_time")
        val exercise = col("exercise_title")
        if (title < 0 || start < 0 || exercise < 0) throw FormatException("not_hevy")
        val setType = col("set_type")
        val weight = col("weight_kg")
        val reps = col("reps")
        val distance = col("distance_km")
        val duration = col("duration_seconds")
        val rpe = col("rpe")

        class Draft(val title: String, val start: LocalDateTime, val end: LocalDateTime?) {
            val exercises = LinkedHashMap<String, MutableList<ImportedSet>>()
        }

        val drafts = LinkedHashMap<LocalDateTime, Draft>()
        var skipped = 0
        var sets = 0
        for (row in rows.drop(1)) {
            fun cell(index: Int): String = if (index < 0) "" else row.getOrNull(index)?.trim().orEmpty()
            val startTime = parseDateTime(cell(start))
            val name = cell(exercise)
            if (startTime == null || name.isEmpty()) {
                skipped++
                continue
            }
            val draft = drafts.getOrPut(startTime) { Draft(cell(title), startTime, parseDateTime(cell(end))) }
            val type = cell(setType).takeIf { it.isNotEmpty() && it != "normal" }
            draft.exercises.getOrPut(name) { mutableListOf() } += ImportedSet(
                kg = number(cell(weight)),
                reps = number(cell(reps))?.toInt(),
                km = number(cell(distance)),
                seconds = number(cell(duration))?.toInt(),
                rpe = number(cell(rpe)),
                type = type,
            )
            sets++
        }
        val workouts = drafts.values
            .map { draft ->
                ImportedWorkout(
                    title = draft.title,
                    start = draft.start,
                    end = draft.end,
                    exercises = draft.exercises.map { (name, list) -> ImportedExercise(name, list) },
                )
            }
            .sortedBy { it.start }
        return Result(workouts, sets, skipped)
    }

    private fun number(raw: String): Double? =
        raw.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    private val months = mapOf(
        "gen" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "mag" to 5, "giu" to 6,
        "lug" to 7, "ago" to 8, "set" to 9, "ott" to 10, "nov" to 11, "dic" to 12,
        "jan" to 1, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "dec" to 12,
    )
    private val isoPattern = Regex("""^(\d{4})-(\d{2})-(\d{2})[T ](\d{1,2}):(\d{2})""")
    private val dayFirstPattern = Regex("""^(\d{1,2})\s+(\p{L}+)\.?\s+(\d{4}),?\s+(\d{1,2})[:.](\d{2})""")
    private val monthFirstPattern = Regex("""^(\p{L}+)\.?\s+(\d{1,2}),?\s+(\d{4}),?\s+(\d{1,2}):(\d{2})\s*([AaPp][Mm])?""")

    internal fun parseDateTime(raw: String): LocalDateTime? {
        val text = raw.trim()
        isoPattern.find(text)?.destructured?.let { (y, mo, d, h, mi) ->
            return build(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt())
        }
        dayFirstPattern.find(text)?.destructured?.let { (d, mon, y, h, mi) ->
            val month = months[mon.take(3).lowercase()] ?: return null
            return build(y.toInt(), month, d.toInt(), h.toInt(), mi.toInt())
        }
        monthFirstPattern.find(text)?.let { match ->
            val (mon, d, y, h, mi) = match.destructured
            val month = months[mon.take(3).lowercase()] ?: return null
            var hour = h.toInt()
            val meridiem = match.groupValues[6].lowercase()
            if (meridiem == "pm" && hour < 12) hour += 12
            if (meridiem == "am" && hour == 12) hour = 0
            return build(y.toInt(), month, d.toInt(), hour, mi.toInt())
        }
        return null
    }

    private fun build(year: Int, month: Int, day: Int, hour: Int, minute: Int): LocalDateTime? =
        runCatching { LocalDateTime.of(year, month, day, hour, minute) }.getOrNull()

    /** RFC 4180: quoted fields may hold commas, newlines and doubled quotes. */
    internal fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> quoted = true
                    ',' -> {
                        row.add(field.toString())
                        field.setLength(0)
                    }
                    '\n', '\r' -> {
                        if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                        row.add(field.toString())
                        field.setLength(0)
                        if (row.size > 1 || row[0].isNotEmpty()) rows.add(row)
                        row = mutableListOf()
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }
}

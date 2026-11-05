package tech.mmarca.openvitals.features.gym

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import tech.mmarca.openvitals.domain.model.ExerciseData

/**
 * Diario Palestra: joins the three places a gym session lives.
 *
 * - Hevy's session in Health Connect: title and times, always current.
 * - Hevy's CSV, imported: the exercises, sets and loads; matched by start minute.
 * - The watch's session that overlaps it (Garmin's HIIT): heart rate and calories.
 *
 * MyFitnessPal re-writes other apps' sessions shifted by hours, so it is never a match.
 */
object GymSessions {

    private const val MATCH_TOLERANCE_MINUTES = 3L
    private const val MIN_OVERLAP_SHARE = 0.4

    fun build(
        healthConnect: List<ExerciseData>,
        imported: List<ImportedWorkout>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<GymSession> {
        val hevy = healthConnect.filter { it.isFrom("hevy") }
        val watch = healthConnect.filter { !it.isFrom("hevy") && !it.isFrom("myfitnesspal") }
        val usedImports = mutableSetOf<String>()

        val fromHealthConnect = hevy.map { session ->
            val start = LocalDateTime.ofInstant(session.startTime, zone)
            val end = LocalDateTime.ofInstant(session.endTime, zone)
            val match = imported
                .filter { it.key !in usedImports }
                .minByOrNull { abs(Duration.between(it.start, start).toMinutes()) }
                ?.takeIf { abs(Duration.between(it.start, start).toMinutes()) <= MATCH_TOLERANCE_MINUTES }
            match?.let { usedImports += it.key }
            session(
                title = session.title?.takeIf { it.isNotBlank() } ?: match?.title.orEmpty(),
                start = start,
                end = end,
                exercises = match?.exercises.orEmpty(),
                fallbackCalories = session.totalCaloriesKcal,
                watch = watch,
                zone = zone,
            )
        }
        val importOnly = imported
            .filter { it.key !in usedImports }
            .map { workout ->
                session(
                    title = workout.title,
                    start = workout.start,
                    end = workout.end,
                    exercises = workout.exercises,
                    fallbackCalories = null,
                    watch = watch,
                    zone = zone,
                )
            }
        return (fromHealthConnect + importOnly).sortedByDescending { it.start }
    }

    private fun session(
        title: String,
        start: LocalDateTime,
        end: LocalDateTime?,
        exercises: List<ImportedExercise>,
        fallbackCalories: Double?,
        watch: List<ExerciseData>,
        zone: ZoneId,
    ): GymSession {
        val watchMatch = end?.let { bestOverlap(start, it, watch, zone) }
        return GymSession(
            key = start.withSecond(0).withNano(0).toString(),
            title = title,
            date = start.toLocalDate(),
            start = start,
            durationMinutes = end?.let { Duration.between(start, it).toMinutes() }?.takeIf { it > 0 },
            caloriesKcal = watchMatch?.totalCaloriesKcal ?: fallbackCalories?.takeIf { it > 0 },
            averageHeartRateBpm = watchMatch?.averageHeartRateBpm,
            watchSource = watchMatch?.source,
            exercises = exercises,
        )
    }

    private fun bestOverlap(
        start: LocalDateTime,
        end: LocalDateTime,
        candidates: List<ExerciseData>,
        zone: ZoneId,
    ): ExerciseData? {
        val length = Duration.between(start, end).toMinutes().coerceAtLeast(1)
        return candidates
            .map { candidate ->
                val cStart = LocalDateTime.ofInstant(candidate.startTime, zone)
                val cEnd = LocalDateTime.ofInstant(candidate.endTime, zone)
                val overlapStart = maxOf(start, cStart)
                val overlapEnd = minOf(end, cEnd)
                val overlap = Duration.between(overlapStart, overlapEnd).toMinutes()
                val shorter = minOf(length, Duration.between(cStart, cEnd).toMinutes().coerceAtLeast(1))
                candidate to overlap.toDouble() / shorter
            }
            .filter { (_, share) -> share >= MIN_OVERLAP_SHARE }
            .maxByOrNull { (_, share) -> share }
            ?.first
    }

    private fun ExerciseData.isFrom(app: String): Boolean = source.contains(app, ignoreCase = true)

    fun stats(sessions: List<GymSession>, today: LocalDate = LocalDate.now()): GymStats {
        if (sessions.isEmpty()) return GymStats()
        val dates = sessions.map { it.date }.toSet()
        val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeks = dates.map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toSet()
        var streak = 0
        var week = if (thisWeek in weeks) thisWeek else thisWeek.minusWeeks(1)
        while (week in weeks) {
            streak++
            week = week.minusWeeks(1)
        }
        val last = dates.filter { !it.isAfter(today) }.maxOrNull()
        val rest = last?.let { (ChronoUnit.DAYS.between(it, today) - 1).coerceAtLeast(0).toInt() }
        val month = sessions.count { it.date.year == today.year && it.date.month == today.month }
        return GymStats(weekStreak = streak, restDays = rest, sessionsThisMonth = month)
    }

    /** Exercises with loads, most practised first: the choices of the progress chart. */
    fun progressExercises(sessions: List<GymSession>): List<String> =
        sessions.flatMap { session ->
            session.exercises
                .filter { !it.isAssisted && it.sets.any { set -> set.estimatedOneRepMax != null } }
                .map { it.name }
                .distinct()
        }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

    /** The best estimated one-rep max of each session, oldest first. */
    fun progress(sessions: List<GymSession>, exercise: String): List<GymProgressPoint> =
        sessions
            .mapNotNull { session ->
                val best = session.exercises
                    .filter { it.name == exercise && !it.isAssisted }
                    .flatMap { it.sets }
                    .mapNotNull { it.estimatedOneRepMax }
                    .maxOrNull()
                best?.let { GymProgressPoint(session.date, it) }
            }
            .groupBy { it.date }
            .map { (_, points) -> points.maxBy { it.oneRepMaxKg } }
            .sortedBy { it.date }
}

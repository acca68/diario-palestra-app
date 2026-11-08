package tech.mmarca.openvitals.features.gym

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import androidx.health.connect.client.records.ExerciseSessionRecord
import tech.mmarca.openvitals.domain.model.ExerciseData

/**
 * Diario Palestra: joins the three places a gym session lives.
 *
 * - Hevy's session in Health Connect: title and times, always current.
 * - Hevy's CSV, imported: the exercises, sets and loads; matched by start minute.
 * - The watch's session that overlaps it (Garmin's HIIT): heart rate and calories.
 *
 * MyFitnessPal re-writes other apps' sessions shifted by hours, so it is never a match.
 * Hevy is often filled in after the workout, so when no watch session overlaps it,
 * the watch's gym session of the same day is used, and its times are the real ones.
 */
object GymSessions {

    private const val MATCH_TOLERANCE_MINUTES = 3L
    private const val MIN_OVERLAP_SHARE = 0.4
    private const val MIN_GYM_MINUTES = 15L
    private const val MIN_CLASS_MINUTES = 10L

    fun build(
        healthConnect: List<ExerciseData>,
        imported: List<ImportedWorkout>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<GymSession> {
        val hevy = healthConnect.filter { it.isFrom("hevy") }
        val watch = healthConnect.filter { !it.isFrom("hevy") && !it.isFrom("myfitnesspal") }
        val usedImports = mutableSetOf<String>()
        val usedWatch = mutableSetOf<String>()

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
                usedWatch = usedWatch,
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
                    usedWatch = usedWatch,
                    zone = zone,
                )
            }
        val classes = watch
            .filter { it.id !in usedWatch }
            .filter { it.exerciseType in classTypes }
            .filter { Duration.between(it.startTime, it.endTime).toMinutes() >= MIN_CLASS_MINUTES }
            .map { activity ->
                val start = LocalDateTime.ofInstant(activity.startTime, zone)
                val end = LocalDateTime.ofInstant(activity.endTime, zone)
                GymSession(
                    key = "class-" + start.withSecond(0).withNano(0),
                    title = activity.title?.trim().orEmpty(),
                    date = start.toLocalDate(),
                    start = start,
                    durationMinutes = Duration.between(start, end).toMinutes().takeIf { it > 0 },
                    caloriesKcal = activity.totalCaloriesKcal?.takeIf { it > 0 },
                    averageHeartRateBpm = activity.averageHeartRateBpm,
                    watchSource = activity.source,
                    exercises = emptyList(),
                    activityType = activity.exerciseType,
                )
            }
        return (fromHealthConnect + importOnly + classes).sortedByDescending { it.start }
    }

    private fun session(
        title: String,
        start: LocalDateTime,
        end: LocalDateTime?,
        exercises: List<ImportedExercise>,
        fallbackCalories: Double?,
        watch: List<ExerciseData>,
        usedWatch: MutableSet<String>,
        zone: ZoneId,
    ): GymSession {
        val available = watch.filter { it.id !in usedWatch }
        val gymCandidates = available.filter { it.exerciseType in strengthTypes || it.exerciseType in legacyGymTypes }
        val overlapping = end?.let { bestOverlap(start, it, gymCandidates, zone) }
        val watchMatch = overlapping
            ?: sameDay(start, available, zone, strengthTypes)
            ?: sameDay(start, available, zone, legacyGymTypes)
        watchMatch?.let { usedWatch += it.id }
        val realStart = watchMatch?.let { LocalDateTime.ofInstant(it.startTime, zone) } ?: start
        val realEnd = watchMatch?.let { LocalDateTime.ofInstant(it.endTime, zone) } ?: end
        return GymSession(
            key = start.withSecond(0).withNano(0).toString(),
            title = title,
            date = start.toLocalDate(),
            start = realStart,
            durationMinutes = realEnd?.let { Duration.between(realStart, it).toMinutes() }?.takeIf { it > 0 },
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

    /** Weights on the watch ("Allenamento di forza"): always the Hevy workout of that day. */
    private val strengthTypes = setOf(
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
    )

    /**
     * Before strength mode, weights were recorded as HIIT: a same-day HIIT still goes with the
     * Hevy workout when the watch has no strength session that day (courses are other types).
     */
    private val legacyGymTypes = setOf(
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
    )

    /** Courses and indoor cardio shown in the diary even without a Hevy workout. */
    private val classTypes = setOf(
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL,
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE,
        ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS,
        ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP,
        ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS,
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING,
        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING,
        ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS,
        ExerciseSessionRecord.EXERCISE_TYPE_BOXING,
        ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
    )

    /** The watch session of the given types on the same day, the closest in time when there are more. */
    private fun sameDay(
        start: LocalDateTime,
        candidates: List<ExerciseData>,
        zone: ZoneId,
        types: Set<Int>,
    ): ExerciseData? =
        candidates
            .filter { it.exerciseType in types }
            .filter { LocalDateTime.ofInstant(it.startTime, zone).toLocalDate() == start.toLocalDate() }
            .filter { Duration.between(it.startTime, it.endTime).toMinutes() >= MIN_GYM_MINUTES }
            .minByOrNull { abs(Duration.between(LocalDateTime.ofInstant(it.startTime, zone), start).toMinutes()) }

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

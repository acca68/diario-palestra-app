package tech.mmarca.openvitals.features.gym

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Diario Palestra: a workout imported from Hevy's CSV export.
 *
 * Health Connect receives only the session from Hevy (title, times, calories),
 * never the exercises, so sets, reps and loads come from the CSV and are kept
 * in a file of the app ([GymImportStore]).
 */
@Immutable
data class ImportedWorkout(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime?,
    val exercises: List<ImportedExercise>,
) {
    /** Hevy writes one row per set; a workout is keyed by its start minute. */
    val key: String get() = start.withSecond(0).withNano(0).toString()
}

@Immutable
data class ImportedExercise(
    val name: String,
    val sets: List<ImportedSet>,
) {
    /** Assisted machines log the assistance: more kilograms is the easier set. */
    val isAssisted: Boolean
        get() = name.contains("assistit", ignoreCase = true) || name.contains("assisted", ignoreCase = true)

    val isCardio: Boolean get() = sets.isNotEmpty() && sets.all { it.reps == null && (it.seconds != null || it.km != null) }
}

@Immutable
data class ImportedSet(
    val kg: Double? = null,
    val reps: Int? = null,
    val km: Double? = null,
    val seconds: Int? = null,
    val rpe: Double? = null,
    val type: String? = null,
) {
    /** Epley's estimated one-rep max, the curve the progress chart draws. */
    val estimatedOneRepMax: Double?
        get() = if (kg != null && kg > 0 && reps != null && reps > 0) kg * (1 + reps / 30.0) else null
}

/** One gym session as the screen shows it: Hevy's session joined with the CSV and the watch. */
@Immutable
data class GymSession(
    val key: String,
    val title: String,
    val date: LocalDate,
    val start: LocalDateTime,
    val durationMinutes: Long?,
    val caloriesKcal: Double?,
    val averageHeartRateBpm: Long?,
    val watchSource: String?,
    val exercises: List<ImportedExercise>,
) {
    val hasSets: Boolean get() = exercises.any { it.sets.isNotEmpty() }

    /** Kilograms lifted: load × reps over every set, assisted machines left out. */
    val volumeKg: Double
        get() = exercises.filterNot { it.isAssisted }.sumOf { exercise ->
            exercise.sets.sumOf { (it.kg ?: 0.0) * (it.reps ?: 0) }
        }
}

@Immutable
data class GymProgressPoint(
    val date: LocalDate,
    val oneRepMaxKg: Double,
)

@Immutable
data class GymStats(
    val weekStreak: Int = 0,
    val restDays: Int? = null,
    val sessionsThisMonth: Int = 0,
)

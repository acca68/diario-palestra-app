package tech.mmarca.openvitals.features.gym

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import tech.mmarca.openvitals.R

/** Diario Palestra: the periods the gym summary can cover. */
enum class GymPeriod(@param:StringRes val label: Int) {
    WEEK(R.string.gym_period_week),
    MONTH(R.string.gym_period_month),
    YEAR(R.string.gym_period_year),
    ALL(R.string.gym_period_all),
}

@Immutable
data class GymSummary(val sessions: Int, val minutes: Long, val volumeKg: Double, val kcal: Double)

/** Something heavy to compare the lifted total with: "about 2 times a blue whale". */
@Immutable
data class LiftComparison(@param:StringRes val thing: Int, val times: Double)

@Immutable
data class Milestone(val current: Int, val previous: Int, val next: Int) {
    val progress: Float get() = ((current - previous).toFloat() / (next - previous)).coerceIn(0f, 1f)
}

/** A goal of the month: [current] of [target], with the label telling what it counts. */
@Immutable
data class Challenge(@param:StringRes val label: Int, val current: Double, val target: Double, val unit: String = "") {
    val done: Boolean get() = target > 0 && current >= target
    val progress: Float get() = if (target <= 0) 0f else (current / target).toFloat().coerceIn(0f, 1f)
}

/** A muscle group that has rested a while: the last day it was trained. */
@Immutable
data class RecoveryHint(val group: MuscleGroup, val lastTrained: LocalDate, val days: Long)

/** Pure calculations behind the gym page's summary, streak, milestones, challenges and hints. */
object GymInsights {

    /** Weeks in a row for the medals: 1 month, 3 months, 6 months, 1 year, 2 years. */
    val MEDALS = listOf(4, 12, 26, 52, 104)

    private val SESSION_MILESTONES = listOf(10, 25, 50, 75, 100, 150, 200, 250, 300, 400, 500, 750, 1000)

    /** Monthly goals: 12 sessions (three a week) and 4 weigh-ins (one a week). */
    const val MONTH_SESSIONS = 12
    const val MONTH_WEIGH_INS = 4

    /** Rested this many days or more, after being trained in the last [RECENT_DAYS]. */
    const val REST_DAYS = 7L
    const val RECENT_DAYS = 60L

    private val COMPARISONS = listOf(
        R.string.gym_lift_car to 1_500.0,
        R.string.gym_lift_elephant to 6_000.0,
        R.string.gym_lift_bus to 12_000.0,
        R.string.gym_lift_whale to 150_000.0,
        R.string.gym_lift_a380 to 575_000.0,
    )

    fun start(period: GymPeriod, today: LocalDate): LocalDate? = when (period) {
        GymPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        GymPeriod.MONTH -> today.withDayOfMonth(1)
        GymPeriod.YEAR -> today.withDayOfYear(1)
        GymPeriod.ALL -> null
    }

    fun summary(sessions: List<GymSession>, period: GymPeriod, today: LocalDate = LocalDate.now()): GymSummary {
        val from = start(period, today)
        val inPeriod = sessions.filter { (from == null || !it.date.isBefore(from)) && !it.date.isAfter(today) }
        return GymSummary(
            sessions = inPeriod.size,
            minutes = inPeriod.sumOf { it.durationMinutes ?: 0L },
            volumeKg = inPeriod.sumOf { it.volumeKg },
            kcal = inPeriod.sumOf { it.caloriesKcal ?: 0.0 },
        )
    }

    fun comparison(kg: Double): LiftComparison? {
        val (thing, weight) = COMPARISONS.lastOrNull { kg >= it.second } ?: return null
        return LiftComparison(thing, kg / weight)
    }

    /** The longest run of weeks in a row with at least one session. */
    fun bestStreak(sessions: List<GymSession>): Int {
        val weeks = sessions.map { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toSortedSet()
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (week in weeks) {
            run = if (previous != null && ChronoUnit.WEEKS.between(previous, week) == 1L) run + 1 else 1
            best = maxOf(best, run)
            previous = week
        }
        return best
    }

    /** The next round number of sessions to reach, counting every session so far. */
    fun sessionMilestone(sessions: List<GymSession>, today: LocalDate = LocalDate.now()): Milestone {
        val count = sessions.count { !it.date.isAfter(today) }
        val next = SESSION_MILESTONES.firstOrNull { it > count } ?: ((count / 500) + 1) * 500
        val previous = SESSION_MILESTONES.lastOrNull { it <= count } ?: 0
        return Milestone(current = count, previous = previous, next = next)
    }

    /** This month's challenges: sessions, lifting more than last month, weighing in each week. */
    fun challenges(sessions: List<GymSession>, weighIns: List<LocalDate>, today: LocalDate = LocalDate.now()): List<Challenge> {
        val month = today.withDayOfMonth(1)
        val lastMonth = month.minusMonths(1)
        fun inMonth(d: LocalDate, m: LocalDate) = d.year == m.year && d.month == m.month
        val thisVolume = sessions.filter { inMonth(it.date, month) }.sumOf { it.volumeKg }
        val lastVolume = sessions.filter { inMonth(it.date, lastMonth) }.sumOf { it.volumeKg }
        return listOfNotNull(
            Challenge(R.string.gym_challenge_sessions, sessions.count { inMonth(it.date, month) }.toDouble(), MONTH_SESSIONS.toDouble()),
            lastVolume.takeIf { it > 0 }?.let {
                Challenge(R.string.gym_challenge_volume, thisVolume / 1000.0, it / 1000.0, "t")
            },
            Challenge(R.string.gym_challenge_weigh, weighIns.distinct().count { inMonth(it, month) }.toDouble(), MONTH_WEIGH_INS.toDouble()),
        )
    }

    /** Muscle groups trained in the last two months that have now rested a week or more, longest first. */
    fun recovery(sessions: List<GymSession>, today: LocalDate = LocalDate.now()): List<RecoveryHint> {
        val last = mutableMapOf<MuscleGroup, LocalDate>()
        sessions.filter { !it.date.isAfter(today) }.forEach { session ->
            session.exercises.forEach { exercise ->
                if (exercise.sets.none { it.reps != null || it.kg != null }) return@forEach
                GymMuscles.musclesOf(exercise.name)?.primary?.forEach { group ->
                    val seen = last[group]
                    if (seen == null || session.date.isAfter(seen)) last[group] = session.date
                }
            }
        }
        return last.mapNotNull { (group, date) ->
            val days = ChronoUnit.DAYS.between(date, today)
            if (days in REST_DAYS..RECENT_DAYS) RecoveryHint(group, date, days) else null
        }.sortedByDescending { it.days }
    }

    /** Sessions matching the search: title, exercise names or the date as written (12.11.2026). */
    fun search(sessions: List<GymSession>, query: String): List<GymSession> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return sessions
        return sessions.filter { session ->
            val text = buildString {
                append(session.title.lowercase()).append(' ')
                append(session.date.format(java.time.format.DateTimeFormatter.ofPattern("d.M.yyyy dd.MM.yyyy"))).append(' ')
                session.exercises.forEach { append(it.name.lowercase()).append(' ') }
            }
            words.all { it in text }
        }
    }
}

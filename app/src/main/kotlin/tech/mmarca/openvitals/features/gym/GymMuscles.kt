package tech.mmarca.openvitals.features.gym

import androidx.annotation.StringRes
import java.time.LocalDate
import tech.mmarca.openvitals.R

/** Diario Palestra: the muscle groups of the body map. */
enum class MuscleGroup(@param:StringRes val labelRes: Int) {
    CHEST(R.string.gym_muscle_chest),
    SHOULDERS(R.string.gym_muscle_shoulders),
    BICEPS(R.string.gym_muscle_biceps),
    TRICEPS(R.string.gym_muscle_triceps),
    FOREARMS(R.string.gym_muscle_forearms),
    ABS(R.string.gym_muscle_abs),
    OBLIQUES(R.string.gym_muscle_obliques),
    TRAPS(R.string.gym_muscle_traps),
    LATS(R.string.gym_muscle_lats),
    LOWER_BACK(R.string.gym_muscle_lower_back),
    GLUTES(R.string.gym_muscle_glutes),
    QUADS(R.string.gym_muscle_quads),
    HAMSTRINGS(R.string.gym_muscle_hamstrings),
    ADDUCTORS(R.string.gym_muscle_adductors),
    CALVES(R.string.gym_muscle_calves),
}

object GymMuscles {

    private class Rule(val pattern: Regex, val primary: List<MuscleGroup>, val secondary: List<MuscleGroup> = emptyList())

    private fun rule(pattern: String, primary: List<MuscleGroup>, secondary: List<MuscleGroup> = emptyList()) =
        Rule(Regex(pattern, RegexOption.IGNORE_CASE), primary, secondary)

    private val G = MuscleGroup.entries.associateBy { it.name }
    private fun g(vararg names: String) = names.map { G.getValue(it) }

    /** First match wins, so the specific names come before the generic ones ("leg curl" before "curl"). */
    private val rules = listOf(
        rule("leg ?curl|femoral|hamstring|rumeno|romanian|good ?morning", g("HAMSTRINGS")),
        rule("leg ?ext", g("QUADS")),
        rule("leg ?press|squat|hack|affond|lunge|step ?up|bulgar", g("QUADS", "GLUTES"), g("HAMSTRINGS", "ADDUCTORS")),
        rule("stacco|deadlift", g("HAMSTRINGS", "GLUTES", "LOWER_BACK"), g("TRAPS", "FOREARMS")),
        rule("abduz|abduct|hip thrust|ponte glutei|glute", g("GLUTES")),
        rule("adduz|adduct", g("ADDUCTORS")),
        rule("calf|polpacc", g("CALVES")),
        rule("iperesten|hyperext|lombar|back ext", g("LOWER_BACK"), g("GLUTES", "HAMSTRINGS")),
        rule("russian twist|obliqu|side bend|woodchop", g("OBLIQUES"), g("ABS")),
        rule("crunch|sit ?up|knee raise|leg raise|plank|addomin|ab wheel", g("ABS"), g("OBLIQUES")),
        rule("tricip|push ?down|french|skull", g("TRICEPS")),
        rule("curl|bicip|martello|hammer", g("BICEPS"), g("FOREARMS")),
        rule("croci inverse|deltoide post|rear delt|reverse fly|face ?pull", g("SHOULDERS"), g("TRAPS")),
        rule("trazion|pull ?up|chin ?up|lat ?pull|pulldown|lat machine", g("LATS"), g("BICEPS")),
        rule("rematore|\\brow\\b|pulley", g("LATS", "TRAPS"), g("BICEPS", "SHOULDERS")),
        rule("scrollat|shrug", g("TRAPS")),
        rule("military|shoulder press|lento|alzate|lateral raise|arnold|overhead|spalle", g("SHOULDERS"), g("TRICEPS")),
        rule("panca|bench|chest press|piegament|push ?up|flessioni|\\bdip", g("CHEST"), g("TRICEPS", "SHOULDERS")),
        rule("butterfly|pec deck|croci|\\bfly|crossover", g("CHEST"), g("SHOULDERS")),
    )

    class Muscles(val primary: List<MuscleGroup>, val secondary: List<MuscleGroup>)

    fun musclesOf(exerciseName: String): Muscles? =
        rules.firstOrNull { it.pattern.containsMatchIn(exerciseName) }?.let { Muscles(it.primary, it.secondary) }

    /**
     * Sets per muscle group between [from] and [to]: a primary muscle counts each set,
     * a secondary one half a set. Cardio and sets without reps or load are left out.
     */
    fun setsPerMuscle(sessions: List<GymSession>, from: LocalDate, to: LocalDate): Map<MuscleGroup, Double> {
        val totals = mutableMapOf<MuscleGroup, Double>()
        sessions.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.forEach { session ->
            session.exercises.forEach { exercise ->
                val muscles = musclesOf(exercise.name) ?: return@forEach
                val sets = exercise.sets.count { it.reps != null || it.kg != null }
                if (sets == 0) return@forEach
                muscles.primary.forEach { totals[it] = (totals[it] ?: 0.0) + sets }
                muscles.secondary.forEach { totals[it] = (totals[it] ?: 0.0) + sets / 2.0 }
            }
        }
        return totals
    }

    /** Which group colours each outline of the library; head and knees stay body-coloured. */
    internal val outlineGroups: Map<String, MuscleGroup> = mapOf(
        "CHEST" to MuscleGroup.CHEST,
        "OBLIQUES" to MuscleGroup.OBLIQUES,
        "ABS" to MuscleGroup.ABS,
        "BICEPS" to MuscleGroup.BICEPS,
        "TRICEPS" to MuscleGroup.TRICEPS,
        "NECK" to MuscleGroup.TRAPS,
        "TRAPEZIUS" to MuscleGroup.TRAPS,
        "FRONT_DELTOIDS" to MuscleGroup.SHOULDERS,
        "BACK_DELTOIDS" to MuscleGroup.SHOULDERS,
        "UPPER_BACK" to MuscleGroup.LATS,
        "LOWER_BACK" to MuscleGroup.LOWER_BACK,
        "FOREARM" to MuscleGroup.FOREARMS,
        "GLUTEAL" to MuscleGroup.GLUTES,
        "ABDUCTORS" to MuscleGroup.ADDUCTORS,
        "ADDUCTOR" to MuscleGroup.ADDUCTORS,
        "QUADRICEPS" to MuscleGroup.QUADS,
        "HAMSTRING" to MuscleGroup.HAMSTRINGS,
        "CALVES" to MuscleGroup.CALVES,
        "LEFT_SOLEUS" to MuscleGroup.CALVES,
        "RIGHT_SOLEUS" to MuscleGroup.CALVES,
    )
}

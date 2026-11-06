package tech.mmarca.openvitals.features.gym

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.mmarca.openvitals.domain.model.ExerciseData

class GymTest {

    private val zone = ZoneId.of("Europe/Zurich")

    private val csv = GymTestData.CSV

    @Test
    fun parsesItalianAndEnglishDatesAndGroupsSets() {
        val result = HevyCsvParser.parse(csv)
        assertEquals(2, result.workouts.size)
        assertEquals(5, result.setCount)
        val upper = result.workouts.last()
        assertEquals(LocalDateTime.of(2026, 11, 2, 19, 15), upper.start)
        assertEquals(3, upper.exercises.size)
        assertEquals(2, upper.exercises.first().sets.size)
        assertTrue(upper.exercises[1].isAssisted)
        assertTrue(upper.exercises[2].isCardio)
        val legs = result.workouts.first()
        assertEquals(LocalDateTime.of(2026, 10, 28, 19, 57), legs.start)
        assertEquals("warmup", legs.exercises.single().sets.single().type)
    }

    @Test
    fun joinsHevySessionWithImportAndWatch() {
        val imported = HevyCsvParser.parse(csv).workouts
        fun at(h: Int, m: Int): Instant = LocalDateTime.of(2026, 11, 2, h, m).atZone(zone).toInstant()
        val hevy = exercise("1", "com.hevy", at(19, 16), at(20, 15), kcal = 300.0)
        val garmin = exercise("2", "com.garmin.android.apps.connectmobile", at(19, 10), at(20, 20), kcal = 520.0, hr = 118)
        val mfp = exercise("3", "com.myfitnesspal.android", at(19, 10), at(20, 20), kcal = 999.0)
        val sessions = GymSessions.build(listOf(hevy, garmin, mfp), imported, zone)

        assertEquals(2, sessions.size)
        val upper = sessions.first()
        assertEquals("Upper Body", upper.title)
        assertEquals(3, upper.exercises.size)
        assertEquals(520.0, upper.caloriesKcal!!, 0.01)
        assertEquals(118L, upper.averageHeartRateBpm)
        // Assisted pull-ups do not count: 35×12 + 40×10.
        assertEquals(820.0, upper.volumeKg, 0.01)
        assertNull(sessions.last().caloriesKcal)
    }

    @Test
    fun progressAndStats() {
        val sessions = GymSessions.build(emptyList(), HevyCsvParser.parse(csv).workouts, zone)
        assertEquals(listOf("Leg Extension (Macchina)", "Panca Piana (Multipower)"), GymSessions.progressExercises(sessions))
        val points = GymSessions.progress(sessions, "Panca Piana (Multipower)")
        assertEquals(1, points.size)
        assertEquals(40 * (1 + 10 / 30.0), points.single().oneRepMaxKg, 0.01)

        val stats = GymSessions.stats(sessions, today = LocalDate.of(2026, 11, 4))
        assertEquals(2, stats.weekStreak)
        assertEquals(1, stats.restDays)
        assertEquals(1, stats.sessionsThisMonth)
    }

    private fun exercise(id: String, source: String, start: Instant, end: Instant, kcal: Double, hr: Long? = null) =
        ExerciseData(
            id = id,
            title = if (source.contains("hevy")) "Upper Body" else null,
            exerciseType = 0,
            startTime = start,
            endTime = end,
            durationMs = end.toEpochMilli() - start.toEpochMilli(),
            source = source,
            totalCaloriesKcal = kcal,
            averageHeartRateBpm = hr,
        )
}

class GymMusclesTest {

    @Test
    fun matchesHevyExerciseNames() {
        fun primary(name: String) = GymMuscles.musclesOf(name)?.primary
        assertEquals(listOf(MuscleGroup.HAMSTRINGS), primary("Leg Curl Seduto (Macchina)"))
        assertEquals(listOf(MuscleGroup.BICEPS), primary("Bicipiti Martello (Cavo)"))
        assertEquals(listOf(MuscleGroup.TRICEPS), primary("Dip Tricipiti (Assistito)"))
        assertEquals(listOf(MuscleGroup.SHOULDERS), primary("Croci Inverse Deltoide Posteriore (Macchina)"))
        assertEquals(listOf(MuscleGroup.CHEST), primary("Butterfly (Pec Deck)"))
        assertEquals(listOf(MuscleGroup.LATS), primary("Lat Pulldown Braccia Dritte (Cavo)"))
        assertEquals(listOf(MuscleGroup.OBLIQUES), primary("Russian Twist (Con Peso Aggiunto)"))
        assertEquals(listOf(MuscleGroup.ADDUCTORS), primary("Adduzione Anche (Macchina)"))
        assertEquals(listOf(MuscleGroup.GLUTES), primary("Abduzione Anche (Macchina)"))
        assertNull(GymMuscles.musclesOf("Tapis Roulant"))
        assertNull(GymMuscles.musclesOf("Vogatore"))
    }

    @Test
    fun countsSetsInPeriod() {
        val sessions = GymSessions.build(emptyList(), HevyCsvParser.parse(GymTestData.CSV).workouts, java.time.ZoneId.of("Europe/Zurich"))
        val sets = GymMuscles.setsPerMuscle(sessions, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7))
        // Two bench sets: chest 2, triceps and shoulders half each; the assisted pull-up counts for the lats.
        assertEquals(2.0, sets[MuscleGroup.CHEST]!!, 0.01)
        assertEquals(1.0, sets[MuscleGroup.TRICEPS]!!, 0.01)
        assertEquals(1.0, sets[MuscleGroup.LATS]!!, 0.01)
        assertNull(sets[MuscleGroup.QUADS])
    }
}

object GymTestData {
    val CSV = """
        "title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
        "Upper Body","2 nov 2026, 19:15","2 nov 2026, 20:15","","Panca Piana (Multipower)",,"",0,"normal",35,12,,,
        "Upper Body","2 nov 2026, 19:15","2 nov 2026, 20:15","","Panca Piana (Multipower)",,"",1,"normal",40,10,,,
        "Upper Body","2 nov 2026, 19:15","2 nov 2026, 20:15","","Trazione Supina (Assistita)",,"",0,"normal",35,10,,,
        "Upper Body","2 nov 2026, 19:15","2 nov 2026, 20:15","","Tapis Roulant",,"",0,"normal",,,1.5,911,
        "Gambe","Oct 28, 2026, 7:57 PM","Oct 28, 2026, 8:52 PM","","Leg Extension (Macchina)",,"",0,"warmup",63,12,,,
    """.trimIndent()
}

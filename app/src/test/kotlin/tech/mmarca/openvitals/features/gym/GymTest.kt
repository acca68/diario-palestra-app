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
    fun hevyFilledInLaterJoinsTheWatchSessionOfTheSameDay() {
        val imported = HevyCsvParser.parse(csv).workouts
        fun at(h: Int, m: Int): Instant = LocalDateTime.of(2026, 11, 2, h, m).atZone(zone).toInstant()
        val walk = exercise("w", "com.garmin.android.apps.connectmobile", at(13, 50), at(13, 56), kcal = 30.0)
            .copy(exerciseType = androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_WALKING)
        val hiit = exercise("h", "com.garmin.android.apps.connectmobile", at(17, 36), at(18, 56), kcal = 588.0, hr = 112)
        val upper = GymSessions.build(listOf(walk, hiit), imported, zone).first()

        assertEquals(588.0, upper.caloriesKcal!!, 0.01)
        assertEquals(112L, upper.averageHeartRateBpm)
        assertEquals(LocalDateTime.of(2026, 11, 2, 17, 36), upper.start)
        assertEquals(80L, upper.durationMinutes)
        assertEquals(LocalDate.of(2026, 11, 2), upper.date)
    }

    @Test
    fun strengthGoesWithHevyAndCoursesStandAlone() {
        val imported = HevyCsvParser.parse(csv).workouts
        fun at(h: Int, m: Int): Instant = LocalDateTime.of(2026, 11, 2, h, m).atZone(zone).toInstant()
        val strength = exercise("s", "com.garmin.android.apps.connectmobile", at(17, 36), at(18, 56), kcal = 500.0, hr = 110)
            .copy(exerciseType = androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING)
        // A yoga class overlapping the time Hevy was filled in must not be taken for the weights.
        val yoga = exercise("y", "com.garmin.android.apps.connectmobile", at(19, 20), at(20, 20), kcal = 200.0, hr = 90)
            .copy(exerciseType = androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_YOGA, title = "Yoga serale")
        val walk = exercise("w", "com.garmin.android.apps.connectmobile", at(12, 0), at(12, 40), kcal = 120.0)
            .copy(exerciseType = androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_WALKING)
        val sessions = GymSessions.build(listOf(strength, yoga, walk), imported, zone)
            .filter { it.date == LocalDate.of(2026, 11, 2) }

        val upper = sessions.single { !it.isClass }
        assertEquals(500.0, upper.caloriesKcal!!, 0.01)
        val course = sessions.single { it.isClass }
        assertEquals("Yoga serale", course.title)
        assertEquals(60L, course.durationMinutes)
        assertEquals(2, sessions.size)
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

class BodyReportTest {

    @Test
    fun parsesImportFileAndComputesChanges() {
        val reports = BodyReportStore.parse(
            """
            {"reports":[
              {"date":"2026-08-29","time":"12:58","values":{"WEIGHT":93.0,"ARM_RIGHT":39.49,"ARM_LEFT":38.97,"UNKNOWN":1}},
              {"date":"2026-11-01","values":{"WEIGHT":91.4,"FAT_PERCENT":24.9}}
            ]}
            """.trimIndent(),
        )
        assertEquals(2, reports.size)
        assertEquals(java.time.LocalTime.of(12, 58), reports.first().time)
        assertEquals(39.49, reports.first()[BodyField.ARM_RIGHT]!!, 0.001)
        val weight = BodyReports.change(reports, BodyField.WEIGHT)!!
        assertEquals(91.4, weight.value, 0.001)
        assertEquals(-1.6, weight.delta!!, 0.001)
        assertEquals(LocalDate.of(2026, 8, 29), weight.previousDate)
        // Only one report has body fat: no previous value to compare with.
        assertNull(BodyReports.change(reports, BodyField.FAT_PERCENT)!!.delta)
    }
}

class TrainerDataTest {

    @Test
    fun countsRemainingLessonsAndSpending() {
        val data = TrainerData(
            packages = listOf(TrainerPackage("p", LocalDate.of(2026, 10, 1), lessons = 10, amountChf = 600.0)),
            lessons = listOf(
                TrainerLesson("a", LocalDate.of(2026, 10, 11)),
                TrainerLesson("b", LocalDate.of(2026, 10, 18)),
                TrainerLesson("c", LocalDate.of(2026, 10, 25), costChf = 70.0, extra = true),
            ),
        )
        assertEquals(10, data.bought)
        assertEquals(2, data.used)
        assertEquals(8, data.remaining)
        assertEquals(670.0, data.spentChf, 0.01)
        assertNull(TrainerData().remaining)
    }

    @Test
    fun conEddySwitchAddsThenRemovesTheLesson() {
        val session = GymSession(
            key = "k", title = "Gambe", date = LocalDate.of(2026, 11, 8),
            start = LocalDateTime.of(2026, 11, 8, 18, 4, 33), durationMinutes = 55,
            caloriesKcal = null, averageHeartRateBpm = null, watchSource = null, exercises = emptyList(),
        )
        val other = TrainerLesson("old", LocalDate.of(2026, 11, 1))
        val on = TrainerData(lessons = listOf(other)).toggleLessonFor(session, "new")
        val added = on.lessons.single { it.id == "new" }
        assertEquals(LocalDate.of(2026, 11, 8), added.date)
        assertEquals(java.time.LocalTime.of(18, 4), added.time)
        assertEquals("Gambe", added.topic)
        assertEquals(55, added.durationMinutes)
        val off = on.toggleLessonFor(session, "unused")
        assertEquals(listOf(other), off.lessons)
    }

    @Test
    fun readsDatesAsTyped() {
        assertEquals(LocalDate.of(2026, 11, 6), FormValues.date("6.11.2026"))
        assertEquals(LocalDate.of(2026, 11, 6), FormValues.date("06/11/2026"))
        assertEquals(java.time.LocalTime.of(9, 5), FormValues.time("9.05"))
        assertEquals(12.5, FormValues.number("12,5")!!, 0.001)
    }
}

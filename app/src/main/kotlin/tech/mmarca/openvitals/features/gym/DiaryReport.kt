package tech.mmarca.openvitals.features.gym

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import tech.mmarca.openvitals.R

/** Everything the monthly report draws from. */
internal class ReportData(
    val sessions: List<GymSession>,
    val bodyReports: List<BodyReport>,
    val trainer: TrainerData,
    val nutrition: List<NutritionVisit>,
    val health: HealthData,
)

/**
 * Diario Palestra: the month on paper (a PDF to send to the trainer or the nutritionist)
 * and the sessions as a CSV for Excel.
 */
internal object DiaryReport {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48f

    fun pdf(context: Context, out: OutputStream, data: ReportData, month: YearMonth = YearMonth.now()) {
        val locale = Locale.getDefault()
        val day = DateTimeFormatter.ofPattern("EEE d MMM", locale)
        val monthName = month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        val doc = PdfDocument()
        val writer = PageWriter(doc)

        writer.title(context.getString(R.string.report_title, monthName))
        writer.small(context.getString(R.string.report_made, LocalDate.now().format(DateTimeFormatter.ofPattern("d.M.yyyy"))))

        // Gym
        val inMonth = data.sessions.filter { YearMonth.from(it.date) == month }.sortedBy { it.start }
        writer.heading(context.getString(R.string.diario_tab_gym))
        val minutes = inMonth.sumOf { it.durationMinutes ?: 0L }
        val volume = inMonth.sumOf { it.volumeKg }
        val kcal = inMonth.sumOf { it.caloriesKcal ?: 0.0 }
        writer.line(
            context.getString(
                R.string.report_gym_summary,
                inMonth.size,
                String.format(locale, "%.1f", minutes / 60.0),
                String.format(locale, "%.1f", volume / 1000.0),
                kcal.roundToInt(),
            ),
        )
        val stats = GymSessions.stats(data.sessions)
        writer.line(context.getString(R.string.report_gym_streak, stats.weekStreak, GymInsights.bestStreak(data.sessions)))
        inMonth.forEach { s ->
            val parts = listOfNotNull(
                s.durationMinutes?.let { "$it min" },
                s.caloriesKcal?.let { "${it.roundToInt()} kcal" },
                s.averageHeartRateBpm?.let { "$it bpm" },
                s.volumeKg.takeIf { it > 0 }?.let { "${it.roundToInt()} kg" },
            )
            val with = data.trainer.lessons.firstOrNull { it.date == s.date }?.let { " · " + context.getString(R.string.gym_with_trainer, data.trainer.trainerOf(it)) }.orEmpty()
            val title = s.title.ifBlank { context.getString(R.string.gym_session_untitled) }
            writer.bullet("${s.date.format(day)} — $title$with: ${parts.joinToString(" · ")}")
        }
        if (inMonth.isEmpty()) writer.line(context.getString(R.string.report_none))

        // Body
        writer.heading(context.getString(R.string.diario_tab_body))
        val reports = data.bodyReports.sortedByDescending { it.date }
        val latest = reports.firstOrNull()
        if (latest == null) {
            writer.line(context.getString(R.string.report_none))
        } else {
            val previous = reports.drop(1).firstOrNull()
            writer.line(context.getString(R.string.report_body_latest, latest.date.format(DateTimeFormatter.ofPattern("d.M.yyyy"))))
            listOf(BodyField.WEIGHT, BodyField.FAT_PERCENT, BodyField.SKELETAL_MUSCLE, BodyField.LEAN_MASS, BodyField.WAIST, BodyField.VISCERAL).forEach { f ->
                val v = latest[f] ?: return@forEach
                val p = previous?.get(f)
                val delta = p?.let { d -> (v - d).let { (if (it >= 0) " (+" else " (−") + kotlin.math.abs(it).bodyNumber() + ")" } }.orEmpty()
                writer.bullet("${context.getString(f.labelRes)}: ${v.bodyNumber()} ${f.unit}$delta".trim())
            }
        }

        // Trainer
        writer.heading(context.getString(R.string.diario_tab_trainer))
        data.trainer.trainers.forEach { name ->
            val account = data.trainer.account(name)
            val lessons = data.trainer.lessons.count { data.trainer.trainerOf(it) == name && YearMonth.from(it.date) == month }
            writer.bullet(
                context.getString(
                    R.string.report_trainer_line,
                    name,
                    lessons,
                    account.remaining?.toString() ?: "—",
                ),
            )
        }

        // Nutrition
        writer.heading(context.getString(R.string.diario_tab_nutrition))
        val plan = data.nutrition.maxByOrNull { it.date }
        if (plan == null) {
            writer.line(context.getString(R.string.report_none))
        } else {
            writer.line(
                context.getString(
                    R.string.report_nutrition_plan,
                    plan.date.format(DateTimeFormatter.ofPattern("d.M.yyyy")),
                    plan.kcal?.roundToInt()?.toString() ?: "—",
                    plan.proteinG?.roundToInt()?.toString() ?: "—",
                    plan.carbsG?.roundToInt()?.toString() ?: "—",
                    plan.fatG?.roundToInt()?.toString() ?: "—",
                ),
            )
            plan.targetWeightKg?.let { writer.line(context.getString(R.string.report_nutrition_target, it.bodyNumber())) }
        }

        // Health
        writer.heading(context.getString(R.string.diario_tab_health))
        val today = LocalDate.now()
        val visits = data.health.visits.filter { YearMonth.from(it.date) == month }.sortedBy { it.date }
        visits.forEach { v ->
            writer.bullet(
                listOfNotNull(
                    v.date.format(day),
                    context.getString(v.type.label),
                    v.provider,
                    v.reason,
                    v.costChf?.let { "CHF " + it.bodyNumber() },
                ).joinToString(" · "),
            )
        }
        if (visits.isEmpty()) writer.line(context.getString(R.string.report_none))
        val due = data.health.checks.map { data.health.state(it, today) }.filter { it.status == CheckStatus.OVERDUE || it.status == CheckStatus.SOON }
        due.forEach { s ->
            writer.bullet(context.getString(R.string.report_health_due, s.check.name ?: context.getString(s.check.type.label), s.next?.format(DateTimeFormatter.ofPattern("d.M.yyyy")) ?: "—"))
        }

        writer.finish()
        doc.writeTo(out)
        doc.close()
    }

    /** One row per set (or one per session without sets), the way Excel likes it. */
    fun csv(out: OutputStream, sessions: List<GymSession>) {
        val sb = StringBuilder()
        sb.append("data;ora;titolo;durata_min;kcal;bpm;esercizio;serie;kg;ripetizioni;km;secondi\n")
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        fun n(d: Double?) = d?.let { String.format(Locale.ROOT, "%.2f", it).trimEnd('0').trimEnd('.') }.orEmpty()
        sessions.sortedBy { it.start }.forEach { s ->
            val head = listOf(
                s.date.toString(),
                s.start.toLocalTime().withSecond(0).withNano(0).toString(),
                q(s.title),
                s.durationMinutes?.toString().orEmpty(),
                s.caloriesKcal?.roundToInt()?.toString().orEmpty(),
                s.averageHeartRateBpm?.toString().orEmpty(),
            ).joinToString(";")
            if (s.exercises.isEmpty()) {
                sb.append(head).append(";;;;;;\n")
            } else {
                s.exercises.forEach { e ->
                    e.sets.forEachIndexed { i, set ->
                        sb.append(head).append(';').append(q(e.name)).append(';').append(i + 1).append(';')
                            .append(n(set.kg)).append(';').append(set.reps?.toString().orEmpty()).append(';')
                            .append(n(set.km)).append(';').append(set.seconds?.toString().orEmpty()).append('\n')
                    }
                }
            }
        }
        out.write(("﻿" + sb).toByteArray(Charsets.UTF_8))
    }

    /** Writes text top to bottom, starting a new page when one is full. */
    private class PageWriter(private val doc: PdfDocument) {
        private var number = 0
        private var page: PdfDocument.Page? = null
        private var y = 0f
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = AndroidColor.rgb(30, 30, 30) }
        private val muted = Paint(text).apply { textSize = 9f; color = AndroidColor.rgb(120, 120, 120) }
        private val head = Paint(text).apply { textSize = 13f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = AndroidColor.rgb(59, 91, 219) }
        private val big = Paint(text).apply { textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

        private fun ensure(height: Float) {
            if (page == null || y + height > PAGE_H - MARGIN) {
                page?.let { doc.finishPage(it) }
                number++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, number).create())
                y = MARGIN
            }
        }

        private fun draw(s: String, paint: Paint, indent: Float = 0f, gap: Float = 4f) {
            wrap(s, paint, PAGE_W - 2 * MARGIN - indent).forEach { lineText ->
                ensure(paint.textSize + gap)
                y += paint.textSize
                page!!.canvas.drawText(lineText, MARGIN + indent, y, paint)
                y += gap
            }
        }

        private fun wrap(s: String, paint: Paint, width: Float): List<String> {
            val lines = mutableListOf<String>()
            var current = ""
            s.split(" ").forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= width || current.isEmpty()) current = candidate else {
                    lines += current
                    current = word
                }
            }
            if (current.isNotEmpty()) lines += current
            return lines
        }

        fun title(s: String) = draw(s, big, gap = 6f)
        fun small(s: String) = draw(s, muted, gap = 10f)
        fun heading(s: String) {
            ensure(40f)
            y += 12f
            draw(s.uppercase(), head, gap = 6f)
        }
        fun line(s: String) = draw(s, text)
        fun bullet(s: String) = draw("• $s", text, indent = 8f)
        fun finish() {
            ensure(0f)
            page?.let { doc.finishPage(it) }
        }
    }
}

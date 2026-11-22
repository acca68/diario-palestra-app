package tech.mmarca.openvitals.features.gym

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tech.mmarca.openvitals.R
import tech.mmarca.openvitals.ui.components.OpenVitalsCard

/** Diario Palestra: the kinds of health visit. */
enum class HealthType(@param:StringRes val label: Int) {
    GP(R.string.health_type_gp),
    SPECIALIST(R.string.health_type_specialist),
    DENTIST(R.string.health_type_dentist),
    HYGIENIST(R.string.health_type_hygienist),
    PHYSIO(R.string.health_type_physio),
    LAB(R.string.health_type_lab),
    EXAM(R.string.health_type_exam),
    OTHER(R.string.health_type_other),
    ;

    companion object {
        fun of(name: String?): HealthType = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** A visit to a doctor, a specialist, the dentist…; a date in the future is an appointment. */
@Immutable
data class HealthVisit(
    val id: String,
    val date: LocalDate,
    val type: HealthType,
    val time: LocalTime? = null,
    val provider: String? = null,
    val reason: String? = null,
    val costChf: Double? = null,
    val note: String? = null,
)

/**
 * A check to repeat, like dental hygiene every 6 months: the last one is the latest past visit
 * of [type] (and, when [match] is set, whose doctor or reason mentions it).
 */
@Immutable
data class HealthCheck(
    val id: String,
    val type: HealthType,
    val everyMonths: Int,
    val name: String? = null,
    val match: String? = null,
)

enum class CheckStatus { OVERDUE, SOON, OK, NEVER }

@Immutable
data class CheckState(val check: HealthCheck, val last: LocalDate?, val next: LocalDate?, val status: CheckStatus)

@Immutable
data class HealthData(val visits: List<HealthVisit> = emptyList(), val checks: List<HealthCheck> = emptyList()) {
    fun past(today: LocalDate): List<HealthVisit> = visits.filter { !it.date.isAfter(today) }.sortedByDescending { it.date }

    fun upcoming(today: LocalDate): List<HealthVisit> = visits.filter { it.date.isAfter(today) }.sortedBy { it.date }

    fun state(check: HealthCheck, today: LocalDate): CheckState {
        val key = check.match?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val last = past(today).firstOrNull { v ->
            v.type == check.type && (
                key == null ||
                    v.provider.orEmpty().lowercase().contains(key) ||
                    v.reason.orEmpty().lowercase().contains(key)
                )
        }?.date
        // A visit of that kind already booked counts as the next one.
        val booked = upcoming(today).firstOrNull { it.type == check.type }?.date
        val next = booked ?: last?.plusMonths(check.everyMonths.toLong())
        val status = when {
            booked != null -> CheckStatus.OK
            next == null -> CheckStatus.NEVER
            next.isBefore(today) -> CheckStatus.OVERDUE
            !next.isAfter(today.plusDays(SOON_DAYS)) -> CheckStatus.SOON
            else -> CheckStatus.OK
        }
        return CheckState(check, last, next, status)
    }

    /** Costs of the visits of [year], by kind, largest first. */
    fun costs(year: Int, today: LocalDate): List<Pair<HealthType, Double>> =
        past(today).filter { it.date.year == year && (it.costChf ?: 0.0) > 0 }
            .groupBy { it.type }
            .map { (type, list) -> type to list.sumOf { it.costChf ?: 0.0 } }
            .sortedByDescending { it.second }

    companion object {
        const val SOON_DAYS = 30L
    }
}

@Singleton
class HealthStore @Inject constructor(@param:ApplicationContext context: Context) {
    private val file = DiaryJsonFile(context, FILE_NAME)
    private val mutex = Mutex()

    suspend fun load(): HealthData = mutex.withLock { read() }

    suspend fun update(change: (HealthData) -> HealthData) = mutex.withLock { write(change(read())) }

    private suspend fun read(): HealthData {
        val root = file.read() as? JsonObject ?: return HealthData()
        return HealthData(
            visits = (root["visits"] as? JsonArray).orEmpty().mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                HealthVisit(
                    id = o.str("id") ?: return@mapNotNull null,
                    date = o.date("date") ?: return@mapNotNull null,
                    type = HealthType.of(o.str("type")),
                    time = o.time("time"),
                    provider = o.str("provider"),
                    reason = o.str("reason"),
                    costChf = o.num("cost"),
                    note = o.str("note"),
                )
            },
            checks = (root["checks"] as? JsonArray).orEmpty().mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                HealthCheck(
                    id = o.str("id") ?: return@mapNotNull null,
                    type = HealthType.of(o.str("type")),
                    everyMonths = o.num("months")?.toInt()?.takeIf { it > 0 } ?: return@mapNotNull null,
                    name = o.str("name"),
                    match = o.str("match"),
                )
            },
        )
    }

    private suspend fun write(data: HealthData) {
        file.write(
            buildJsonObject {
                put("version", 1)
                put(
                    "visits",
                    buildJsonArray {
                        data.visits.sortedBy { it.date }.forEach { v ->
                            add(
                                buildJsonObject {
                                    put("id", v.id)
                                    put("date", v.date.toString())
                                    put("type", v.type.name)
                                    put("time", v.time?.toString())
                                    put("provider", v.provider)
                                    put("reason", v.reason)
                                    put("cost", v.costChf)
                                    put("note", v.note)
                                },
                            )
                        }
                    },
                )
                put(
                    "checks",
                    buildJsonArray {
                        data.checks.forEach { c ->
                            add(
                                buildJsonObject {
                                    put("id", c.id)
                                    put("type", c.type.name)
                                    put("months", c.everyMonths)
                                    put("name", c.name)
                                    put("match", c.match)
                                },
                            )
                        }
                    },
                )
            },
        )
    }

    companion object {
        const val FILE_NAME = "diario_palestra_salute.json"
    }
}

sealed interface HealthEditor {
    data class Visit(val visit: HealthVisit?) : HealthEditor
    data class Check(val check: HealthCheck?) : HealthEditor
}

@Immutable
data class HealthUiState(val data: HealthData = HealthData(), val editor: HealthEditor? = null)

@HiltViewModel
class HealthViewModel @Inject constructor(private val store: HealthStore) : ViewModel() {
    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch { _uiState.update { it.copy(data = store.load(), editor = null) } }
    }

    fun open(editor: HealthEditor?) = _uiState.update { it.copy(editor = editor) }

    private fun change(transform: (HealthData) -> HealthData) {
        viewModelScope.launch {
            store.update(transform)
            reload()
        }
    }

    fun saveVisit(previous: HealthVisit?, v: Map<String, String>): Int? {
        val date = FormValues.date(v["date"]) ?: return R.string.body_error_date
        if (FormValues.text(v["time"]) != null && FormValues.time(v["time"]) == null) return R.string.body_error_time
        val visit = HealthVisit(
            id = previous?.id ?: UUID.randomUUID().toString(),
            date = date,
            type = HealthType.of(v["type"]),
            time = FormValues.time(v["time"]),
            provider = FormValues.text(v["provider"]),
            reason = FormValues.text(v["reason"]),
            costChf = FormValues.number(v["cost"]),
            note = FormValues.text(v["note"]),
        )
        change { d -> d.copy(visits = d.visits.filter { it.id != visit.id } + visit) }
        return null
    }

    fun saveCheck(previous: HealthCheck?, v: Map<String, String>): Int? {
        val months = FormValues.number(v["months"])?.toInt()?.takeIf { it > 0 } ?: return R.string.health_error_months
        val check = HealthCheck(
            id = previous?.id ?: UUID.randomUUID().toString(),
            type = HealthType.of(v["type"]),
            everyMonths = months,
            name = FormValues.text(v["name"]),
            match = FormValues.text(v["match"]),
        )
        change { d -> d.copy(checks = d.checks.filter { it.id != check.id } + check) }
        return null
    }

    fun deleteVisit(id: String) = change { d -> d.copy(visits = d.visits.filter { it.id != id }) }

    fun deleteCheck(id: String) = change { d -> d.copy(checks = d.checks.filter { it.id != id }) }
}

private val healthDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())

@Composable
fun HealthTab(viewModel: HealthViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val data = state.data
    val today = LocalDate.now()
    HealthEditorHost(state.editor, viewModel)

    val upcoming = data.upcoming(today)
    val past = data.past(today)
    val checks = data.checks.map { data.state(it, today) }
        .sortedWith(compareBy({ it.status.ordinal }, { it.next ?: LocalDate.MAX }))
    val costs = data.costs(today.year, today)
    val due = checks.count { it.status == CheckStatus.OVERDUE || it.status == CheckStatus.SOON }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val next = upcoming.firstOrNull()
            DiaryHeroCard(
                title = stringResource(R.string.diario_tab_health),
                subtitle = next?.let {
                    stringResource(
                        R.string.health_next,
                        stringResource(it.type.label),
                        it.date.format(healthDate),
                        ChronoUnit.DAYS.between(today, it.date),
                    )
                } ?: stringResource(R.string.health_no_next),
                stats = listOf(
                    past.count { it.date.year == today.year }.toString() to stringResource(R.string.health_visits_year, today.year),
                    (costs.sumOf { it.second }.takeIf { it > 0 }?.bodyNumber() ?: "—") to stringResource(R.string.health_spent_year),
                    due.toString() to stringResource(R.string.health_checks_due),
                ),
                action = { DiaryPill(stringResource(R.string.health_visit), Icons.Outlined.Add) { viewModel.open(HealthEditor.Visit(null)) } },
            )
        }
        if (upcoming.isNotEmpty()) {
            item { DiarySectionHeader(stringResource(R.string.health_upcoming)) }
            items(upcoming, key = { "u" + it.id }) { visit -> VisitRow(visit, today) { viewModel.open(HealthEditor.Visit(visit)) } }
        }
        item {
            DiarySectionHeader(stringResource(R.string.health_checks)) {
                TextButton(onClick = { viewModel.open(HealthEditor.Check(null)) }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.health_check))
                }
            }
        }
        if (checks.isEmpty()) {
            item { DiaryNote(stringResource(R.string.health_checks_empty)) }
        }
        items(checks, key = { "c" + it.check.id }) { s -> CheckRow(s, today) { viewModel.open(HealthEditor.Check(s.check)) } }
        if (costs.isNotEmpty()) {
            item { DiarySectionHeader(stringResource(R.string.health_costs, today.year)) }
            item { CostsCard(costs) }
        }
        item { DiarySectionHeader(stringResource(R.string.health_history)) }
        if (past.isEmpty()) {
            item { DiaryNote(stringResource(R.string.health_empty)) }
        }
        items(past, key = { "p" + it.id }) { visit -> VisitRow(visit, today) { viewModel.open(HealthEditor.Visit(visit)) } }
    }
}

@Composable
private fun VisitRow(visit: HealthVisit, today: LocalDate, onClick: () -> Unit) {
    OpenVitalsCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(visit.type.label).uppercase(),
                style = DiaryType.smallLabel,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    visit.provider ?: visit.reason ?: stringResource(visit.type.label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    visit.date.format(healthDate) + (visit.time?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (visit.date.isAfter(today)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val details = listOfNotNull(
                visit.reason?.takeIf { visit.provider != null },
                visit.costChf?.let { "CHF " + it.bodyNumber() },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            visit.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun CheckRow(s: CheckState, today: LocalDate, onClick: () -> Unit) {
    val color = when (s.status) {
        CheckStatus.OVERDUE -> MaterialTheme.colorScheme.error
        CheckStatus.SOON -> MaterialTheme.colorScheme.tertiary
        CheckStatus.OK -> Color(0xFF3FA66B)
        CheckStatus.NEVER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    OpenVitalsCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.check.name ?: stringResource(s.check.type.label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when (s.status) {
                        CheckStatus.OVERDUE -> stringResource(R.string.health_status_overdue, ChronoUnit.DAYS.between(s.next, today))
                        CheckStatus.SOON -> stringResource(R.string.health_status_soon, ChronoUnit.DAYS.between(today, s.next))
                        CheckStatus.OK -> stringResource(R.string.health_status_ok)
                        CheckStatus.NEVER -> stringResource(R.string.health_status_never)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
            }
            Text(
                listOfNotNull(
                    stringResource(R.string.health_every_months, s.check.everyMonths),
                    s.last?.let { stringResource(R.string.health_last, it.format(healthDate)) },
                    s.next?.let { stringResource(R.string.health_next_due, it.format(healthDate)) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CostsCard(costs: List<Pair<HealthType, Double>>) {
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            costs.forEach { (type, chf) ->
                Row {
                    Text(stringResource(type.label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("CHF " + chf.bodyNumber(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            Row {
                Text(stringResource(R.string.health_total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "CHF " + costs.sumOf { it.second }.bodyNumber(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun HealthEditorHost(editor: HealthEditor?, viewModel: HealthViewModel) {
    val close = { viewModel.open(null) }
    val types = HealthType.entries.map { it.name to stringResource(it.label) }
    when (editor) {
        null -> Unit
        is HealthEditor.Visit -> {
            val v = editor.visit
            DiaryFormDialog(
                title = if (v == null) R.string.health_new_visit else R.string.health_edit_visit,
                fields = listOf(
                    FormField("type", R.string.health_type, FormKind.CHOICE, (v?.type ?: HealthType.GP).name, types),
                    FormField("date", R.string.body_date, FormKind.DATE, (v?.date ?: LocalDate.now()).format(FormValues.dateOutput)),
                    FormField("time", R.string.body_time, FormKind.TIME, v?.time?.toString().orEmpty()),
                    FormField("provider", R.string.health_provider, FormKind.TEXT, v?.provider.orEmpty()),
                    FormField("reason", R.string.health_reason, FormKind.TEXT, v?.reason.orEmpty()),
                    FormField("cost", R.string.trainer_cost, FormKind.NUMBER, FormValues.format(v?.costChf)),
                    FormField("note", R.string.health_note, FormKind.TEXT, v?.note.orEmpty()),
                ),
                onDismiss = close,
                validate = { viewModel.saveVisit(v, it) },
                onDelete = v?.let { { viewModel.deleteVisit(it.id) } },
            )
        }
        is HealthEditor.Check -> {
            val c = editor.check
            DiaryFormDialog(
                title = if (c == null) R.string.health_new_check else R.string.health_edit_check,
                fields = listOf(
                    FormField("type", R.string.health_type, FormKind.CHOICE, (c?.type ?: HealthType.HYGIENIST).name, types),
                    FormField("name", R.string.health_check_name, FormKind.TEXT, c?.name.orEmpty()),
                    FormField("months", R.string.health_check_months, FormKind.NUMBER, c?.everyMonths?.toString().orEmpty()),
                    FormField("match", R.string.health_check_match, FormKind.TEXT, c?.match.orEmpty()),
                ),
                onDismiss = close,
                validate = { viewModel.saveCheck(c, it) },
                onDelete = c?.let { { viewModel.deleteCheck(it.id) } },
            )
        }
    }
}

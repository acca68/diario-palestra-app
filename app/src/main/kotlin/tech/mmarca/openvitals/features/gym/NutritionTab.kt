package tech.mmarca.openvitals.features.gym

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
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
import tech.mmarca.openvitals.data.repository.contract.NutritionRepository
import tech.mmarca.openvitals.domain.model.DailyMacros
import tech.mmarca.openvitals.ui.components.OpenVitalsCard

/** Diario Palestra: a visit to the nutritionist and the plan it set. */
@Immutable
data class NutritionVisit(
    val id: String,
    val date: LocalDate,
    val kcal: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val targetWeightKg: Double? = null,
    val nextVisit: LocalDate? = null,
    val costChf: Double? = null,
    val note: String? = null,
)

@Singleton
class NutritionStore @Inject constructor(@param:ApplicationContext context: Context) {
    private val file = DiaryJsonFile(context, "diario_palestra_nutrizionista.json")
    private val mutex = Mutex()

    suspend fun load(): List<NutritionVisit> = mutex.withLock { read() }

    suspend fun update(change: (List<NutritionVisit>) -> List<NutritionVisit>) = mutex.withLock {
        val visits = change(read())
        file.write(
            buildJsonObject {
                put("version", 1)
                put(
                    "visits",
                    buildJsonArray {
                        visits.sortedBy { it.date }.forEach { v ->
                            add(
                                buildJsonObject {
                                    put("id", v.id)
                                    put("date", v.date.toString())
                                    put("kcal", v.kcal)
                                    put("protein", v.proteinG)
                                    put("carbs", v.carbsG)
                                    put("fat", v.fatG)
                                    put("target", v.targetWeightKg)
                                    put("next", v.nextVisit?.toString())
                                    put("cost", v.costChf)
                                    put("note", v.note)
                                },
                            )
                        }
                    },
                )
            },
        )
    }

    private suspend fun read(): List<NutritionVisit> {
        val root = file.read() as? JsonObject ?: return emptyList()
        return (root["visits"] as? JsonArray).orEmpty().mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            NutritionVisit(
                id = o.str("id") ?: return@mapNotNull null,
                date = o.date("date") ?: return@mapNotNull null,
                kcal = o.num("kcal"),
                proteinG = o.num("protein"),
                carbsG = o.num("carbs"),
                fatG = o.num("fat"),
                targetWeightKg = o.num("target"),
                nextVisit = o.date("next"),
                costChf = o.num("cost"),
                note = o.str("note"),
            )
        }
    }
}

@Immutable
data class NutritionUiState(
    val visits: List<NutritionVisit> = emptyList(),
    val days: List<DailyMacros> = emptyList(),
    val editing: NutritionVisit? = null,
    val isEditorOpen: Boolean = false,
) {
    /** The plan in force: the latest visit that set calories or macros. */
    val plan: NutritionVisit? get() = visits.filter { it.kcal != null || it.proteinG != null }.maxByOrNull { it.date }
    val nextVisit: LocalDate? get() = visits.mapNotNull { it.nextVisit }.filter { !it.isBefore(LocalDate.now()) }.minOrNull()
    val loggedDays: List<DailyMacros> get() = days.filter { it.energyKcal > 0 }
}

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val store: NutritionStore,
    private val nutritionRepository: NutritionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NutritionUiState())
    val uiState: StateFlow<NutritionUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val visits = store.load()
            val today = LocalDate.now()
            val days = runCatching { nutritionRepository.loadDailyMacros(today.minusDays(6), today) }.getOrDefault(emptyList())
            _uiState.update {
                it.copy(visits = visits.sortedByDescending { v -> v.date }, days = days.sortedBy { d -> d.date }, isEditorOpen = false, editing = null)
            }
        }
    }

    fun open(visit: NutritionVisit?) = _uiState.update { it.copy(isEditorOpen = true, editing = visit) }

    fun close() = _uiState.update { it.copy(isEditorOpen = false, editing = null) }

    fun save(previous: NutritionVisit?, v: Map<String, String>): Int? {
        val date = FormValues.date(v["date"]) ?: return R.string.body_error_date
        if (FormValues.text(v["next"]) != null && FormValues.date(v["next"]) == null) return R.string.body_error_date
        val visit = NutritionVisit(
            id = previous?.id ?: UUID.randomUUID().toString(),
            date = date,
            kcal = FormValues.number(v["kcal"]),
            proteinG = FormValues.number(v["protein"]),
            carbsG = FormValues.number(v["carbs"]),
            fatG = FormValues.number(v["fat"]),
            targetWeightKg = FormValues.number(v["target"]),
            nextVisit = FormValues.date(v["next"]),
            costChf = FormValues.number(v["cost"]),
            note = FormValues.text(v["note"]),
        )
        viewModelScope.launch {
            store.update { list -> list.filter { it.id != visit.id } + visit }
            reload()
        }
        return null
    }

    fun delete(id: String) {
        viewModelScope.launch {
            store.update { list -> list.filter { it.id != id } }
            reload()
        }
    }
}

private val visitDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

@Composable
fun NutritionTab(viewModel: NutritionViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isEditorOpen) {
        val e = state.editing
        DiaryFormDialog(
            title = if (e == null) R.string.nutri_new_visit else R.string.nutri_edit_visit,
            fields = listOf(
                FormField("date", R.string.body_date, FormKind.DATE, (e?.date ?: LocalDate.now()).format(FormValues.dateOutput)),
                FormField("kcal", R.string.nutri_kcal, FormKind.NUMBER, FormValues.format(e?.kcal)),
                FormField("protein", R.string.nutri_protein, FormKind.NUMBER, FormValues.format(e?.proteinG)),
                FormField("carbs", R.string.nutri_carbs, FormKind.NUMBER, FormValues.format(e?.carbsG)),
                FormField("fat", R.string.nutri_fat, FormKind.NUMBER, FormValues.format(e?.fatG)),
                FormField("target", R.string.nutri_target_weight, FormKind.NUMBER, FormValues.format(e?.targetWeightKg)),
                FormField("next", R.string.nutri_next_visit, FormKind.DATE, e?.nextVisit?.format(FormValues.dateOutput).orEmpty()),
                FormField("cost", R.string.trainer_cost, FormKind.NUMBER, FormValues.format(e?.costChf)),
                FormField("note", R.string.nutri_note, FormKind.TEXT, e?.note.orEmpty()),
            ),
            onDismiss = viewModel::close,
            validate = { viewModel.save(e, it) },
            onDelete = e?.let { { viewModel.delete(it.id) } },
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DiarySectionHeader(stringResource(R.string.nutri_plan)) }
        item { PlanCard(state.plan, state.nextVisit) }
        item { DiarySectionHeader(stringResource(R.string.nutri_week)) }
        item { WeekCard(state.plan, state.days, state.loggedDays) }
        item {
            DiarySectionHeader(stringResource(R.string.nutri_visits)) {
                TextButton(onClick = { viewModel.open(null) }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.diary_add))
                }
            }
        }
        items(state.visits, key = { it.id }) { visit ->
            OpenVitalsCard(modifier = Modifier.fillMaxWidth().clickable { viewModel.open(visit) }) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(visit.date.format(visitDate), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    val details = listOfNotNull(
                        visit.kcal?.let { "${it.roundToInt()} kcal" },
                        visit.targetWeightKg?.let { stringResource(R.string.nutri_target_short, it.bodyNumber()) },
                        visit.costChf?.let { "CHF " + it.bodyNumber() },
                    ).joinToString(" · ")
                    if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    visit.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: NutritionVisit?, nextVisit: LocalDate?) {
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (plan == null) {
                Text(
                    stringResource(R.string.nutri_plan_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanValue(plan.kcal?.roundToInt()?.toString(), "kcal", Modifier.weight(1f))
                    PlanValue(plan.proteinG?.roundToInt()?.toString(), stringResource(R.string.nutri_protein_short), Modifier.weight(1f))
                    PlanValue(plan.carbsG?.roundToInt()?.toString(), stringResource(R.string.nutri_carbs_short), Modifier.weight(1f))
                    PlanValue(plan.fatG?.roundToInt()?.toString(), stringResource(R.string.nutri_fat_short), Modifier.weight(1f))
                }
                plan.targetWeightKg?.let {
                    Text(stringResource(R.string.nutri_target_short, it.bodyNumber()), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(R.string.nutri_plan_since, plan.date.format(visitDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            nextVisit?.let {
                val days = ChronoUnit.DAYS.between(LocalDate.now(), it)
                Text(
                    stringResource(R.string.nutri_next_in, it.format(visitDate), days),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PlanValue(value: String?, label: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(value ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeekCard(plan: NutritionVisit?, days: List<DailyMacros>, logged: List<DailyMacros>) {
    val locale = Locale.getDefault()
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (logged.isEmpty()) {
                Text(
                    stringResource(R.string.nutri_week_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            fun avg(f: (DailyMacros) -> Double) = logged.map(f).average()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AverageValue(avg { it.energyKcal }, plan?.kcal, "kcal", Modifier.weight(1f))
                AverageValue(avg { it.proteinGrams }, plan?.proteinG, stringResource(R.string.nutri_protein_short), Modifier.weight(1f))
                AverageValue(avg { it.carbsGrams }, plan?.carbsG, stringResource(R.string.nutri_carbs_short), Modifier.weight(1f))
                AverageValue(avg { it.fatGrams }, plan?.fatG, stringResource(R.string.nutri_fat_short), Modifier.weight(1f))
            }
            Text(
                stringResource(R.string.nutri_week_average, logged.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val target = plan?.kcal
            val max = (days.maxOfOrNull { it.energyKcal } ?: 0.0).coerceAtLeast(target ?: 0.0).coerceAtLeast(1.0)
            days.forEach { day ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) + " " + day.date.dayOfMonth,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(64.dp),
                    )
                    Box(modifier = Modifier.weight(1f).height(12.dp)) {
                        if (day.energyKcal > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((day.energyKcal / max).toFloat().coerceIn(0.03f, 1f))
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(barColor(day.energyKcal, target)),
                            )
                        }
                    }
                    Text(
                        if (day.energyKcal > 0) "${day.energyKcal.roundToInt()}" else "—",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(48.dp).padding(start = 6.dp),
                    )
                }
            }
            if (target != null) {
                Text(
                    stringResource(R.string.nutri_week_hint, target.roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun barColor(kcal: Double, target: Double?): Color = when {
    target == null -> MaterialTheme.colorScheme.primary
    kcal > target * 1.1 -> Color(0xFFD08A2E)
    kcal < target * 0.8 -> MaterialTheme.colorScheme.tertiary
    else -> Color(0xFF3FA66B)
}

@Composable
private fun AverageValue(value: Double, target: Double?, label: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(value.roundToInt().toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            if (target != null) "$label / ${target.roundToInt()}" else label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

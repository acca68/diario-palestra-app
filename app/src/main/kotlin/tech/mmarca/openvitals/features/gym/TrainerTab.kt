package tech.mmarca.openvitals.features.gym

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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.mmarca.openvitals.R
import tech.mmarca.openvitals.ui.components.OpenVitalsCard

sealed interface TrainerEditor {
    data class Lesson(val lesson: TrainerLesson?) : TrainerEditor
    data class Package(val pack: TrainerPackage?) : TrainerEditor
    data object Name : TrainerEditor
}

@Immutable
data class TrainerUiState(
    val data: TrainerData = TrainerData(),
    val editor: TrainerEditor? = null,
)

@HiltViewModel
class TrainerViewModel @Inject constructor(private val store: TrainerStore) : ViewModel() {
    private val _uiState = MutableStateFlow(TrainerUiState())
    val uiState: StateFlow<TrainerUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch { _uiState.update { it.copy(data = store.load(), editor = null) } }
    }

    fun open(editor: TrainerEditor?) = _uiState.update { it.copy(editor = editor) }

    private fun change(transform: (TrainerData) -> TrainerData) {
        viewModelScope.launch {
            store.update(transform)
            reload()
        }
    }

    /** Returns the error to show, or null when saved. */
    fun saveLesson(previous: TrainerLesson?, v: Map<String, String>): Int? {
        val date = FormValues.date(v["date"]) ?: return R.string.body_error_date
        if (FormValues.text(v["time"]) != null && FormValues.time(v["time"]) == null) return R.string.body_error_time
        val lesson = TrainerLesson(
            id = previous?.id ?: UUID.randomUUID().toString(),
            date = date,
            time = FormValues.time(v["time"]),
            topic = FormValues.text(v["topic"]),
            durationMinutes = FormValues.number(v["minutes"])?.toInt(),
            costChf = FormValues.number(v["cost"]),
            extra = v["extra"] == "true",
            note = FormValues.text(v["note"]),
        )
        change { data -> data.copy(lessons = data.lessons.filter { it.id != lesson.id } + lesson) }
        return null
    }

    fun savePackage(previous: TrainerPackage?, v: Map<String, String>): Int? {
        val date = FormValues.date(v["date"]) ?: return R.string.body_error_date
        val lessons = FormValues.number(v["lessons"])?.toInt()?.takeIf { it > 0 } ?: return R.string.trainer_error_lessons
        val pack = TrainerPackage(
            id = previous?.id ?: UUID.randomUUID().toString(),
            date = date,
            lessons = lessons,
            amountChf = FormValues.number(v["amount"]),
            note = FormValues.text(v["note"]),
        )
        change { data -> data.copy(packages = data.packages.filter { it.id != pack.id } + pack) }
        return null
    }

    fun saveName(v: Map<String, String>): Int? {
        change { it.copy(trainerName = FormValues.text(v["name"]).orEmpty()) }
        return null
    }

    fun deleteLesson(id: String) = change { data -> data.copy(lessons = data.lessons.filter { it.id != id }) }

    fun deletePackage(id: String) = change { data -> data.copy(packages = data.packages.filter { it.id != id }) }
}

private val listDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())

@Composable
fun TrainerTab(viewModel: TrainerViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val data = state.data
    TrainerEditorHost(state.editor, data, viewModel)

    LazyColumn(
        modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val remaining = data.remaining
            DiaryHeroCard(
                title = data.trainerName.ifBlank { stringResource(R.string.trainer_title) },
                subtitle = if (remaining != null && remaining <= 2) {
                    stringResource(R.string.trainer_low, remaining)
                } else {
                    stringResource(R.string.trainer_title)
                },
                stats = listOf(
                    (remaining?.toString() ?: "—") to stringResource(R.string.trainer_remaining),
                    data.used.toString() to stringResource(R.string.trainer_done, data.bought),
                    (if (data.spentChf > 0) data.spentChf.bodyNumber() else "—") to stringResource(R.string.trainer_spent),
                ),
                action = {
                    IconButton(onClick = { viewModel.open(TrainerEditor.Name) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.trainer_set_name), tint = DiaryColors.onHeroMuted)
                    }
                    DiaryPill(stringResource(R.string.trainer_lesson), Icons.Outlined.Add) { viewModel.open(TrainerEditor.Lesson(null)) }
                },
            )
        }
        item {
            DiarySectionHeader(stringResource(R.string.trainer_section_entries)) {
                TextButton(onClick = { viewModel.open(TrainerEditor.Package(null)) }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.trainer_package))
                }
            }
        }
        if (data.packages.isEmpty() && data.lessons.isEmpty()) {
            item { DiaryNote(stringResource(R.string.trainer_empty)) }
        }
        val entries = data.lessons.map { it.date to (it as Any) } + data.packages.map { it.date to (it as Any) }
        items(entries.sortedByDescending { it.first }, key = { (_, e) -> if (e is TrainerLesson) "l" + e.id else "p" + (e as TrainerPackage).id }) { (_, entry) ->
            when (entry) {
                is TrainerLesson -> TrainerRow(
                    title = entry.topic ?: stringResource(R.string.trainer_lesson),
                    date = entry.date.format(listDate) + (entry.time?.let { " · $it" } ?: ""),
                    details = listOfNotNull(
                        entry.durationMinutes?.let { stringResource(R.string.gym_minutes, it) },
                        entry.costChf?.let { "CHF " + it.bodyNumber() },
                        if (entry.extra) stringResource(R.string.trainer_extra_short) else null,
                        entry.note,
                    ).joinToString(" · "),
                    onClick = { viewModel.open(TrainerEditor.Lesson(entry)) },
                )
                is TrainerPackage -> TrainerRow(
                    title = stringResource(R.string.trainer_package_title, entry.lessons),
                    date = entry.date.format(listDate),
                    details = listOfNotNull(entry.amountChf?.let { "CHF " + it.bodyNumber() }, entry.note).joinToString(" · "),
                    onClick = { viewModel.open(TrainerEditor.Package(entry)) },
                )
            }
        }
    }
}

@Composable
private fun TrainerStat(value: String, label: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TrainerRow(title: String, date: String, details: String, onClick: () -> Unit) {
    OpenVitalsCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TrainerEditorHost(editor: TrainerEditor?, data: TrainerData, viewModel: TrainerViewModel) {
    val close = { viewModel.open(null) }
    when (editor) {
        null -> Unit
        TrainerEditor.Name -> DiaryFormDialog(
            title = R.string.trainer_set_name,
            fields = listOf(FormField("name", R.string.trainer_name, FormKind.TEXT, data.trainerName)),
            onDismiss = close,
            validate = viewModel::saveName,
        )
        is TrainerEditor.Lesson -> {
            val l = editor.lesson
            DiaryFormDialog(
                title = if (l == null) R.string.trainer_new_lesson else R.string.trainer_edit_lesson,
                fields = listOf(
                    FormField("date", R.string.body_date, FormKind.DATE, (l?.date ?: java.time.LocalDate.now()).format(FormValues.dateOutput)),
                    FormField("time", R.string.body_time, FormKind.TIME, l?.time?.toString().orEmpty()),
                    FormField("topic", R.string.trainer_topic, FormKind.TEXT, l?.topic.orEmpty()),
                    FormField("minutes", R.string.trainer_minutes, FormKind.NUMBER, l?.durationMinutes?.toString().orEmpty()),
                    FormField("cost", R.string.trainer_cost, FormKind.NUMBER, FormValues.format(l?.costChf)),
                    FormField("extra", R.string.trainer_extra, FormKind.CHECK, (l?.extra ?: false).toString()),
                    FormField("note", R.string.trainer_note, FormKind.TEXT, l?.note.orEmpty()),
                ),
                onDismiss = close,
                validate = { viewModel.saveLesson(l, it) },
                onDelete = l?.let { { viewModel.deleteLesson(it.id) } },
            )
        }
        is TrainerEditor.Package -> {
            val p = editor.pack
            DiaryFormDialog(
                title = if (p == null) R.string.trainer_new_package else R.string.trainer_edit_package,
                fields = listOf(
                    FormField("date", R.string.body_date, FormKind.DATE, (p?.date ?: java.time.LocalDate.now()).format(FormValues.dateOutput)),
                    FormField("lessons", R.string.trainer_lessons_count, FormKind.NUMBER, p?.lessons?.toString().orEmpty()),
                    FormField("amount", R.string.trainer_amount, FormKind.NUMBER, FormValues.format(p?.amountChf)),
                    FormField("note", R.string.trainer_note, FormKind.TEXT, p?.note.orEmpty()),
                ),
                onDismiss = close,
                validate = { viewModel.savePackage(p, it) },
                onDelete = p?.let { { viewModel.deletePackage(it.id) } },
            )
        }
    }
}

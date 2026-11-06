package tech.mmarca.openvitals.features.gym

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import tech.mmarca.openvitals.R

private val editorDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val editorTime = DateTimeFormatter.ofPattern("HH:mm")
private val editorDateInput = DateTimeFormatter.ofPattern("d.M.yyyy")

/** Full-screen form with every line of the scale report; empty fields are simply not saved. */
@Composable
internal fun BodyEditorDialog(
    initial: BodyReport?,
    onDismiss: () -> Unit,
    onSave: (BodyReport) -> Unit,
    onDelete: (BodyReport) -> Unit,
) {
    var dateText by remember { mutableStateOf((initial?.date ?: LocalDate.now()).format(editorDate)) }
    var timeText by remember { mutableStateOf(initial?.time?.format(editorTime).orEmpty()) }
    val values = remember {
        mutableStateMapOf<BodyField, String>().apply {
            initial?.values?.forEach { (field, value) -> put(field, value.bodyNumber()) }
        }
    }
    var error by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.body_cancel)) }
                    Text(
                        text = stringResource(if (initial == null) R.string.body_new else R.string.body_edit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Button(
                        onClick = {
                            val date = runCatching { LocalDate.parse(dateText.trim().replace('/', '.').replace('-', '.'), editorDateInput) }.getOrNull()
                            val timeRaw = timeText.trim().replace('.', ':')
                            val time = timeRaw.takeIf { it.isNotEmpty() }
                                ?.let { runCatching { LocalTime.parse(it.padStart(5, '0'), editorTime) }.getOrNull() }
                            val timeInvalid = timeRaw.isNotEmpty() && time == null
                            val parsed = values.mapNotNull { (field, text) ->
                                text.trim().replace(',', '.').toDoubleOrNull()?.let { field to it }
                            }.toMap()
                            when {
                                date == null -> error = R.string.body_error_date
                                timeInvalid -> error = R.string.body_error_time
                                parsed.isEmpty() -> error = R.string.body_error_empty
                                else -> onSave(
                                    BodyReport(
                                        date = date,
                                        time = time,
                                        values = parsed,
                                        healthConnectIds = initial?.healthConnectIds.orEmpty(),
                                    ),
                                )
                            }
                        },
                    ) { Text(stringResource(R.string.body_save)) }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    error?.let {
                        Text(text = stringResource(it), color = MaterialTheme.colorScheme.error)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dateText,
                            onValueChange = { dateText = it },
                            label = { Text(stringResource(R.string.body_date)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1.4f),
                        )
                        OutlinedTextField(
                            value = timeText,
                            onValueChange = { timeText = it },
                            label = { Text(stringResource(R.string.body_time)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    listOf(
                        BodyFieldGroup.COMPOSITION to R.string.body_group_composition,
                        BodyFieldGroup.CIRCUMFERENCE to R.string.body_group_circumferences,
                        BodyFieldGroup.SEGMENTAL to R.string.body_group_segmental,
                    ).forEach { (group, title) ->
                        Text(
                            text = stringResource(title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        BodyField.entries.filter { it.group == group }.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { field ->
                                    OutlinedTextField(
                                        value = values[field].orEmpty(),
                                        onValueChange = { values[field] = it },
                                        label = {
                                            Text(
                                                stringResource(field.labelRes) +
                                                    if (field.unit.isNotEmpty()) " (${field.unit})" else "",
                                                maxLines = 1,
                                            )
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (pair.size == 1) Column(modifier = Modifier.weight(1f)) {}
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.body_editor_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (initial != null) {
                        TextButton(
                            onClick = { if (confirmDelete) onDelete(initial) else confirmDelete = true },
                            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                        ) {
                            Text(
                                text = stringResource(if (confirmDelete) R.string.body_delete_confirm else R.string.body_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

package tech.mmarca.openvitals.features.gym

import android.content.Context
import androidx.annotation.StringRes
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
import androidx.compose.material3.Checkbox
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
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import tech.mmarca.openvitals.R

/** Diario Palestra: one JSON file of the app's private storage, written atomically. */
internal class DiaryJsonFile(private val context: Context, private val name: String) {
    suspend fun read(): JsonElement? = withContext(Dispatchers.IO) {
        val f = File(context.filesDir, name)
        if (!f.exists()) null else runCatching { Json.parseToJsonElement(f.readText()) }.getOrNull()
    }

    suspend fun write(json: JsonElement) = withContext(Dispatchers.IO) {
        val text = json.toString()
        val tmp = File(context.filesDir, "$name.tmp")
        val target = File(context.filesDir, name)
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }
}

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content?.takeIf { it.isNotEmpty() }

internal fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull

internal fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

internal fun JsonObject.date(key: String): LocalDate? = str(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

internal fun JsonObject.time(key: String): LocalTime? = str(key)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

enum class FormKind { TEXT, NUMBER, DATE, TIME, CHECK }

class FormField(val key: String, @param:StringRes val label: Int, val kind: FormKind, val initial: String = "")

/** Parsing of the form's text, shared by the editors. */
object FormValues {
    private val dateInput = DateTimeFormatter.ofPattern("d.M.yyyy")
    val dateOutput: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun date(text: String?): LocalDate? =
        text?.trim()?.takeIf { it.isNotEmpty() }?.replace('/', '.')?.replace('-', '.')
            ?.let { runCatching { LocalDate.parse(it, dateInput) }.getOrNull() }

    fun time(text: String?): LocalTime? =
        text?.trim()?.takeIf { it.isNotEmpty() }?.replace('.', ':')
            ?.let { runCatching { LocalTime.parse(it.padStart(5, '0')) }.getOrNull() }

    fun number(text: String?): Double? = text?.trim()?.replace(',', '.')?.toDoubleOrNull()

    fun text(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }

    fun format(value: Double?): String = value?.bodyNumber().orEmpty()
}

/**
 * Full-screen form. [validate] returns the error message to show, or null when the
 * values were accepted (and saved by the caller).
 */
@Composable
internal fun DiaryFormDialog(
    @StringRes title: Int,
    fields: List<FormField>,
    onDismiss: () -> Unit,
    validate: (Map<String, String>) -> Int?,
    onDelete: (() -> Unit)? = null,
) {
    val values = remember { mutableStateMapOf<String, String>().apply { fields.forEach { put(it.key, it.initial) } } }
    var error by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.body_cancel)) }
                    Text(
                        text = stringResource(title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Button(onClick = { error = validate(values.toMap()) }) { Text(stringResource(R.string.body_save)) }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                    fields.forEach { field ->
                        if (field.kind == FormKind.CHECK) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = values[field.key] == "true",
                                    onCheckedChange = { values[field.key] = it.toString() },
                                )
                                Text(stringResource(field.label))
                            }
                        } else {
                            OutlinedTextField(
                                value = values[field.key].orEmpty(),
                                onValueChange = { values[field.key] = it },
                                label = { Text(stringResource(field.label)) },
                                singleLine = field.kind != FormKind.TEXT,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = when (field.kind) {
                                        FormKind.NUMBER -> KeyboardType.Decimal
                                        FormKind.DATE, FormKind.TIME -> KeyboardType.Number
                                        else -> KeyboardType.Text
                                    },
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (onDelete != null) {
                        TextButton(
                            onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                        ) {
                            Text(
                                text = stringResource(if (confirmDelete) R.string.body_delete_confirm else R.string.diary_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

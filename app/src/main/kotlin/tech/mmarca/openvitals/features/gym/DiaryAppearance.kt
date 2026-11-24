package tech.mmarca.openvitals.features.gym

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tech.mmarca.openvitals.R

/** Diario Palestra: the "Aspetto" window: style, light or dark, and the accent of the blue style. */
@Composable
internal fun DiaryAppearanceDialog(
    style: DiaryStyle,
    mode: DiaryThemeMode,
    accent: DiaryAccent,
    onStyle: (DiaryStyle) -> Unit,
    onMode: (DiaryThemeMode) -> Unit,
    onAccent: (DiaryAccent) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appearance_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label(R.string.appearance_style)
                Chips(
                    listOf(
                        DiaryStyle.BLUE to R.string.diary_style_blue,
                        DiaryStyle.PASTEL to R.string.diary_style_pastel,
                        DiaryStyle.GLASS to R.string.diary_style_glass,
                    ),
                    style,
                    onStyle,
                )
                Label(R.string.appearance_mode)
                Chips(
                    listOf(
                        DiaryThemeMode.AUTO to R.string.diary_mode_auto,
                        DiaryThemeMode.LIGHT to R.string.diary_mode_light,
                        DiaryThemeMode.DARK to R.string.diary_mode_dark,
                    ),
                    mode,
                    onMode,
                )
                Label(R.string.appearance_accent)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DiaryAccent.entries.forEach { a ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(a.color)
                                .border(if (a == accent) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { onAccent(a) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (a == accent) Icon(Icons.Outlined.Check, contentDescription = stringResource(a.label), tint = Color.White)
                        }
                    }
                }
                if (style != DiaryStyle.BLUE) {
                    Text(
                        stringResource(R.string.appearance_accent_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_close)) } },
    )
}

@Composable
private fun Label(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun <T> Chips(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(stringResource(label)) })
        }
    }
}

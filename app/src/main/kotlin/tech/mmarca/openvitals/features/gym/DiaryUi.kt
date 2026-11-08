package tech.mmarca.openvitals.features.gym

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.mmarca.openvitals.ui.components.OpenVitalsCard

/**
 * Diario Palestra: the section title that splits each tab into clear parts, with the
 * section's own actions on the right ("+ New", a period switch).
 */
@Composable
internal fun DiarySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp).padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        action?.invoke(this)
    }
}

/** A few headline figures side by side in one card. */
@Composable
internal fun DiaryStatsCard(stats: List<Pair<String, String>>) {
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
            stats.forEach { (value, label) ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Two- or three-way switch shown in a section header. */
@Composable
internal fun <T> DiaryChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    options.forEach { (value, label) ->
        FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
    }
}

/** A short muted line, for empty states. */
@Composable
internal fun DiaryNote(text: String, modifier: Modifier = Modifier) {
    OpenVitalsCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

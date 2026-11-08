package tech.mmarca.openvitals.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Diario Palestra: section titles as monospaced, spaced capitals, as in the reference. */
val DiarySectionLabel = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    letterSpacing = 2.5.sp,
)

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = DiarySectionLabel,
        color = MaterialTheme.colorScheme.primary,
        // A heading, so a screen reader can jump from section to section.
        modifier = modifier
            .semantics { heading() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

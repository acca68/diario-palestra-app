package tech.mmarca.openvitals.features.gym

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SportsGymnastics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import tech.mmarca.openvitals.R

/** A tile of the pastel home: an outlined rounded square with an icon and its name under it. */
internal class DiaryTile(@param:StringRes val label: Int, val icon: ImageVector, val onClick: () -> Unit)

/** The sections of the diary, in the order of the tabs. */
internal val diarySections = listOf(
    Triple(R.string.diario_tab_gym, Icons.Outlined.FitnessCenter, DiaryTint.gym),
    Triple(R.string.diario_tab_body, Icons.Outlined.MonitorWeight, DiaryTint.body),
    Triple(R.string.diario_tab_trainer, Icons.Outlined.SportsGymnastics, DiaryTint.trainer),
    Triple(R.string.diario_tab_nutrition, Icons.Outlined.Restaurant, DiaryTint.nutrition),
    Triple(R.string.diario_tab_health, Icons.Outlined.HealthAndSafety, DiaryTint.health),
)

/**
 * Diario Palestra, pastel style: the home page. A greeting, the sections as tiles, the
 * diary's tools, and two soft cards with this month's sessions and the latest weight.
 */
@Composable
internal fun DiaryHome(
    sessionsThisMonth: Int,
    latestWeight: Double?,
    onOpen: (Int) -> Unit,
    tools: List<DiaryTile>,
) {
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> R.string.diary_home_morning
        in 12..17 -> R.string.diary_home_afternoon
        else -> R.string.diary_home_evening
    }
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault()))
    LazyColumn(
        modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(greeting), style = DiaryType.pastelGreeting, textAlign = TextAlign.Center)
                Text(
                    today.replaceFirstChar { it.titlecase(Locale.getDefault()) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { DiarySectionHeader(stringResource(R.string.diary_home_sections)) }
        item {
            TilePanel(diarySections.mapIndexed { index, (label, icon, _) -> DiaryTile(label, icon) { onOpen(index) } })
        }
        item { DiarySectionHeader(stringResource(R.string.diary_home_month)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftCard(
                    value = sessionsThisMonth.toString(),
                    label = stringResource(R.string.diary_home_sessions),
                    tint = DiaryTint.gym,
                    modifier = Modifier.weight(1f),
                ) { onOpen(0) }
                SoftCard(
                    value = latestWeight?.bodyNumber() ?: "—",
                    label = stringResource(R.string.diary_home_weight),
                    tint = DiaryTint.nutrition,
                    modifier = Modifier.weight(1f),
                ) { onOpen(1) }
            }
        }
        item { DiarySectionHeader(stringResource(R.string.diary_home_tools)) }
        item { TilePanel(tools) }
    }
}

@Composable
private fun TilePanel(tiles: List<DiaryTile>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 16.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        tiles.chunked(4).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { tile -> Tile(tile, Modifier.weight(1f)) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Tile(tile: DiaryTile, modifier: Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = tile.onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tile.icon, contentDescription = null, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(tile.label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun SoftCard(value: String, label: String, tint: DiaryTint, modifier: Modifier, onClick: () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colors = if (dark) listOf(tint.secondDark, tint.firstDark) else listOf(Color.White.copy(alpha = 0.7f), tint.first)
    Column(
        modifier = modifier
            .height(112.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(Brush.linearGradient(colors))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(value, style = DiaryType.pastelFigure.copy(fontSize = DiaryType.pastelTitle.fontSize))
        Text("$label  ↗", style = MaterialTheme.typography.titleMedium)
    }
}

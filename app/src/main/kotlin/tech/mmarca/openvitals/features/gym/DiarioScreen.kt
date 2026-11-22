package tech.mmarca.openvitals.features.gym

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import tech.mmarca.openvitals.R

/**
 * Diario Palestra: the personal diary, one part each for gym, body, trainer, nutritionist and
 * health. In the blue style the parts are tabs; in the pastel style a home page of tiles
 * opens each part on its own page.
 */
@Composable
fun DiarioScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Pastel style: the open part, or -1 for the home page.
    var section by rememberSaveable { mutableIntStateOf(-1) }
    var showBackup by rememberSaveable { mutableStateOf(false) }
    val gym = hiltViewModel<GymViewModel>()
    val body = hiltViewModel<BodyViewModel>()
    val trainer = hiltViewModel<TrainerViewModel>()
    val nutrition = hiltViewModel<NutritionViewModel>()
    val health = hiltViewModel<HealthViewModel>()
    if (showBackup) {
        DiaryBackupDialog(
            onDismiss = { showBackup = false },
            onRestored = {
                gym.refresh()
                body.reload()
                trainer.reload()
                nutrition.reload()
                health.reload()
            },
        )
    }
    val context = LocalContext.current
    var themeMode by remember { mutableStateOf(DiaryThemePrefs.load(context)) }
    var style by remember { mutableStateOf(DiaryThemePrefs.loadStyle(context)) }
    val pastel = style == DiaryStyle.PASTEL
    val shown = if (pastel) section else tab
    LaunchedEffect(shown) {
        when (shown) {
            0 -> gym.reloadTrainer()
            2 -> trainer.reload()
        }
    }
    BackHandler(enabled = pastel && section >= 0) { section = -1 }

    val cycleTheme = {
        themeMode = DiaryThemeMode.entries[(themeMode.ordinal + 1) % DiaryThemeMode.entries.size]
        DiaryThemePrefs.save(context, themeMode)
    }
    val switchStyle = {
        style = if (pastel) DiaryStyle.BLUE else DiaryStyle.PASTEL
        DiaryThemePrefs.saveStyle(context, style)
        if (style == DiaryStyle.PASTEL) section = -1 else if (section >= 0) tab = section
    }
    val themeIcon = when (themeMode) {
        DiaryThemeMode.AUTO -> Icons.Outlined.BrightnessAuto
        DiaryThemeMode.LIGHT -> Icons.Outlined.LightMode
        DiaryThemeMode.DARK -> Icons.Outlined.DarkMode
    }

    @Composable
    fun part(index: Int) {
        when (index) {
            0 -> GymScreen(viewModel = gym)
            1 -> BodyTab(viewModel = body)
            2 -> TrainerTab(viewModel = trainer)
            3 -> NutritionTab(viewModel = nutrition)
            else -> HealthTab(viewModel = health)
        }
    }

    val tint = if (pastel && section >= 0) diarySections[section].third else DiaryTint.home
    DiaryTheme(themeMode, style, tint) {
        if (pastel) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                if (section < 0) {
                    val gymState by gym.uiState.collectAsStateWithLifecycle()
                    val bodyState by body.uiState.collectAsStateWithLifecycle()
                    DiaryHome(
                        sessionsThisMonth = gymState.stats.sessionsThisMonth,
                        latestWeight = bodyState.latest?.get(BodyField.WEIGHT),
                        onOpen = { section = it },
                        tools = listOf(
                            DiaryTile(R.string.diary_tile_backup, Icons.Outlined.SaveAlt) { showBackup = true },
                            DiaryTile(
                                when (themeMode) {
                                    DiaryThemeMode.AUTO -> R.string.diary_mode_auto
                                    DiaryThemeMode.LIGHT -> R.string.diary_mode_light
                                    DiaryThemeMode.DARK -> R.string.diary_mode_dark
                                },
                                themeIcon,
                            ) { cycleTheme() },
                            DiaryTile(R.string.diary_style_blue, Icons.Outlined.Palette) { switchStyle() },
                        ),
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                            IconButton(onClick = { section = -1 }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.diary_home_back))
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { part(section) }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = tab,
                        modifier = Modifier.weight(1f),
                        edgePadding = 8.dp,
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ) {
                        diarySections.forEachIndexed { index, (title, _, _) ->
                            Tab(
                                selected = tab == index,
                                onClick = { tab = index },
                                text = { Text(stringResource(title)) },
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = switchStyle) {
                        Icon(Icons.Outlined.Palette, contentDescription = stringResource(R.string.diary_style_pastel))
                    }
                    IconButton(onClick = cycleTheme) {
                        Icon(imageVector = themeIcon, contentDescription = stringResource(R.string.diary_theme))
                    }
                    IconButton(onClick = { showBackup = true }) {
                        Icon(Icons.Outlined.SaveAlt, contentDescription = stringResource(R.string.backup_title))
                    }
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { part(tab) }
            }
        }
    }
}

@Composable
private fun DiaryBackupDialog(onDismiss: () -> Unit, onRestored: () -> Unit) {
    val viewModel = hiltViewModel<DiaryBackupViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val restorer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.restore(it, onRestored) }
    }
    val messageText = state.message?.let { message ->
        when (message) {
            BackupMessage.Exported -> stringResource(R.string.backup_exported)
            is BackupMessage.Restored -> stringResource(R.string.backup_restored)
            BackupMessage.NotBackup -> stringResource(R.string.backup_not_backup)
            BackupMessage.Failed -> stringResource(R.string.backup_failed)
        }
    }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            Toast.makeText(context, messageText, Toast.LENGTH_LONG).show()
            viewModel.dismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.backup_body), style = MaterialTheme.typography.bodyMedium)
                if (state.busy) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = { exporter.launch("diario-palestra-${LocalDate.now()}.json") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.backup_export)) }
                    OutlinedButton(
                        onClick = {
                            if (confirmRestore) {
                                confirmRestore = false
                                restorer.launch(arrayOf("*/*"))
                            } else {
                                confirmRestore = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(if (confirmRestore) R.string.backup_restore_confirm else R.string.backup_restore))
                    }
                    if (confirmRestore) {
                        Text(
                            stringResource(R.string.backup_restore_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_close)) } },
    )
}

package tech.mmarca.openvitals.features.gym

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
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

/** Diario Palestra: the personal diary, one tab per part: gym, body, trainer, nutritionist. */
@Composable
fun DiarioScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showBackup by rememberSaveable { mutableStateOf(false) }
    val gym = hiltViewModel<GymViewModel>()
    val body = hiltViewModel<BodyViewModel>()
    val trainer = hiltViewModel<TrainerViewModel>()
    val nutrition = hiltViewModel<NutritionViewModel>()
    val tabs = listOf(
        R.string.diario_tab_gym,
        R.string.diario_tab_body,
        R.string.diario_tab_trainer,
        R.string.diario_tab_nutrition,
    )
    if (showBackup) {
        DiaryBackupDialog(
            onDismiss = { showBackup = false },
            onRestored = {
                gym.refresh()
                body.reload()
                trainer.reload()
                nutrition.reload()
            },
        )
    }
    LaunchedEffect(tab) {
        when (tab) {
            0 -> gym.reloadTrainer()
            2 -> trainer.reload()
        }
    }
    val context = LocalContext.current
    var themeMode by remember { mutableStateOf(DiaryThemePrefs.load(context)) }
    DiaryTheme(themeMode) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryScrollableTabRow(
                selectedTabIndex = tab,
                modifier = Modifier.weight(1f),
                edgePadding = 8.dp,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(title)) })
                }
            }
            IconButton(
                onClick = {
                    themeMode = DiaryThemeMode.entries[(themeMode.ordinal + 1) % DiaryThemeMode.entries.size]
                    DiaryThemePrefs.save(context, themeMode)
                },
            ) {
                Icon(
                    imageVector = when (themeMode) {
                        DiaryThemeMode.AUTO -> Icons.Outlined.BrightnessAuto
                        DiaryThemeMode.LIGHT -> Icons.Outlined.LightMode
                        DiaryThemeMode.DARK -> Icons.Outlined.DarkMode
                    },
                    contentDescription = stringResource(R.string.diary_theme),
                )
            }
            IconButton(onClick = { showBackup = true }) {
                Icon(Icons.Outlined.SaveAlt, contentDescription = stringResource(R.string.backup_title))
            }
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (tab) {
                0 -> GymScreen(viewModel = gym)
                1 -> BodyTab(viewModel = body)
                2 -> TrainerTab(viewModel = trainer)
                else -> NutritionTab(viewModel = nutrition)
            }
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

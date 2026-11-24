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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Palette
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
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.mmarca.openvitals.R

/**
 * Diario Palestra: the personal diary, one part each for gym, body, trainer, nutritionist and
 * health. In the blue style the parts are tabs; in the pastel style a home page of tiles
 * opens each part on its own page.
 */
@Composable
fun DiarioScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Soft styles: the open part, -1 for the home page, CONNECTIONS for "Collegamenti".
    var section by rememberSaveable { mutableIntStateOf(-1) }
    // Blue style: "Collegamenti" shown in place of the tabs.
    var blueConnections by rememberSaveable { mutableStateOf(false) }
    var showBackup by rememberSaveable { mutableStateOf(false) }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    val gym = hiltViewModel<GymViewModel>()
    val body = hiltViewModel<BodyViewModel>()
    val trainer = hiltViewModel<TrainerViewModel>()
    val nutrition = hiltViewModel<NutritionViewModel>()
    val health = hiltViewModel<HealthViewModel>()
    val connections = hiltViewModel<ConnectionsViewModel>()
    val gymState by gym.uiState.collectAsStateWithLifecycle()
    val bodyState by body.uiState.collectAsStateWithLifecycle()
    val trainerState by trainer.uiState.collectAsStateWithLifecycle()
    val nutritionState by nutrition.uiState.collectAsStateWithLifecycle()
    val healthState by health.uiState.collectAsStateWithLifecycle()
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
    val scope = rememberCoroutineScope()
    var themeMode by remember { mutableStateOf(DiaryThemePrefs.load(context)) }
    var style by remember { mutableStateOf(DiaryThemePrefs.loadStyle(context)) }
    var accent by remember { mutableStateOf(DiaryThemePrefs.loadAccent(context)) }
    val soft = style.soft
    val shown = if (soft) section else tab
    LaunchedEffect(shown) {
        when (shown) {
            0 -> gym.reloadTrainer()
            2 -> trainer.reload()
        }
    }
    BackHandler(enabled = soft && section >= 0) { section = -1 }
    BackHandler(enabled = !soft && blueConnections) { blueConnections = false }

    // Files written from "Collegamenti": the month's PDF report and the sessions as CSV.
    fun writeFile(uri: android.net.Uri, write: (java.io.OutputStream) -> Unit) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use(write) ?: error("no stream") }.isSuccess
            }
            Toast.makeText(context, context.getString(if (ok) R.string.report_saved else R.string.report_failed), Toast.LENGTH_LONG).show()
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let {
            val data = ReportData(gymState.sessions, bodyState.reports, trainerState.data, nutritionState.visits, healthState.data)
            writeFile(it) { out -> DiaryReport.pdf(context, out, data) }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { writeFile(it) { out -> DiaryReport.csv(out, gymState.sessions) } }
    }
    val hevyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(gym::importHevyCsv) }
    val bodyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(body::import) }
    val actions = ConnectionActions(
        importHevy = { hevyPicker.launch(arrayOf("*/*")) },
        importBody = { bodyPicker.launch(arrayOf("*/*")) },
        backup = { showBackup = true },
        exportCsv = { csvLauncher.launch("diario-palestra-sedute-${LocalDate.now()}.csv") },
        exportPdf = { pdfLauncher.launch("diario-palestra-report-${java.time.YearMonth.now()}.pdf") },
    )

    if (showAppearance) {
        DiaryAppearanceDialog(
            style = style,
            mode = themeMode,
            accent = accent,
            onStyle = { new ->
                if (new != style) {
                    if (new.soft && !style.soft) section = -1
                    if (!new.soft && section in 0 until CONNECTIONS) tab = section
                    style = new
                    DiaryThemePrefs.saveStyle(context, new)
                }
            },
            onMode = { themeMode = it; DiaryThemePrefs.save(context, it) },
            onAccent = { accent = it; DiaryThemePrefs.saveAccent(context, it) },
            onDismiss = { showAppearance = false },
        )
    }

    @Composable
    fun part(index: Int) {
        when (index) {
            0 -> GymScreen(viewModel = gym)
            1 -> BodyTab(viewModel = body)
            2 -> TrainerTab(viewModel = trainer)
            3 -> NutritionTab(viewModel = nutrition)
            4 -> HealthTab(viewModel = health)
            else -> ConnectionsScreen(viewModel = connections, actions = actions)
        }
    }

    @Composable
    fun backTo(label: Int, onClick: () -> Unit) {
        // One clear way back (the bar above leaves the diary).
        TextButton(onClick = onClick, modifier = Modifier.padding(start = 8.dp)) {
            Icon(Icons.Outlined.GridView, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(label))
        }
    }

    val tint = if (soft && section in diarySections.indices) diarySections[section].third else DiaryTint.home
    DiaryTheme(themeMode, style, tint, accent) {
        if (soft) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                if (section < 0) {
                    DiaryHome(
                        sessionsThisMonth = gymState.stats.sessionsThisMonth,
                        latestWeight = bodyState.latest?.get(BodyField.WEIGHT),
                        onOpen = { section = it },
                        tools = listOf(
                            DiaryTile(R.string.connections_title, Icons.Outlined.Hub) {
                                connections.reload()
                                section = CONNECTIONS
                            },
                            DiaryTile(R.string.diary_tile_report, Icons.Outlined.PictureAsPdf) { actions.exportPdf() },
                            DiaryTile(R.string.diary_tile_backup, Icons.Outlined.SaveAlt) { showBackup = true },
                            DiaryTile(R.string.appearance_title, Icons.Outlined.Palette) { showAppearance = true },
                        ),
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        backTo(R.string.diary_home_back) { section = -1 }
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { part(section) }
                    }
                }
            }
        } else if (blueConnections) {
            Column(modifier = Modifier.fillMaxSize()) {
                backTo(R.string.diary_home_back) { blueConnections = false }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { part(CONNECTIONS) }
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
                    IconButton(onClick = { showAppearance = true }) {
                        Icon(Icons.Outlined.Palette, contentDescription = stringResource(R.string.appearance_title))
                    }
                    IconButton(onClick = {
                        connections.reload()
                        blueConnections = true
                    }) {
                        Icon(Icons.Outlined.Hub, contentDescription = stringResource(R.string.connections_title))
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

/** The index of the "Collegamenti" page among the diary's parts. */
private const val CONNECTIONS = 5

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

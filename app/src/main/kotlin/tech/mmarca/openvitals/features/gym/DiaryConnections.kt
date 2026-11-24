package tech.mmarca.openvitals.features.gym

import android.content.Context
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
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.mmarca.openvitals.R
import tech.mmarca.openvitals.data.repository.contract.ActivityRepository
import tech.mmarca.openvitals.healthconnect.openHealthConnectPermissionSettings
import tech.mmarca.openvitals.ui.components.OpenVitalsCard

/**
 * Diario Palestra: which apps' workouts the diary uses and how. A gym app (Hevy) gives the
 * session and its sets; an ignored app (MyFitnessPal re-writing other apps' sessions) is
 * left out; any other app is a watch whose data completes or adds sessions. Matching is
 * by package name containing the key.
 */
@Immutable
data class SourceRules(
    val gymApps: Set<String> = setOf("hevy"),
    val ignored: Set<String> = setOf("myfitnesspal"),
) {
    fun isGymApp(source: String): Boolean = gymApps.any { source.contains(it, ignoreCase = true) }

    fun isIgnored(source: String): Boolean = ignored.any { source.contains(it, ignoreCase = true) }
}

internal object SourceRulesPrefs {
    private const val FILE = "diario_palestra_prefs"
    private const val GYM = "gym_apps"
    private const val IGNORED = "ignored_apps"

    fun load(context: Context): SourceRules {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val defaults = SourceRules()
        return SourceRules(
            gymApps = prefs.getStringSet(GYM, null)?.toSet() ?: defaults.gymApps,
            ignored = prefs.getStringSet(IGNORED, null)?.toSet() ?: defaults.ignored,
        )
    }

    fun save(context: Context, rules: SourceRules) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putStringSet(GYM, rules.gymApps)
            .putStringSet(IGNORED, rules.ignored)
            .apply()
    }
}

/** An app that wrote workouts to Health Connect in the last weeks. */
@Immutable
data class WorkoutSource(val packageName: String, val name: String, val count: Int, val last: LocalDate)

@Immutable
data class ConnectionsUiState(
    val loading: Boolean = true,
    val connected: Boolean = false,
    val sources: List<WorkoutSource> = emptyList(),
    val rules: SourceRules = SourceRules(),
)

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConnectionsUiState(rules = SourceRulesPrefs.load(context)))
    val uiState: StateFlow<ConnectionsUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val result = runCatching { activityRepository.loadWorkoutsWithMetrics(today.minusDays(DAYS), today) }
            val sources = result.getOrDefault(emptyList())
                .groupBy { it.source }
                .map { (pkg, list) ->
                    WorkoutSource(
                        packageName = pkg,
                        name = appName(pkg),
                        count = list.size,
                        last = list.maxOf { it.startTime }.atZone(ZoneId.systemDefault()).toLocalDate(),
                    )
                }
                .sortedByDescending { it.count }
            _uiState.update { it.copy(loading = false, connected = result.isSuccess, sources = sources) }
        }
    }

    /** Marks [pkg] as a gym app (like Hevy), as ignored, or neither. */
    fun setRole(pkg: String, gym: Boolean, ignored: Boolean) {
        val r = _uiState.value.rules
        val rules = SourceRules(
            gymApps = (r.gymApps.filterNot { pkg.contains(it, ignoreCase = true) } + listOfNotNull(pkg.takeIf { gym })).toSet(),
            ignored = (r.ignored.filterNot { pkg.contains(it, ignoreCase = true) } + listOfNotNull(pkg.takeIf { ignored })).toSet(),
        )
        SourceRulesPrefs.save(context, rules)
        _uiState.update { it.copy(rules = rules) }
    }

    private fun appName(pkg: String): String {
        KNOWN.entries.firstOrNull { pkg.contains(it.key, ignoreCase = true) }?.let { return it.value }
        return runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    companion object {
        const val DAYS = 90L
        private val KNOWN = mapOf(
            "garmin" to "Garmin Connect",
            "hevy" to "Hevy",
            "myfitnesspal" to "MyFitnessPal",
            "samsung" to "Samsung Health",
            "google.android.apps.fitness" to "Google Fit",
            "strava" to "Strava",
            "opengym" to "openGym",
            "fitactive" to "FitActive",
            "egym" to "EGYM",
            "tech.mmarca.openvitals" to "Diario Palestra",
        )
    }
}

/** What the page can start on the other parts of the diary. */
internal class ConnectionActions(
    val importHevy: () -> Unit,
    val importBody: () -> Unit,
    val backup: () -> Unit,
    val exportCsv: () -> Unit,
    val exportPdf: () -> Unit,
)

private val lastDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** Diario Palestra: the "Collegamenti" page. */
@Composable
internal fun ConnectionsScreen(viewModel: ConnectionsViewModel, actions: ConnectionActions) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.connections_title), style = DiaryType.pastelTitle)
            Text(
                stringResource(R.string.connections_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { DiarySectionHeader(stringResource(R.string.connections_health_connect)) }
        item {
            OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(R.string.connections_hc_name), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(if (state.connected) R.string.connections_hc_ok else R.string.connections_hc_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { openHealthConnectPermissionSettings(context) }) { Text(stringResource(R.string.connections_hc_open)) }
                }
            }
        }
        item { DiarySectionHeader(stringResource(R.string.connections_sources, ConnectionsViewModel.DAYS)) }
        if (!state.loading && state.sources.isEmpty()) {
            item { DiaryNote(stringResource(R.string.connections_sources_none)) }
        }
        items(state.sources, key = { it.packageName }) { source ->
            val gym = state.rules.isGymApp(source.packageName)
            val ignored = state.rules.isIgnored(source.packageName)
            OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.connections_source_count, source.count, source.last.format(lastDate)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        stringResource(
                            when {
                                ignored -> R.string.connections_role_ignored
                                gym -> R.string.connections_role_gym
                                else -> R.string.connections_role_watch
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Toggle(R.string.connections_rule_gym, gym && !ignored, enabled = !ignored) {
                        viewModel.setRole(source.packageName, gym = it, ignored = false)
                    }
                    Toggle(R.string.connections_rule_ignore, ignored, enabled = true) {
                        viewModel.setRole(source.packageName, gym = false, ignored = it)
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.connections_rules_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { DiarySectionHeader(stringResource(R.string.connections_import)) }
        item {
            ActionCard(
                listOf(
                    Triple(Icons.Outlined.FileUpload, R.string.connections_import_hevy, actions.importHevy),
                    Triple(Icons.Outlined.FileUpload, R.string.connections_import_body, actions.importBody),
                    Triple(Icons.Outlined.SaveAlt, R.string.connections_backup, actions.backup),
                ),
            )
        }
        item { DiarySectionHeader(stringResource(R.string.connections_export)) }
        item {
            ActionCard(
                listOf(
                    Triple(Icons.Outlined.PictureAsPdf, R.string.connections_export_pdf, actions.exportPdf),
                    Triple(Icons.Outlined.TableChart, R.string.connections_export_csv, actions.exportCsv),
                    Triple(Icons.Outlined.FileDownload, R.string.connections_backup, actions.backup),
                ),
            )
        }
    }
}

@Composable
private fun Toggle(label: Int, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun ActionCard(actions: List<Triple<ImageVector, Int, () -> Unit>>) {
    OpenVitalsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            actions.forEach { (icon, label, onClick) ->
                TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(label), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

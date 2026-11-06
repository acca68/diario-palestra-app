package tech.mmarca.openvitals.features.gym

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import tech.mmarca.openvitals.R

/** Diario Palestra: the personal diary, one tab per part: gym, body, trainer, nutritionist. */
@Composable
fun DiarioScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.diario_tab_gym, R.string.diario_tab_body, R.string.diario_tab_trainer, R.string.diario_tab_nutrition)
    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = tab, modifier = Modifier.fillMaxWidth(), edgePadding = 8.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(title)) })
            }
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (tab) {
                0 -> GymScreen(viewModel = hiltViewModel<GymViewModel>())
                1 -> BodyTab(viewModel = hiltViewModel<BodyViewModel>())
                2 -> TrainerTab(viewModel = hiltViewModel<TrainerViewModel>())
                else -> NutritionTab(viewModel = hiltViewModel<NutritionViewModel>())
            }
        }
    }
}

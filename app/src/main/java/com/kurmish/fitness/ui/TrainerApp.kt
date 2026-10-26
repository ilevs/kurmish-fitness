package com.kurmish.fitness.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kurmish.fitness.R
import com.kurmish.fitness.ui.voice.rememberSpeaker

/** Вкладки нижней панели. */
private enum class Tab(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Today(R.string.tab_today, R.drawable.ic_today),
    Log(R.string.tab_log, R.drawable.ic_add),
    Journal(R.string.tab_journal, R.drawable.ic_list),
    Profile(R.string.tab_profile, R.drawable.ic_person),
}

/** Корневой экран: нижняя панель и четыре вкладки. */
@Composable
fun TrainerApp(
    modifier: Modifier = Modifier,
    viewModel: TrainerViewModel = viewModel(
        factory = TrainerViewModel.Factory
    )
) {
    val data by viewModel.data.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    val speaker = rememberSpeaker()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { tab = item },
                        icon = { Icon(painterResource(item.icon), contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        when (tab) {
            Tab.Today -> TodayScreen(
                data = data,
                today = viewModel.today(),
                onSpeak = speaker::speak,
                modifier = content
            )
            Tab.Log -> LogScreen(
                onSave = { text, parsed ->
                    viewModel.addWorkout(text, parsed)
                    speaker.speak(parsed.summary())
                },
                modifier = content,
            )
            Tab.Journal -> JournalScreen(
                data = data,
                today = viewModel.today(),
                syncStatus = syncStatus,
                onDelete = viewModel::deleteWorkout,
                onTelegramConnect = viewModel::connectTelegram,
                onTelegramDisconnect = viewModel::disconnectTelegram,
                onSync = viewModel::requestSync,
                modifier = content,
            )
            Tab.Profile -> ProfileScreen(
                data = data,
                today = viewModel.today(),
                onProfileChange = viewModel::updateProfile,
                modifier = content,
            )
        }
    }
}

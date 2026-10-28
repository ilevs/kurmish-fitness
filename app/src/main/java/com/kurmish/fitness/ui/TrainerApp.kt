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
import com.kurmish.fitness.backup.BackupViewModel
import com.kurmish.fitness.backup.DriveBackupViewModel
import com.kurmish.fitness.backup.FileBackupViewModel
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.Profile
import com.kurmish.fitness.ui.voice.Speaker
import com.kurmish.fitness.ui.voice.rememberSpeaker

/** Вкладки нижней панели. */
private enum class Tab(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Journal(R.string.tab_journal, R.drawable.ic_list),
    Stats(R.string.tab_stats, R.drawable.ic_chart),
    Profile(R.string.tab_profile, R.drawable.ic_person),
}

/** Корневой экран: нижняя панель и вкладки. */
@Composable
fun TrainerApp(
    modifier: Modifier = Modifier,
    viewModel: TrainerViewModel = viewModel(factory = TrainerViewModel.Factory),
    backupViewModel: BackupViewModel = viewModel(),
    driveViewModel: DriveBackupViewModel = viewModel(),
    fileViewModel: FileBackupViewModel = viewModel(),
) {
    val data by viewModel.data.collectAsState()
    val backup by backupViewModel.ui.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.Journal) }
    var testing by rememberSaveable { mutableStateOf(false) }
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
            Tab.Journal -> JournalScreen(
                data = data,
                today = viewModel.today,
                actions = viewModel.journalActions(data, speaker),
                modifier = content,
            )
            Tab.Stats -> StatsScreen(data = data, today = viewModel.today, modifier = content)
            Tab.Profile -> ProfileScreen(
                data = data,
                today = viewModel.today,
                backup = backup,
                onProfileChange = viewModel::updateProfile,
                yandex = backupViewModel.actions(viewModel::restore),
                files = fileBackups(driveViewModel, fileViewModel, viewModel::restore),
                onTest = { testing = true },
                onWeight = viewModel::logWeight,
                modifier = content,
            )
        }
    }
    if (testing || needsTest(data, loaded)) {
        FirstTest(data, viewModel.today, viewModel::updateProfile) { testing = false }
    }
}

@Composable
private fun FirstTest(
    data: AppData,
    today: Long,
    onProfile: (Profile) -> Unit,
    onClose: () -> Unit,
) {
    FirstTestDialog(
        profile = data.profile,
        today = today,
        onFinish = { profile ->
            onProfile(profile)
            onClose()
        },
        onSkip = {
            onProfile(data.profile.copy(onboarded = true))
            onClose()
        },
    )
}

/** Анкета открывается сама при первом запуске: Дневник прочитан, пуст, а тестирование не пройдено. */
private fun needsTest(data: AppData, loaded: Boolean): Boolean =
    loaded && !data.profile.onboarded && data.sessions.isEmpty()

@Composable
private fun fileBackups(
    drive: DriveBackupViewModel,
    file: FileBackupViewModel,
    onRestore: (AppData) -> Unit,
): FileBackups {
    val driveState by drive.ui.collectAsState()
    val status by file.status.collectAsState()
    return FileBackups(driveState, drive.actions(onRestore), status, file.actions(onRestore))
}

private fun DriveBackupViewModel.actions(onRestored: (AppData) -> Unit) = DriveActions(
    onCreate = ::create,
    onRestoreFrom = { restoreFrom(it, onRestored) },
    onBackup = ::backupNow,
    onRestore = { restore(onRestored) },
    onUnlink = ::unlink,
)

private fun BackupViewModel.actions(onRestored: (AppData) -> Unit) = YandexActions(
    onConnect = ::connect,
    onBackup = ::backupNow,
    onRestore = { restore(onRestored) },
    onDisconnect = ::disconnect,
)

private fun FileBackupViewModel.actions(onRestored: (AppData) -> Unit) = FileActions(
    onExport = ::exportTo,
    onImport = { importFrom(it, onRestored) },
    onShare = ::prepareShare,
)

private fun TrainerViewModel.journalActions(data: AppData, speaker: Speaker) = JournalActions(
    onRecord = { text, parsed ->
        recordWorkout(text, parsed)
        speaker.speak(parsed.summary())
    },
    onSave = ::saveWorkout,
    onDelete = ::deleteWorkout,
    onSpeak = speaker::speak,
    onChoose = { slot, exercise -> updateProfile(data.profile.choose(slot, exercise)) },
    onTest = { on -> updateProfile(data.profile.copy(testEpochDay = today.takeIf { on })) },
)

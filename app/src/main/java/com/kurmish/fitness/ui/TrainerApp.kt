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
import com.kurmish.fitness.core.toSession
import com.kurmish.fitness.ui.voice.rememberSpeaker

/** Вкладки нижней панели. */
private enum class Tab(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Journal(R.string.tab_journal, R.drawable.ic_list),
    Cycles(R.string.tab_cycles, R.drawable.ic_today),
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
    var tab by rememberSaveable { mutableStateOf(Tab.Journal) }
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
                today = viewModel.today(),
                actions = JournalActions(
                    onRecord = { text, parsed ->
                        viewModel.saveWorkout(parsed.toSession(text, viewModel.today()))
                        speaker.speak(parsed.summary())
                    },
                    onSave = viewModel::saveWorkout,
                    onDelete = viewModel::deleteWorkout,
                    onSpeak = speaker::speak,
                    onChoose = { slot, exercise -> viewModel.updateProfile(data.profile.choose(slot, exercise)) },
                    diet = viewModel.dietActions(),
                ),
                modifier = content,
            )
            Tab.Cycles -> CyclesScreen(data, viewModel.today(), viewModel::updateProfile, modifier = content)
            Tab.Stats -> StatsScreen(data = data, today = viewModel.today(), modifier = content)
            Tab.Profile -> ProfileScreen(
                data = data,
                today = viewModel.today(),
                backup = backup,
                onProfileChange = viewModel::updateProfile,
                onBackupConnect = backupViewModel::connect,
                onBackupNow = backupViewModel::backupNow,
                onRestore = { backupViewModel.restore(viewModel::restore) },
                onBackupDisconnect = backupViewModel::disconnect,
                files = fileBackups(driveViewModel, fileViewModel, viewModel::restore),
                modifier = content,
            )
        }
    }
}

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

private fun FileBackupViewModel.actions(onRestored: (AppData) -> Unit) = FileActions(
    onExport = ::exportTo,
    onImport = { importFrom(it, onRestored) },
    onShare = ::prepareShare,
)

private fun TrainerViewModel.dietActions() = DietActions(
    onLogWeight = ::logWeight,
    onRefeedDone = ::markRefeed,
    onApplyCorrection = ::applyCalorieCorrection,
    onPhaseChange = ::changeDietPhase,
)

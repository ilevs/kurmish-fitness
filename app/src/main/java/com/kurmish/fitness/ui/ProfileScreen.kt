package com.kurmish.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.kurmish.fitness.backup.BackupUi
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.Profile

/** Экраны, которые открываются из Профиля. */
private enum class ProfilePage { Main, Cycles, Backup }

/** Профиль атлета: данные, ПМ и питание. Циклы и резервная копия открываются отдельными экранами. */
@Composable
internal fun ProfileScreen(
    data: AppData,
    today: Long,
    backup: BackupUi,
    onProfileChange: (Profile) -> Unit,
    yandex: YandexActions,
    files: FileBackups,
    onTest: () -> Unit,
    onWeight: (Double) -> Unit,
    diet: DietActions,
    modifier: Modifier = Modifier
) {
    var page by rememberSaveable { mutableStateOf(ProfilePage.Main) }
    val back = { page = ProfilePage.Main }
    when (page) {
        ProfilePage.Cycles -> CyclesScreen(data, today, onProfileChange, onBack = back, modifier = modifier)
        ProfilePage.Backup -> BackupScreen(backup, yandex, files, onBack = back, modifier = modifier)
        ProfilePage.Main -> ProfileContent(
            data = data,
            today = today,
            onProfileChange = onProfileChange,
            onTest = onTest,
            onWeight = onWeight,
            diet = diet,
            onOpen = { page = it },
            modifier = modifier,
        )
    }
}

/**
 * Профиль сверху вниз по частоте: вес с данными тела и питание на сегодня трогают почти каждый день, циклы и ПМ —
 * раз в неделю или месяц, а цель, настройки питания, тесты и резервную копию — редко.
 */
@Composable
private fun ProfileContent(
    data: AppData,
    today: Long,
    onProfileChange: (Profile) -> Unit,
    onTest: () -> Unit,
    onWeight: (Double) -> Unit,
    diet: DietActions,
    onOpen: (ProfilePage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        BodyCard(profile = data.profile, onWeight = onWeight, onProfileChange = onProfileChange)
        TodayDiet(data = data, today = today, diet = diet)
        CyclesCard(data = data, today = today, onOpen = { onOpen(ProfilePage.Cycles) })
        StrengthCard(data = data, today = today, onProfileChange = onProfileChange)
        GoalCard(profile = data.profile, onProfileChange = onProfileChange)
        DietSettingsCard(profile = data.profile, today = today, onProfileChange = onProfileChange)
        TestCard(data = data, today = today, onAgain = onTest)
        BackupEntryCard(onOpen = { onOpen(ProfilePage.Backup) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        // Ряд переносится на новую строку, чтобы выбранный вариант не уезжал за край экрана.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            options.forEach { (value, label) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
            }
        }
    }
}

@Composable
internal fun NumberField(label: String, value: Double?, onValueChange: (Double?) -> Unit, placeholder: String? = null) {
    var text by rememberSaveable { mutableStateOf(value?.plain().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            val number = input.replace(',', '.').toDoubleOrNull()
            if (input.isBlank() || number != null) onValueChange(number)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { hint -> { Text(hint) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

private fun Double.plain(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

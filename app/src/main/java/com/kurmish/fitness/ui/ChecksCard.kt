package com.kurmish.fitness.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.kurmish.fitness.R
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.Checks
import com.kurmish.fitness.core.Stats

/** Числовые проверки Дневника: перекосы, которых не видно на весах баланса. */
@Composable
internal fun ChecksCard(data: AppData, today: Long) {
    val enough = remember(data, today) { Stats.enoughForBalance(data, today) }
    val lines = remember(data, today) { Checks.lines(data, today) }
    Section(stringResource(R.string.stats_checks_title)) {
        when {
            !enough -> Hint(stringResource(R.string.stats_checks_early, Stats.BALANCE_MIN_SESSIONS))
            lines.isEmpty() -> Hint(stringResource(R.string.stats_checks_ok))
            else -> lines.forEach { Text(text = "• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

package com.kurmish.fitness.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.kurmish.fitness.R
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.LiftZones
import com.kurmish.fitness.core.ZoneVerdict

/** КПШ по зонам интенсивности в последней тренировке против коридоров; ничего, если считать не по чему. */
@Composable
internal fun ZonesCard(data: AppData, today: Long) {
    val last = remember(data, today) { LiftZones.lastSession(data, today) } ?: return
    Section(stringResource(R.string.stats_zones_title, last.first.monthDay())) {
        last.second.forEach { count ->
            Text(
                text = count.line,
                style = MaterialTheme.typography.bodySmall,
                color = if (count.verdict == ZoneVerdict.Inside) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        Hint(stringResource(R.string.stats_zones_hint))
    }
}

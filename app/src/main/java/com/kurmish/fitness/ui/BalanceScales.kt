package com.kurmish.fitness.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurmish.fitness.R
import com.kurmish.fitness.core.OppositePair
import kotlin.math.abs

private val ScaleHeight = 16.dp
private val MarkerWidth = 4.dp
private const val ZoneAlpha = 0.35f
private const val BarShare = 0.5f

/**
 * Весы противоположностей: у каждой пары полоса от центра в сторону типа, где повторов больше. Цветная зона —
 * норма по книге: левый тип больше правого не дальше чем в 1,5 раза, правый больше левого до двух раз.
 */
@Composable
internal fun BalanceScales(pairs: List<OppositePair>) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Hint(stringResource(R.string.stats_scales_hint))
        pairs.forEach { PairScale(it) }
    }
}

@Composable
private fun PairScale(pair: OppositePair) {
    val left = stringResource(R.string.stats_scale_side, pair.left.title, pair.leftReps)
    val right = stringResource(R.string.stats_scale_side, pair.right.title, pair.rightReps)
    val description = stringResource(R.string.stats_scale_chart, left, right)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val zone = MaterialTheme.colorScheme.tertiary.copy(alpha = ZoneAlpha)
    val center = MaterialTheme.colorScheme.outline
    val marker = if (pair.balanced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(left, style = MaterialTheme.typography.bodySmall)
            Text(right, style = MaterialTheme.typography.bodySmall)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ScaleHeight)
                .semantics { contentDescription = description },
        ) {
            val half = size.width / 2
            val radius = CornerRadius(BarCorner.toPx())
            drawRoundRect(track, cornerRadius = radius)
            val zoneStart = half + (OppositePair.MIN_SHIFT * half).toFloat()
            val zoneEnd = half + (OppositePair.GOOD_SHIFT * half).toFloat()
            drawRect(zone, topLeft = Offset(zoneStart, 0f), size = Size(zoneEnd - zoneStart, size.height))
            pair.shift?.let { shift ->
                val x = half + (shift * half).toFloat()
                val from = minOf(half, x)
                val bar = size.height * BarShare
                drawRect(marker, topLeft = Offset(from, (size.height - bar) / 2), size = Size(abs(x - half), bar))
                val width = MarkerWidth.toPx()
                drawRect(marker, topLeft = Offset(x - width / 2, 0f), size = Size(width, size.height))
            }
            drawLine(center, Offset(half, 0f), Offset(half, size.height), strokeWidth = 1.dp.toPx())
        }
    }
}

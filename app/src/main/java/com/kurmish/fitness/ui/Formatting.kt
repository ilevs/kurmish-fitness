package com.kurmish.fitness.ui

import androidx.compose.ui.unit.dp
import com.kurmish.fitness.core.ExerciseEntry
import com.kurmish.fitness.core.ParsedWorkout
import com.kurmish.fitness.core.SetGroup
import com.kurmish.fitness.core.kg
import kotlin.math.roundToInt

/** Внешний отступ экранов. */
internal val ScreenPadding = 16.dp

/** Промежуток между блоками. */
internal val Gap = 12.dp

/** «100 кг × 5 × 3» или «10 × 3» для собственного веса. */
internal fun SetGroup.label(): String {
    val weight = weightKg?.let { "${it.kg()} × " }.orEmpty()
    val sets = if (sets > 1) " × $sets" else ""
    return "$weight$reps$sets"
}

/** «Жим лёжа: 100 кг × 5, 110 кг × 3». */
internal fun ExerciseEntry.label(): String = "$displayName: ${sets.joinToString { it.label() }}"

/** Фраза для озвучки после сохранения. */
internal fun ParsedWorkout.summary(): String = "Записал. " + entries.joinToString(". ") { entry ->
    val sets = entry.sets.joinToString(", ") { set ->
        val weight = set.weightKg?.let { "${it.kg()} " }.orEmpty()
        val tail = if (set.sets > 1) " в ${set.sets} подходах" else ""
        "$weight на ${set.reps}$tail"
    }
    "${entry.displayName} $sets"
}

private const val KgInTonne = 1000.0
private const val TonneDecimals = 10.0

/** «3,2 т» от тонны, иначе «850 кг». */
internal fun Double.tonnage(): String = if (this >= KgInTonne) {
    "${((this / KgInTonne) * TonneDecimals).roundToInt() / TonneDecimals}".replace('.', ',') + " т"
} else {
    "${roundToInt()} кг"
}

/** Вес, округлённый до килограмма. */
internal fun Double.roundedKg(): String = "${roundToInt()} кг"

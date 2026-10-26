package com.kurmish.fitness.core

/**
 * Итог разбора свободного текста.
 *
 * @property entries узнанные упражнения с подходами.
 * @property feeling самочувствие, если в тексте было «легко» или «тяжело».
 * @property dayOffset сдвиг даты: -1 для «вчера», -2 для «позавчера».
 */
data class ParsedWorkout(val entries: List<ExerciseEntry>, val feeling: Feeling, val dayOffset: Int)

/**
 * Разбирает тренировку, записанную текстом или голосом в свободной форме.
 *
 * Понимает «жим 100 на 5», «присед 120х5х3», «подтягивания 3 по 10», «тяга 3 подхода по 3 с весом 180»
 * и числа словами. Полная грамматика описана в docs/reference/input-format.md.
 */
object WorkoutParser {
    private val kgWords = setOf("кг", "килограмм", "килограмма", "килограммов", "кило")
    private val repWords = setOf(
        "раз", "раза", "повтор", "повтора", "повторов", "повторение", "повторения", "повторений", "повт",
    )
    private val setWords = setOf("подход", "подхода", "подходов", "сет", "сета", "сетов", "серия", "серии", "серий")
    private val weightMarks = setOf("с", "со", "весом", "вес")
    private val fillers = setOf(
        "потом", "затем", "и", "еще", "сделал", "сделала", "делал", "делала", "сегодня", "вчера", "позавчера",
        "было", "был", "очень", "в", "конце", "начале", "а", "тоже", "по", "на", "x",
    )
    private val hardWords = setOf("тяжело", "тяжко", "еле", "трудно", "сложно", "недожал", "провал", "устал")
    private val easyWords = setOf("легко", "изи", "свободно", "просто", "легкотня")

    private const val MAX_WEIGHT = 500.0
    private const val MAX_REPS = 100
    private const val MAX_SETS = 20

    /** Порог, выше которого первое число в «А по Б» считается весом, а не подходами. */
    private const val WEIGHT_THRESHOLD = 20.0
    private const val FULL_CHAIN = 3
    private const val YESTERDAY = -1
    private const val DAY_BEFORE_YESTERDAY = -2

    /** Разбирает текст. Пустые и непонятные куски пропускаются. */
    fun parse(text: String): ParsedWorkout {
        val tokens = TextNormalizer.tokenize(text)
        val blocks = mutableListOf<Block>()
        var current: Block? = null
        val pendingName = mutableListOf<String>()
        var index = 0
        while (index < tokens.size) {
            val match = ExerciseMatcher.matchAt(tokens, index)
            when {
                match != null -> {
                    current = Block(match.first, null).also(blocks::add)
                    pendingName.clear()
                    index = match.second
                }
                tokens[index].isNumber() -> {
                    val block = blockForNumbers(current, pendingName)
                    if (block !== current) {
                        blocks += block
                        current = block
                    }
                    pendingName.clear()
                    val chain = readChain(tokens, index)
                    block.addChain(chain.first)
                    index = chain.second
                }
                else -> {
                    collectNameWord(tokens[index], pendingName)
                    index++
                }
            }
        }
        val entries = blocks.filter { it.groups.isNotEmpty() }
            .map { ExerciseEntry(it.exercise, it.customName, it.groups.toList()) }
        return ParsedWorkout(entries, detectFeeling(tokens), detectDayOffset(tokens))
    }

    private fun blockForNumbers(current: Block?, pendingName: List<String>): Block {
        val startsCustom = pendingName.isNotEmpty() && (current == null || current.groups.isNotEmpty())
        return when {
            startsCustom -> Block(null, pendingName.joinToString(" "))
            current != null -> current
            else -> Block(null, "упражнение")
        }
    }

    private fun collectNameWord(token: String, pendingName: MutableList<String>) {
        val isWord = token.first().isLetter()
        val isKeyword = token in fillers || token in kgWords || token in repWords || token in setWords ||
            token in weightMarks || token in hardWords || token in easyWords
        if (isWord && !isKeyword) {
            pendingName += token
        } else if (!isWord) {
            pendingName.clear()
        }
    }

    /** Читает цепочку чисел, связанных «x», «на», «по» и единицами измерения. */
    private fun readChain(tokens: List<String>, start: Int): Pair<Chain, Int> {
        val chain = Chain()
        var index = start
        while (index < tokens.size && chain.accept(tokens[index])) {
            index++
        }
        return chain to index
    }

    private fun roleOf(unitWord: String): Role = when (unitWord) {
        in kgWords -> Role.Weight
        in repWords -> Role.Reps
        else -> Role.Sets
    }

    private fun interpret(chain: Chain, bodyweight: Boolean): SetGroup? {
        val slots = chain.slots
        val assigned = slots.mapNotNull { it.role }.toSet()
        val order = positionalOrder(slots.size, chain.ops.firstOrNull(), slots.first().value, bodyweight) - assigned
        slots.filter { it.role == null }.forEachIndexed { i, slot -> slot.role = order.getOrNull(i) }
        val weight = slots.firstOrNull { it.role == Role.Weight }?.value
        val reps = slots.firstOrNull { it.role == Role.Reps }?.value?.toInt()
            ?: if (weight != null && !bodyweight) 1 else return null
        val sets = slots.firstOrNull { it.role == Role.Sets }?.value?.toInt() ?: 1
        val valid = reps in 1..MAX_REPS && sets in 1..MAX_SETS && (weight == null || weight in 0.0..MAX_WEIGHT)
        return if (valid) SetGroup(weight, reps, sets) else null
    }

    /** Как раскладывать числа без единиц измерения: правила из таблицы в docs/reference/input-format.md. */
    private fun positionalOrder(count: Int, firstOp: String?, firstValue: Double?, bodyweight: Boolean): List<Role> {
        val setsFirst = firstOp == "по" && (firstValue ?: 0.0) < WEIGHT_THRESHOLD
        return when {
            count >= FULL_CHAIN -> listOf(Role.Weight, Role.Reps, Role.Sets)
            count == 2 && bodyweight && firstOp != "на" -> listOf(Role.Sets, Role.Reps)
            count == 2 && setsFirst -> listOf(Role.Sets, Role.Reps)
            count == 2 -> listOf(Role.Weight, Role.Reps)
            bodyweight -> listOf(Role.Reps)
            else -> listOf(Role.Weight)
        }
    }

    private fun detectFeeling(tokens: List<String>): Feeling {
        val negatedEasy = tokens.zipWithNext().any { (a, b) -> a == "не" && b in easyWords }
        return when {
            negatedEasy || tokens.any { it in hardWords } || "не дожал" in tokens.joinToString(" ") -> Feeling.Hard
            tokens.any { it in easyWords } -> Feeling.Easy
            else -> Feeling.Normal
        }
    }

    private fun detectDayOffset(tokens: List<String>): Int = when {
        "позавчера" in tokens -> DAY_BEFORE_YESTERDAY
        "вчера" in tokens -> YESTERDAY
        else -> 0
    }

    private fun String.isNumber(): Boolean = first().isDigit()

    private enum class Role { Weight, Reps, Sets }

    private class Slot(val value: Double, var role: Role?)

    private class Chain {
        val slots = mutableListOf<Slot>()
        val ops = mutableListOf<String>()
        private var expectsNumber = true
        private var markWeight = false

        /** Принимает очередной токен; false, если цепочка на нём закончилась. */
        fun accept(token: String): Boolean = when {
            token.isNumber() -> acceptNumber(token)
            token == "x" || token == "на" || token == "по" -> {
                ops += token
                expectsNumber = true
                true
            }
            token in weightMarks -> {
                markWeight = true
                expectsNumber = true
                true
            }
            token in kgWords || token in repWords || token in setWords -> {
                slots.lastOrNull()?.let { if (it.role == null) it.role = roleOf(token) }
                expectsNumber = true
                true
            }
            else -> false
        }

        private fun acceptNumber(token: String): Boolean {
            if (!expectsNumber) return false
            slots += Slot(token.toDouble(), if (markWeight) Role.Weight else null)
            expectsNumber = false
            markWeight = false
            return true
        }
    }

    private class Block(val exercise: Exercise?, val customName: String?) {
        val groups = mutableListOf<SetGroup>()

        /** «…, 3 подхода» после веса и повторов уточняет число подходов предыдущей строки. */
        fun addChain(chain: Chain) {
            val onlySets = chain.slots.singleOrNull()?.role == Role.Sets
            if (onlySets && groups.isNotEmpty()) {
                groups[groups.lastIndex] = groups.last().copy(sets = chain.slots.single().value.toInt())
            } else {
                interpret(chain, exercise?.bodyweight == true)?.let(groups::add)
            }
        }
    }
}

/** Находит название упражнения в потоке токенов, сравнивая слова по основам. */
internal object ExerciseMatcher {
    private const val SHORT_WORD = 4
    private const val SHORT_STEM = 3
    private const val LONG_STEM = 4
    private const val SUFFIX = 2

    private val synonyms: List<Pair<List<String>, Exercise>> = Exercise.entries
        .flatMap { exercise -> exercise.synonyms.map { it.split(" ") to exercise } }
        .sortedByDescending { it.first.size }

    /** Возвращает упражнение и индекс токена после его названия, если название начинается в [start]. */
    fun matchAt(tokens: List<String>, start: Int): Pair<Exercise, Int>? = synonyms
        .firstOrNull { (words, _) ->
            words.indices.all { i -> tokens.getOrNull(start + i)?.let { sameStem(it, words[i]) } == true }
        }
        ?.let { (words, exercise) -> exercise to start + words.size }

    private fun sameStem(token: String, word: String): Boolean {
        val stem = if (word.length <= SHORT_WORD) {
            word.take(maxOf(SHORT_STEM, word.length - 1))
        } else {
            word.take(maxOf(LONG_STEM, word.length - SUFFIX))
        }
        return token.startsWith(stem) && token.length <= word.length + SUFFIX + 1
    }
}

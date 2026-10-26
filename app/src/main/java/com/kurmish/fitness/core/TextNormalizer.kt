package com.kurmish.fitness.core

/** Приводит свободную речь к виду, удобному для разбора: нижний регистр, «е» вместо «ё», числа цифрами. */
internal object TextNormalizer {
    private const val HALF = 0.5
    private val units = mapOf(
        "ноль" to 0, "один" to 1, "одна" to 1, "одну" to 1, "два" to 2, "две" to 2, "три" to 3,
        "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9, "десять" to 10,
        "одиннадцать" to 11, "двенадцать" to 12, "тринадцать" to 13, "четырнадцать" to 14, "пятнадцать" to 15,
        "шестнадцать" to 16, "семнадцать" to 17, "восемнадцать" to 18, "девятнадцать" to 19,
    )
    private val tens = mapOf(
        "двадцать" to 20,
        "тридцать" to 30,
        "сорок" to 40,
        "пятьдесят" to 50,
        "шестьдесят" to 60,
        "семьдесят" to 70,
        "восемьдесят" to 80,
        "девяносто" to 90,
    )
    private val hundreds = mapOf(
        "сто" to 100,
        "двести" to 200,
        "триста" to 300,
        "четыреста" to 400,
        "пятьсот" to 500,
    )
    private val tokenPattern = Regex("""\d+(?:\.\d+)?|[a-zа-я]+|[^\sa-zа-я\d]""")
    private val decimalComma = Regex("""(\d),(\d)""")
    private val timesSign = Regex("""(\d)\s*[xх×*]\s*(?=\d)""")

    /** Разбивает текст на токены: числа, слова и отдельные знаки. */
    fun tokenize(text: String): List<String> {
        val prepared = text.lowercase()
            .replace('ё', 'е')
            .replace(decimalComma, "$1.$2")
            .replace(timesSign, "$1 x ")
        return joinNumberWords(tokenPattern.findAll(prepared).map { it.value }.toList())
    }

    private fun joinNumberWords(tokens: List<String>): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index < tokens.size) {
            val parsed = readNumber(tokens, index)
            if (parsed == null) {
                result += tokens[index]
                index++
            } else {
                result += formatNumber(parsed.first)
                index = parsed.second
            }
        }
        return result
    }

    /** Читает число словами: «сто двадцать два с половиной» превращается в 122.5. */
    private fun readNumber(tokens: List<String>, start: Int): Pair<Double, Int>? {
        var index = start
        var value = 0.0
        var matched = false
        hundreds[tokens[index]]?.let {
            value += it
            index++
            matched = true
        }
        tens[tokens.getOrNull(index)]?.let {
            value += it
            index++
            matched = true
        }
        val unit = tokens.getOrNull(index)?.let { units[it] }
        if (unit != null) {
            value += unit
            index++
            matched = true
        }
        if (matched && tokens.getOrNull(index) == "с" && tokens.getOrNull(index + 1) == "половиной") {
            value += HALF
            index += 2
        }
        return if (matched) value to index else null
    }

    private fun formatNumber(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else value.toString()
}

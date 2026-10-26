package com.kurmish.fitness.core

/** Подбор названия упражнения по первым буквам: база упражнений и свои названия из Дневника. */
object ExerciseNames {
    /** Сколько подсказок показывать при вводе. */
    const val LIMIT = 8

    /** Упражнение базы, чьё название или синоним совпадает с [name] без учёта регистра и «ё». */
    fun resolve(name: String): Exercise? {
        val key = normalize(name)
        return Exercise.entries.firstOrNull { normalize(it.title) == key || key in it.synonyms }
    }

    /**
     * Подсказки к вводу [query]: сначала названия, начинающиеся с введённого, потом содержащие его
     * в начале любого слова. Свои названия [custom] идут впереди базы. Пустой запрос даёт весь список.
     */
    fun suggest(query: String, custom: List<String>, limit: Int = LIMIT): List<String> {
        val key = normalize(query)
        val all = (custom + Exercise.entries.map { it.title }).distinctBy(::normalize)
        if (key.isEmpty()) return all
        val names = all.map { name -> name to (listOf(name) + synonymsOf(name)).map(::normalize) }
        val starts = names.filter { (_, keys) -> keys.any { it.startsWith(key) } }.map { it.first }
        val words = names.filter { (_, keys) -> keys.any { k -> k.split(' ', '-').any { it.startsWith(key) } } }
            .map { it.first }
        return (starts + words).distinct().filterNot { normalize(it) == key }.take(limit)
    }

    private fun synonymsOf(title: String): List<String> =
        Exercise.entries.firstOrNull { it.title == title }?.synonyms.orEmpty()

    private fun normalize(text: String): String = text.trim().lowercase().replace('ё', 'е')
}

/** Свои названия упражнений из Дневника, новые первыми, без повторов. */
fun AppData.customExerciseNames(): List<String> = sessions
    .sortedByDescending { it.epochDay }
    .flatMap { session -> session.entries.filter { it.exercise == null }.map { it.displayName } }
    .filter { it.isNotBlank() }
    .distinctBy { it.lowercase().replace('ё', 'е') }

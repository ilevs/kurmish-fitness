package com.kurmish.fitness

import org.junit.Assert.assertEquals
import org.junit.Test

/** Проверяет, что модульные тесты запускаются в CI. */
class SmokeTest {
    @Test
    fun applicationIdIsStable() {
        assertEquals("com.kurmish.fitness", BuildConfig.APPLICATION_ID)
    }
}

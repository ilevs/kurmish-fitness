package com.kurmish.fitness.core

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CloudBackupTest {
    private val server = MockWebServer()
    private val disk by lazy { YandexDisk("me", "secret", server.url("").toString().trimEnd('/')) }
    private val journal = "{\"sessions\":[]}".toByteArray()

    @After
    fun stop() {
        server.shutdown()
    }

    @Test
    fun uploadCreatesFoldersAndWritesDailyAndCurrentCopies() {
        server.enqueue(MockResponse().setResponseCode(201))
        // Папка истории уже есть: Диск отвечает 405, это не ошибка.
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(201))

        CloudBackup.upload(disk, journal, LocalDate.of(2026, 10, 26))

        val requests = List(4) { server.takeRequest() }
        assertEquals(listOf("MKCOL", "MKCOL", "PUT", "PUT"), requests.map { it.method })
        assertEquals(
            listOf(
                "/kurmish-fitness",
                "/kurmish-fitness/history",
                "/kurmish-fitness/history/journal-2026-10-26.json",
                "/kurmish-fitness/journal.json",
            ),
            requests.map { it.path },
        )
        assertArrayEquals(journal, requests.last().body.readByteArray())
        assertTrue(requests.all { it.getHeader("Authorization") == "Basic bWU6c2VjcmV0" })
    }

    @Test
    fun wrongPasswordIsReported() {
        server.enqueue(MockResponse().setResponseCode(401))
        val failure = runCatching { CloudBackup.upload(disk, journal, LocalDate.of(2026, 10, 26)) }.exceptionOrNull()
        assertTrue(failure is CloudException && failure.unauthorized)
    }

    @Test
    fun downloadReturnsCurrentCopyOrNull() {
        server.enqueue(MockResponse().setBody("{\"sessions\":[]}"))
        server.enqueue(MockResponse().setResponseCode(404))
        assertArrayEquals(journal, CloudBackup.download(disk))
        assertEquals("/kurmish-fitness/journal.json", server.takeRequest().path)
        assertNull(CloudBackup.download(disk))
    }
}

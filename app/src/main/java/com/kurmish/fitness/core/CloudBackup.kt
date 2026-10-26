package com.kurmish.fitness.core

import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.HttpURLConnection
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Файлы в облаке. Ошибки — [IOException] или [CloudException]. */
interface CloudFiles {
    /** Создаёт папку, если её ещё нет. */
    fun createFolder(path: String)

    /** Записывает файл целиком, заменяя прежний. */
    fun upload(path: String, bytes: ByteArray)

    /** Читает файл; null, если его нет. */
    fun download(path: String): ByteArray?
}

/**
 * Облако ответило ошибкой. Сетевые сбои — обычный [IOException].
 *
 * @property code HTTP-код ответа.
 */
class CloudException(message: String, val code: Int) : IOException(message) {
    /** Неверный логин или пароль приложения. */
    val unauthorized: Boolean
        get() = code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN
}

/**
 * Где лежит бэкап Дневника и как его записать и прочитать.
 *
 * В папке [FOLDER] лежит текущая копия и по копии на каждый день: если в Дневнике что-то
 * испортили, можно вернуться к прошлому дню.
 */
object CloudBackup {
    /** Папка бэкапа в корне Диска. */
    const val FOLDER = "kurmish-fitness"

    /** Текущая копия. */
    const val CURRENT = "$FOLDER/journal.json"

    private const val HISTORY = "$FOLDER/history"

    /** Копия за день [date]. */
    fun historyPath(date: LocalDate): String = "$HISTORY/journal-$date.json"

    /** Сохраняет [bytes] как текущую копию и как копию за [date]. */
    fun upload(files: CloudFiles, bytes: ByteArray, date: LocalDate) {
        files.createFolder(FOLDER)
        files.createFolder(HISTORY)
        files.upload(historyPath(date), bytes)
        // Текущая копия пишется последней: если связь оборвётся раньше, она останется целой прежней.
        files.upload(CURRENT, bytes)
    }

    /** Текущая копия; null, если бэкапа ещё нет. */
    fun download(files: CloudFiles): ByteArray? = files.download(CURRENT)
}

/**
 * Яндекс Диск по WebDAV. Вход по логину и паролю приложения из id.yandex.ru, тип «Файлы»:
 * регистрировать своё приложение в Яндексе не нужно.
 */
class YandexDisk(
    login: String,
    password: String,
    private val baseUrl: String = BASE_URL,
    private val client: OkHttpClient = defaultClient,
) : CloudFiles {
    private val authorization = Credentials.basic(login.trim(), password.trim())

    override fun createFolder(path: String) {
        execute(request(path).method("MKCOL", null).build()) { code ->
            // 405: папка уже есть.
            code == HttpURLConnection.HTTP_CREATED || code == HttpURLConnection.HTTP_BAD_METHOD
        }
    }

    override fun upload(path: String, bytes: ByteArray) {
        execute(request(path).put(bytes.toRequestBody()).build()) { it in SUCCESS }
    }

    override fun download(path: String): ByteArray? {
        client.newCall(request(path).get().build()).execute().use { response ->
            return when {
                response.code == HttpURLConnection.HTTP_NOT_FOUND -> null
                response.isSuccessful -> response.body?.bytes() ?: ByteArray(0)
                else -> throw CloudException("Яндекс Диск ответил ${response.code}", response.code)
            }
        }
    }

    private fun request(path: String): Request.Builder =
        Request.Builder().url("$baseUrl/$path").header("Authorization", authorization)

    private fun execute(request: Request, accepted: (Int) -> Boolean) {
        client.newCall(request).execute().use { response ->
            if (!accepted(response.code)) {
                throw CloudException("Яндекс Диск ответил ${response.code}", response.code)
            }
        }
    }

    private companion object {
        const val BASE_URL = "https://webdav.yandex.ru"
        const val TIMEOUT_SECONDS = 30L
        val SUCCESS = 200..299
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        }
    }
}

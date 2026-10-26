package com.kurmish.fitness.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Пользователь или бот Telegram.
 *
 * @property id номер.
 * @property username имя без @, если задано.
 * @property firstName отображаемое имя.
 */
@Serializable
data class TgUser(
    val id: Long,
    val username: String? = null,
    @SerialName("first_name") val firstName: String = "",
)

/**
 * Чат.
 *
 * @property id номер чата.
 * @property type private, group, supergroup или channel.
 */
@Serializable
data class TgChat(val id: Long, val type: String = "")

/**
 * Сообщение.
 *
 * @property messageId номер в чате.
 * @property date время отправки, секунды с 1970-01-01 UTC.
 * @property chat чат сообщения.
 * @property text текст; null для голосовых, фото и прочего.
 */
@Serializable
data class TgMessage(
    @SerialName("message_id") val messageId: Long,
    val date: Long = 0,
    val chat: TgChat,
    val text: String? = null,
)

/**
 * Событие из getUpdates.
 *
 * @property updateId сквозной номер события.
 * @property message новое сообщение.
 * @property editedMessage исправленное сообщение.
 */
@Serializable
data class TgUpdate(
    @SerialName("update_id") val updateId: Long,
    val message: TgMessage? = null,
    @SerialName("edited_message") val editedMessage: TgMessage? = null,
)

/**
 * Ответ Bot API.
 *
 * @property ok запрос выполнен.
 * @property result результат при успехе.
 * @property description текст ошибки.
 * @property errorCode код ошибки.
 * @property parameters подробности ошибки.
 */
@Serializable
data class TgResponse<T>(
    val ok: Boolean,
    val result: T? = null,
    val description: String? = null,
    @SerialName("error_code") val errorCode: Int? = null,
    val parameters: TgErrorParameters? = null,
)

/**
 * Подробности ошибки.
 *
 * @property retryAfter через сколько секунд можно повторить запрос.
 */
@Serializable
data class TgErrorParameters(@SerialName("retry_after") val retryAfter: Int? = null)

/**
 * Telegram ответил ошибкой: неверный токен, лимит запросов и т.п. Сетевые сбои — это [IOException].
 *
 * @property code код ошибки Bot API.
 */
class TelegramException(message: String, val code: Int?) : IOException(message) {
    /** Токен неверный или бот удалён. */
    val unauthorized: Boolean
        get() = code == UNAUTHORIZED

    /** Правка совпадает с тем, что уже в сообщении. */
    val notModified: Boolean
        get() = message.orEmpty().contains(NOT_MODIFIED)

    private companion object {
        const val UNAUTHORIZED = 401
        const val NOT_MODIFIED = "message is not modified"
    }
}

/** Методы Bot API, которые нужны синхронизации. Ошибки — [IOException] или [TelegramException]. */
interface TelegramApi {
    /** Проверяет токен и возвращает бота. */
    fun getMe(): TgUser

    /** Новые события начиная с [offset]; заодно подтверждает получение всех более ранних. */
    fun getUpdates(offset: Long): List<TgUpdate>

    /** Отправляет текст в чат. */
    fun sendMessage(chatId: Long, text: String): TgMessage

    /** Заменяет текст сообщения бота. */
    fun editMessage(chatId: Long, messageId: Long, text: String)

    /** Удаляет сообщение бота. */
    fun deleteMessage(chatId: Long, messageId: Long)

    /** Ставит реакцию на сообщение. */
    fun react(chatId: Long, messageId: Long, emoji: String)
}

/** [TelegramApi] поверх HTTPS, без сторонних библиотек. */
class HttpTelegramApi(private val token: String) : TelegramApi {
    private val json = Json { ignoreUnknownKeys = true }

    override fun getMe(): TgUser = call("getMe", JsonObject(emptyMap()), TgUser.serializer())

    override fun getUpdates(offset: Long): List<TgUpdate> = call(
        method = "getUpdates",
        body = buildJsonObject {
            put("offset", offset)
            put("allowed_updates", buildJsonArray { AllowedUpdates.forEach { add(JsonPrimitive(it)) } })
        },
        serializer = ListSerializer(TgUpdate.serializer()),
    )

    override fun sendMessage(chatId: Long, text: String): TgMessage = call(
        method = "sendMessage",
        body = buildJsonObject {
            put("chat_id", chatId)
            put("text", text)
        },
        serializer = TgMessage.serializer(),
    )

    override fun editMessage(chatId: Long, messageId: Long, text: String) {
        call(
            method = "editMessageText",
            body = buildJsonObject {
                put("chat_id", chatId)
                put("message_id", messageId)
                put("text", text)
            },
            serializer = TgMessage.serializer(),
        )
    }

    override fun deleteMessage(chatId: Long, messageId: Long) {
        call(
            method = "deleteMessage",
            body = buildJsonObject {
                put("chat_id", chatId)
                put("message_id", messageId)
            },
            serializer = Boolean.serializer(),
        )
    }

    override fun react(chatId: Long, messageId: Long, emoji: String) {
        val reaction = buildJsonObject {
            put("type", "emoji")
            put("emoji", emoji)
        }
        call(
            method = "setMessageReaction",
            body = buildJsonObject {
                put("chat_id", chatId)
                put("message_id", messageId)
                put("reaction", buildJsonArray { add(reaction) })
            },
            serializer = Boolean.serializer(),
        )
    }

    private fun <T> call(method: String, body: JsonObject, serializer: KSerializer<T>): T {
        val connection = URI.create("$BASE_URL$token/$method").toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val stream = if (connection.responseCode < HTTP_ERROR) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = runCatching {
                json.decodeFromString(TgResponse.serializer(serializer), text)
            }.getOrElse { throw IOException("Непонятный ответ Telegram: HTTP ${connection.responseCode}", it) }
            val result = response.result
            if (!response.ok || result == null) {
                throw TelegramException(response.description ?: "Ошибка Telegram", response.errorCode)
            }
            return result
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE_URL = "https://api.telegram.org/bot"
        const val TIMEOUT_MS = 15_000
        const val HTTP_ERROR = 400
        val AllowedUpdates = listOf("message", "edited_message")
    }
}

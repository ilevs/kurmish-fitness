package com.kurmish.fitness.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit

/**
 * Файл копии на Google Диске, выбранный через системное окно. Хранится ссылка на документ: приложение пишет
 * в него само, а на Диск файл отправляет приложение Google Диск, так что вход в Google здесь не нужен.
 */
internal class DriveSettings(context: Context) {
    private val app = context.applicationContext

    /** Сами настройки: на их изменения подписывается экран. */
    val preferences: SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Документ копии; null — файл не выбран. */
    val uri: Uri?
        get() = preferences.getString(URI, null)?.let(Uri::parse)

    /** Имя файла для экрана. */
    val fileName: String
        get() = preferences.getString(FILE_NAME, null).orEmpty()

    /** Когда копия последний раз записана, секунды с 1970 года; null — ещё ни разу. */
    val lastSuccessEpochSecond: Long?
        get() = preferences.getLong(LAST_SUCCESS, 0).takeIf { it > 0 }

    /** Отпечаток последней записанной копии: одинаковое второй раз не пишется. */
    val lastHash: String
        get() = preferences.getString(LAST_HASH, null).orEmpty()

    /** Файл перестал открываться: его удалили или отозвали доступ. */
    val lost: Boolean
        get() = preferences.getBoolean(LOST, false)

    private fun link(uri: Uri, fileName: String) {
        preferences.edit {
            putString(URI, uri.toString())
            putString(FILE_NAME, fileName)
            remove(LAST_HASH)
            putBoolean(LOST, false)
        }
    }

    /** Получает постоянный доступ к документу [uri] и запоминает его; false — система доступа не дала. */
    fun link(uri: Uri): Boolean {
        val kept = Documents.keepAccess(app, uri)
        if (kept) link(uri, Documents.name(app, uri))
        return kept
    }

    /** Забывает файл и отдаёт доступ к нему; сам файл на Диске остаётся. */
    fun unlink() {
        uri?.let { Documents.releaseAccess(app, it) }
        preferences.edit { clear() }
    }

    /** Отмечает удачную запись. */
    fun markSent(hash: String, epochSecond: Long) {
        preferences.edit {
            putString(LAST_HASH, hash)
            putLong(LAST_SUCCESS, epochSecond)
            putBoolean(LOST, false)
        }
    }

    /** Отмечает, что файл не открылся. */
    fun markLost() {
        preferences.edit { putBoolean(LOST, true) }
    }

    private companion object {
        const val NAME = "drive-backup"
        const val URI = "uri"
        const val FILE_NAME = "fileName"
        const val LAST_SUCCESS = "lastSuccess"
        const val LAST_HASH = "lastHash"
        const val LOST = "lost"
    }
}

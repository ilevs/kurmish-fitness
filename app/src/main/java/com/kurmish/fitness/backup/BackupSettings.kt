package com.kurmish.fitness.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.kurmish.fitness.core.YandexDisk

/**
 * Настройки бэкапа на Яндекс Диск. Лежат отдельно от Дневника, чтобы пароль не попадал в бэкап,
 * а восстановление не стирало вход.
 */
internal class BackupSettings(context: Context) {
    /** Сами настройки: на их изменения подписывается экран. */
    val preferences: SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Логин Яндекса. */
    val login: String
        get() = preferences.getString(LOGIN, null).orEmpty()

    private val password: String
        get() = preferences.getString(PASSWORD, null).orEmpty()

    /** Вход задан. */
    val connected: Boolean
        get() = login.isNotBlank() && password.isNotBlank()

    /** Когда копия последний раз ушла на Диск, секунды с 1970 года; null — ещё ни разу. */
    val lastSuccessEpochSecond: Long?
        get() = preferences.getLong(LAST_SUCCESS, 0).takeIf { it > 0 }

    /** Отпечаток последней отправленной копии: одинаковое второй раз не отправляется. */
    val lastHash: String
        get() = preferences.getString(LAST_HASH, null).orEmpty()

    /** Последняя ошибка входа; null, если вход работает. */
    val authError: Boolean
        get() = preferences.getBoolean(AUTH_ERROR, false)

    /** Диск с сохранённым входом; null, если вход не задан. */
    fun disk(): YandexDisk? = if (connected) YandexDisk(login, password) else null

    /** Запоминает вход. */
    fun connect(login: String, password: String) {
        preferences.edit {
            putString(LOGIN, login.trim())
            putString(PASSWORD, password.trim())
            remove(LAST_HASH)
            putBoolean(AUTH_ERROR, false)
        }
    }

    /** Забывает вход; копии на Диске остаются. */
    fun disconnect() {
        preferences.edit { clear() }
    }

    /** Отмечает удачную отправку. */
    fun markSent(hash: String, epochSecond: Long) {
        preferences.edit {
            putString(LAST_HASH, hash)
            putLong(LAST_SUCCESS, epochSecond)
            putBoolean(AUTH_ERROR, false)
        }
    }

    /** Отмечает, что Диск не принял логин или пароль. */
    fun markAuthError() {
        preferences.edit { putBoolean(AUTH_ERROR, true) }
    }

    private companion object {
        const val NAME = "backup"
        const val LOGIN = "login"
        const val PASSWORD = "password"
        const val LAST_SUCCESS = "lastSuccess"
        const val LAST_HASH = "lastHash"
        const val AUTH_ERROR = "authError"
    }
}

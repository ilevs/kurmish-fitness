package com.kurmish.fitness.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.IOException

/** Чтение и запись документов, выбранных в системном окне: файлы на телефоне, Google Диск и другие. */
internal object Documents {
    private const val ACCESS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    /** Записывает [bytes] в документ [uri] целиком, заменяя прежнее содержимое. */
    fun write(context: Context, uri: Uri, bytes: ByteArray) {
        val resolver = context.contentResolver
        // «wt» обрезает старое содержимое; кто его не знает, обрезает и при «w».
        val stream = try {
            resolver.openOutputStream(uri, "wt")
        } catch (ignored: IllegalArgumentException) {
            resolver.openOutputStream(uri, "w")
        } catch (ignored: FileNotFoundException) {
            resolver.openOutputStream(uri, "w")
        }
        (stream ?: throw IOException("Документ не открылся для записи")).use { it.write(bytes) }
    }

    /** Содержимое документа [uri]. */
    fun read(context: Context, uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Документ не открылся для чтения")

    /** Имя документа [uri] для экрана. */
    fun name(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()

    /** Сохраняет доступ к документу [uri] после перезапуска телефона; false — система не дала. */
    fun keepAccess(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(uri, ACCESS)
        true
    } catch (ignored: SecurityException) {
        false
    }

    /** Отдаёт сохранённый доступ к документу [uri]. */
    fun releaseAccess(context: Context, uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(uri, ACCESS)
        } catch (ignored: SecurityException) {
            // Доступа уже нет.
        }
    }
}

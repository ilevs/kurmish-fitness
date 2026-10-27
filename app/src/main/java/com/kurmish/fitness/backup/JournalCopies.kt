package com.kurmish.fitness.backup

import android.content.Context
import android.net.Uri
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.JournalStore
import java.io.File
import java.io.IOException
import java.time.LocalDate

/** Чем закончилось последнее действие с файлом копии. */
enum class FileStatus {
    /** Ничего не делали. */
    Idle,

    /** Идёт запись или чтение. */
    Working,

    /** Копия записана. */
    Saved,

    /** Дневник восстановлен из файла. */
    Restored,

    /** Файл не похож на Дневник. */
    BadBackup,

    /** Файл не открылся. */
    Failed,

    /** Дневник пуст: сохранять нечего. */
    Empty,
}

/**
 * Прочитанная копия.
 *
 * @property status итог чтения.
 * @property data Дневник из файла; null, если прочитать не вышло.
 */
internal class ReadCopy(val status: FileStatus, val data: AppData?)

/** Копии файла Дневника в документах, выбранных в системном окне. */
internal class JournalCopies(private val context: Context) {
    private val store = JournalStore(Backup.journalFile(context))

    /** Файл Дневника; null, если он ещё пуст. */
    fun raw(): ByteArray? = store.raw()

    /** Записывает Дневник в документ [uri]. */
    fun save(uri: Uri): FileStatus {
        val bytes = store.raw() ?: return FileStatus.Empty
        return try {
            Documents.write(context, uri, bytes)
            FileStatus.Saved
        } catch (ignored: IOException) {
            FileStatus.Failed
        } catch (ignored: SecurityException) {
            FileStatus.Failed
        }
    }

    /** Читает Дневник из документа [uri]. */
    fun load(uri: Uri): ReadCopy {
        val bytes = try {
            Documents.read(context, uri)
        } catch (ignored: IOException) {
            null
        } catch (ignored: SecurityException) {
            null
        }
        val data = bytes?.let(store::decode)
        val status = when {
            bytes == null -> FileStatus.Failed
            data == null -> FileStatus.BadBackup
            else -> FileStatus.Restored
        }
        return ReadCopy(status, data)
    }

    /** Копия во временной папке для «Поделиться»; null, если Дневник пуст. */
    fun shareFile(date: LocalDate): File? = store.raw()?.let { bytes ->
        val folder = File(context.cacheDir, SHARE_FOLDER).apply { mkdirs() }
        File(folder, fileName(date)).apply { writeBytes(bytes) }
    }

    /** Имена файлов копии. */
    companion object {
        /** Папка в кэше для «Поделиться»; её же открывает FileProvider. */
        const val SHARE_FOLDER = "shared"

        /** Имя файла копии за день [date]. */
        fun fileName(date: LocalDate): String = "kurmish-fitness-$date.json"
    }
}

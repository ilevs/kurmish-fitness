package com.kurmish.fitness.backup

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kurmish.fitness.core.JournalStore
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Обновляет файл копии на Google Диске. Сеть не нужна: файл забирает приложение Google Диск
 * и отправляет его само, когда появится интернет.
 */
internal class DriveBackupWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val settings = DriveSettings(applicationContext)
        val uri = settings.uri
        val bytes = JournalStore(Backup.journalFile(applicationContext)).raw()
        if (uri == null || bytes == null || Backup.fingerprint(bytes) == settings.lastHash) return Result.success()
        return write(settings, uri, bytes)
    }

    private fun write(settings: DriveSettings, uri: Uri, bytes: ByteArray): Result = try {
        Documents.write(applicationContext, uri, bytes)
        settings.markSent(Backup.fingerprint(bytes), Instant.now().epochSecond)
        Result.success()
    } catch (ignored: SecurityException) {
        settings.markLost()
        Result.failure()
    } catch (ignored: IOException) {
        Result.retry()
    }
}

/** Планирование записи копии на Google Диск. */
internal object DriveBackup {
    private const val WORK = "drive-backup"
    private const val BATCH_SECONDS = 30L

    /** Обновить файл копии; частые правки сливаются в одну запись. */
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DriveBackupWorker>().setInitialDelay(BATCH_SECONDS, TimeUnit.SECONDS).build(),
        )
    }

    /** Отменяет запись, когда файл забыт. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
}

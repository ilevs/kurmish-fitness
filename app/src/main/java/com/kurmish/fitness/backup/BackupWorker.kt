package com.kurmish.fitness.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kurmish.fitness.core.CloudBackup
import com.kurmish.fitness.core.CloudException
import com.kurmish.fitness.core.CloudFiles
import com.kurmish.fitness.core.JournalStore
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Отправляет файл Дневника на Яндекс Диск. Запускается системой, когда есть сеть, даже если приложение
 * закрыто; без сети ждёт её сам.
 */
internal class BackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val settings = BackupSettings(applicationContext)
        val disk = settings.disk()
        val bytes = JournalStore(Backup.journalFile(applicationContext)).raw()
        if (disk == null || bytes == null) return Result.success()
        val hash = Backup.fingerprint(bytes)
        return when {
            hash == settings.lastHash -> Result.success()
            else -> upload(settings, disk, bytes, hash)
        }
    }

    private fun upload(settings: BackupSettings, disk: CloudFiles, bytes: ByteArray, hash: String): Result = try {
        CloudBackup.upload(disk, bytes, LocalDate.now())
        settings.markSent(hash, Instant.now().epochSecond)
        Result.success()
    } catch (rejected: CloudException) {
        if (rejected.unauthorized) {
            settings.markAuthError()
            Result.failure()
        } else {
            Result.retry()
        }
    } catch (ignored: IOException) {
        Result.retry()
    }
}

/** Планирование бэкапа. */
internal object Backup {
    private const val NOW = "backup-now"
    private const val DAILY = "backup-daily"
    private const val DAILY_HOURS = 24L
    private const val BATCH_SECONDS = 30L

    /** Отпечаток файла: одинаковые копии второй раз не отправляются. */
    fun fingerprint(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(Locale.ROOT, it) }

    /** Файл Дневника приложения. */
    fun journalFile(context: Context): File = File(context.filesDir, "journal.json")

    /**
     * Отправить копию, как только будет сеть. Частые правки сливаются в одну отправку,
     * а раз в сутки копия проверяется ещё раз на случай, если что-то не дошло.
     */
    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context)
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        manager.enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(network)
                .setInitialDelay(BATCH_SECONDS, TimeUnit.SECONDS)
                .build(),
        )
        manager.enqueueUniquePeriodicWork(
            DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupWorker>(DAILY_HOURS, TimeUnit.HOURS).setConstraints(network).build(),
        )
    }

    /** Отменяет отправки, когда вход забыт. */
    fun cancel(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(NOW)
        manager.cancelUniqueWork(DAILY)
    }
}

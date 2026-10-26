package com.kurmish.fitness.ui.voice

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Модель распознавания русской речи GigaAM v3 (CTC, int8) для sherpa-onnx.
 *
 * Модель весит около 225 МБ и в APK не входит: скачивается один раз при первой голосовой записи
 * в файлы приложения и дальше работает без сети. Детектор речи Silero VAD маленький и лежит в assets.
 */
internal object VoiceModel {
    /** Имя папки модели в файлах приложения. Новая версия модели — новое имя, старая папка удаляется. */
    const val NAME = "gigaam-v3-ctc-int8"

    /** Файл нейросети. */
    const val MODEL_FILE = "model.int8.onnx"

    /** Словарь символов модели. */
    const val TOKENS_FILE = "tokens.txt"

    /** Частота дискретизации звука для модели и детектора речи. */
    const val SAMPLE_RATE = 16_000

    /** Сколько отсчётов детектор речи принимает за раз. */
    const val VAD_WINDOW = 512

    private const val FEATURE_DIM = 64
    private const val THREADS = 4
    private const val VAD_ASSET = "silero_vad.onnx"
    private const val PAUSE_SECONDS = 0.8f
    private const val MAX_PHRASE_SECONDS = 20f
    private const val BUFFER_BYTES = 64 * 1024
    private const val TIMEOUT_MS = 30_000
    private const val PERCENT = 100

    /** Сначала зеркало в релизах проекта, затем исходный репозиторий модели. */
    private val mirrors = listOf(
        "https://github.com/ilevs/kurmish-fitness/releases/download/voice-model/",
        "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-giga-am-v3-russian-2025-12-16/resolve/main/",
    )

    /** Скачана ли модель целиком в [dir]. */
    fun isReady(dir: File): Boolean = listOf(TOKENS_FILE, MODEL_FILE).all { File(dir, it).length() > 0 }

    /** Загружает скачанную модель из [dir] в память. Это занимает несколько секунд. */
    fun recognizer(dir: File): OfflineRecognizer = OfflineRecognizer(
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = FEATURE_DIM),
            modelConfig = OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(model = File(dir, MODEL_FILE).absolutePath),
                tokens = File(dir, TOKENS_FILE).absolutePath,
                numThreads = THREADS,
            ),
        ),
    )

    /** Детектор речи: режет звук на фразы по паузам. */
    fun vad(assets: AssetManager): Vad = Vad(
        assetManager = assets,
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = VAD_ASSET,
                minSilenceDuration = PAUSE_SECONDS,
                maxSpeechDuration = MAX_PHRASE_SECONDS,
                windowSize = VAD_WINDOW,
            ),
            sampleRate = SAMPLE_RATE,
            numThreads = 1,
        ),
    )

    /**
     * Скачивает модель в [dir], сообщая проценты в [onProgress]. Файл появляется под своим именем,
     * только когда докачан полностью, поэтому оборванная загрузка просто начнётся заново.
     *
     * Одновременно идёт только одна загрузка: второй вызов ждёт первый и находит файлы готовыми.
     *
     * @throws IOException если ни одно зеркало не ответило.
     */
    @Synchronized
    fun download(dir: File, onProgress: (Int) -> Unit) {
        dir.parentFile?.listFiles()?.filter { it.isDirectory && it.name.startsWith("gigaam") && it != dir }
            ?.forEach { it.deleteRecursively() }
        dir.mkdirs()
        listOf(TOKENS_FILE, MODEL_FILE).forEach { name ->
            val target = File(dir, name)
            if (target.length() == 0L) fetch(name, target, if (name == MODEL_FILE) onProgress else { _ -> })
        }
    }

    private fun fetch(name: String, target: File, onProgress: (Int) -> Unit) {
        var lastError: IOException? = null
        for (mirror in mirrors) {
            try {
                fetchFrom(URI(mirror + name).toURL(), target, onProgress)
                return
            } catch (error: IOException) {
                lastError = error
            }
        }
        throw lastError ?: IOException("Нет адресов для загрузки модели")
    }

    private fun fetchFrom(url: URL, target: File, onProgress: (Int) -> Unit) {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code для $url")
            val total = connection.contentLengthLong
            val part = File(target.parentFile, target.name + ".part")
            val done = connection.inputStream.use { input -> copy(input, part, total, onProgress) }
            val complete = (total <= 0 || done == total) && part.renameTo(target)
            if (!complete) throw IOException("Загрузка $url оборвалась: $done из $total байт")
        } finally {
            connection.disconnect()
        }
    }

    private fun copy(input: InputStream, part: File, total: Long, onProgress: (Int) -> Unit): Long {
        var done = 0L
        part.outputStream().use { output ->
            val buffer = ByteArray(BUFFER_BYTES)
            var read = input.read(buffer)
            while (read >= 0) {
                output.write(buffer, 0, read)
                done += read
                if (total > 0) onProgress((done * PERCENT / total).toInt())
                read = input.read(buffer)
            }
        }
        return done
    }
}

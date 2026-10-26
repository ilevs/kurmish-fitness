package com.kurmish.fitness.ui.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.Vad
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Что сейчас делает распознавание речи. */
internal sealed interface VoiceState {
    /** Микрофон выключен. */
    data object Idle : VoiceState

    /** Первый запуск: модель скачивается, [percent] — сколько готово. */
    data class Downloading(val percent: Int) : VoiceState

    /** Модель загружается в память. */
    data object Preparing : VoiceState

    /** Микрофон включён. */
    data object Listening : VoiceState

    /** Микрофон выключен, распознаётся последняя фраза. */
    data object Finishing : VoiceState

    /** Распознавание не запустилось, [reason] — почему. */
    data class Failed(val reason: VoiceFailure) : VoiceState
}

/** Причина, по которой голосовой ввод не сработал. */
internal enum class VoiceFailure {
    /** Модель не скачалась: нет сети или сервер недоступен. */
    Download,

    /** Микрофон недоступен или занят. */
    Microphone,
}

/**
 * Офлайн-распознавание русской речи моделью GigaAM v3 на движке sherpa-onnx.
 *
 * Звук с микрофона режет на фразы детектор речи Silero VAD (лежит в assets), каждую фразу целиком
 * распознаёт GigaAM и передаёт текст в [onText]. Вся тяжёлая работа идёт в отдельном потоке,
 * [state] обновляется в главном.
 */
internal class OfflineSpeech(private val context: Context, private val onText: (String) -> Unit) {
    /** Текущее состояние для экрана. */
    var state: VoiceState by mutableStateOf(VoiceState.Idle)
        private set

    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var listening = false
    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null

    /** Включает микрофон, при первом запуске сначала скачивает модель. Разрешение RECORD_AUDIO уже выдано. */
    fun start() {
        val busy = state != VoiceState.Idle && state !is VoiceState.Failed
        if (busy || worker.isShutdown) return
        listening = true
        state = VoiceState.Preparing
        worker.execute(::session)
    }

    /** Выключает микрофон; уже сказанное распознаётся до конца. */
    fun stop() {
        if (listening) {
            listening = false
            if (state == VoiceState.Listening) state = VoiceState.Finishing
        }
    }

    /** Освобождает микрофон и модель, когда экран закрыт. */
    fun release() {
        listening = false
        worker.execute {
            recognizer?.release()
            recognizer = null
            vad?.release()
            vad = null
        }
        worker.shutdown()
    }

    private fun session() {
        val dir = File(context.filesDir, VoiceModel.NAME)
        try {
            if (!VoiceModel.isReady(dir)) download(dir)
            val engine = recognizer ?: VoiceModel.recognizer(dir).also { recognizer = it }
            val detector = vad ?: VoiceModel.vad(context.assets).also { vad = it }
            record(engine, detector)
        } catch (ignored: IOException) {
            post(VoiceState.Failed(VoiceFailure.Download))
        }
    }

    private fun download(dir: File) {
        var shown = -1
        post(VoiceState.Downloading(0))
        VoiceModel.download(dir) { percent ->
            if (percent != shown) {
                shown = percent
                post(VoiceState.Downloading(percent))
            }
        }
        post(VoiceState.Preparing)
    }

    private fun record(engine: OfflineRecognizer, detector: Vad) {
        if (!listening) {
            post(VoiceState.Idle)
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val audio = if (granted) openMicrophone() else null
        if (audio == null) {
            post(VoiceState.Failed(VoiceFailure.Microphone))
            return
        }
        post(VoiceState.Listening)
        val chunk = ShortArray(WINDOW)
        try {
            audio.startRecording()
            while (listening) {
                val read = audio.read(chunk, 0, chunk.size)
                if (read > 0) {
                    detector.acceptWaveform(FloatArray(read) { chunk[it] / PCM_SCALE })
                    transcribe(engine, detector)
                }
            }
        } finally {
            audio.stop()
            audio.release()
        }
        detector.flush()
        transcribe(engine, detector)
        detector.reset()
        post(VoiceState.Idle)
    }

    private fun openMicrophone(): AudioRecord? {
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, ENCODING)
        // Запас в несколько секунд: пока распознаётся фраза, микрофон пишет следующую в буфер.
        val buffer = maxOf(minimum, SAMPLE_RATE * BYTES_PER_SAMPLE * BUFFER_SECONDS)
        return try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                ENCODING,
                buffer,
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } catch (ignored: SecurityException) {
            null
        }
    }

    private fun transcribe(engine: OfflineRecognizer, detector: Vad) {
        while (!detector.empty()) {
            val samples = detector.front().samples
            detector.pop()
            val stream = engine.createStream()
            stream.acceptWaveform(samples, SAMPLE_RATE)
            engine.decode(stream)
            val text = engine.getResult(stream).text.trim()
            stream.release()
            if (text.isNotEmpty()) main.post { onText(text) }
        }
    }

    private fun post(next: VoiceState) {
        main.post {
            val stopped = next == VoiceState.Listening && !listening
            state = if (stopped) VoiceState.Finishing else next
        }
    }

    private companion object {
        const val SAMPLE_RATE = VoiceModel.SAMPLE_RATE
        const val WINDOW = VoiceModel.VAD_WINDOW
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2
        const val BUFFER_SECONDS = 10
        const val PCM_SCALE = 32_768f
    }
}

/** Распознавание, привязанное к экрану: при уходе с экрана микрофон и модель освобождаются. */
@Composable
internal fun rememberOfflineSpeech(onText: (String) -> Unit): OfflineSpeech {
    val context = LocalContext.current.applicationContext
    val currentOnText by rememberUpdatedState(onText)
    val speech = remember(context) { OfflineSpeech(context) { currentOnText(it) } }
    DisposableEffect(speech) {
        onDispose { speech.release() }
    }
    return speech
}

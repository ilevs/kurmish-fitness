package com.kurmish.fitness.ui.voice

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

/** Что сейчас делает распознавание речи. */
internal sealed interface VoiceState {
    /** Микрофон выключен. */
    data object Idle : VoiceState

    /** Первый запуск: модель распаковывается из APK в память телефона. */
    data object Preparing : VoiceState

    /** Микрофон включён; [partial] — фраза, которая ещё произносится. */
    data class Listening(val partial: String) : VoiceState

    /** Распознавание не запустилось. */
    data object Failed : VoiceState
}

/**
 * Офлайн-распознавание русской речи встроенной моделью Vosk. Не нужны ни сеть, ни сервисы Google.
 *
 * Модель лежит в assets/model-ru, при первом запуске распаковывается в файлы приложения.
 * Каждая законченная фраза передаётся в [onText], пока не вызван [stop].
 */
internal class OfflineSpeech(private val context: Context, private val onText: (String) -> Unit) :
    RecognitionListener {
    /** Текущее состояние для экрана. */
    var state: VoiceState by mutableStateOf(VoiceState.Idle)
        private set

    private var model: Model? = null
    private var service: SpeechService? = null
    private var released = false

    /** Включает микрофон. Разрешение RECORD_AUDIO должно быть уже выдано. */
    fun start() {
        if (state != VoiceState.Idle && state != VoiceState.Failed) return
        val loaded = model
        if (loaded != null) {
            listen(loaded)
            return
        }
        state = VoiceState.Preparing
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        StorageService.unpack(
            context,
            MODEL_ASSETS,
            MODEL_TARGET,
            { unpacked ->
                if (released) {
                    unpacked.close()
                } else {
                    model = unpacked
                    listen(unpacked)
                }
            },
            { state = VoiceState.Failed },
        )
    }

    /** Выключает микрофон; уже сказанное распознаётся до конца. */
    fun stop() {
        service?.stop()
        service?.shutdown()
        service = null
        if (state != VoiceState.Failed) state = VoiceState.Idle
    }

    /** Освобождает микрофон и модель, когда экран закрыт. */
    fun release() {
        released = true
        service?.shutdown()
        service = null
        model?.close()
        model = null
        state = VoiceState.Idle
    }

    private fun listen(model: Model) {
        state = try {
            val speech = SpeechService(Recognizer(model, SAMPLE_RATE), SAMPLE_RATE)
            service = speech
            if (speech.startListening(this)) VoiceState.Listening("") else VoiceState.Failed
        } catch (ignored: IOException) {
            VoiceState.Failed
        }
    }

    override fun onPartialResult(hypothesis: String?) {
        if (state is VoiceState.Listening) state = VoiceState.Listening(jsonField(hypothesis, "partial"))
    }

    override fun onResult(hypothesis: String?) {
        jsonField(hypothesis, "text").takeIf(String::isNotBlank)?.let(onText)
        if (state is VoiceState.Listening) state = VoiceState.Listening("")
    }

    override fun onFinalResult(hypothesis: String?) {
        jsonField(hypothesis, "text").takeIf(String::isNotBlank)?.let(onText)
    }

    override fun onError(exception: Exception?) {
        service?.shutdown()
        service = null
        state = VoiceState.Failed
    }

    override fun onTimeout() {
        stop()
    }

    private fun jsonField(hypothesis: String?, name: String): String = hypothesis
        ?.let { Json.parseToJsonElement(it).jsonObject[name]?.jsonPrimitive?.content }
        .orEmpty()

    private companion object {
        const val MODEL_ASSETS = "model-ru"
        const val MODEL_TARGET = "model"
        const val SAMPLE_RATE = 16_000f
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

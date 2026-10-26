package com.kurmish.fitness.ui.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Озвучка советов через системный синтез речи. Русский голос работает без сети, если он скачан. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val engine = TextToSpeech(context.applicationContext, this)

    @Volatile
    private var ready = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            engine.language = Locale.forLanguageTag("ru-RU")
            ready = true
        }
    }

    /** Произносит текст, прерывая предыдущую фразу. */
    fun speak(text: String) {
        if (ready) engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    /** Освобождает движок синтеза. */
    fun shutdown() {
        engine.stop()
        engine.shutdown()
    }

    private companion object {
        const val UTTERANCE_ID = "coach"
    }
}

/** Создаёт [Speaker] на время жизни экрана. */
@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember(context) { Speaker(context) }
    DisposableEffect(speaker) {
        onDispose { speaker.shutdown() }
    }
    return speaker
}

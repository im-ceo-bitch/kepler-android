package com.iumrah.beta.ui.advisor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.UUID

/** Local speech fallback for actual selected-language guidance only. No background speech. */
internal class AdvisorSystemSpeech(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var current: String? = null
    private var engine: TextToSpeech? = null
    private var callback: ((Boolean, Boolean) -> Unit)? = null
    var ready by mutableStateOf(false)
        private set
    private val listener = object : UtteranceProgressListener() {
        override fun onStart(id: String?) { main.post { if (!closed && current == id && id != null) callback?.invoke(true, false) } }
        override fun onDone(id: String?) { finish(id, false) }
        @Deprecated("Legacy Android callback") override fun onError(id: String?) { finish(id, true) }
        override fun onError(id: String?, errorCode: Int) { finish(id, true) }
    }
    init {
        engine = TextToSpeech(context.applicationContext) { result ->
            main.post { if (!closed) ready = result == TextToSpeech.SUCCESS && engine?.setOnUtteranceProgressListener(listener) == TextToSpeech.SUCCESS }
        }
    }
    private fun finish(id: String?, failed: Boolean) {
        main.post {
            if (!closed && id != null && current == id) {
                current = null
                val result = callback; callback = null
                result?.invoke(false, failed)
            }
        }
    }
    fun speak(text: String, language: String, onState: (playing: Boolean, failed: Boolean) -> Unit): Boolean {
        stop()
        val tts = engine ?: return false
        if (!ready || closed || text.isBlank() || text.length > TextToSpeech.getMaxSpeechInputLength()) return false
        val locale = Locale.forLanguageTag(language)
        // Do not use a different-language voice or send religious text to a network TTS provider.
        val voice = runCatching { tts.voices.orEmpty().filter { it.locale.language == locale.language && !it.isNetworkConnectionRequired }
            .sortedByDescending { it.locale == locale }.firstOrNull() }.getOrNull() ?: return false
        val id = UUID.randomUUID().toString()
        return runCatching {
            if (tts.setVoice(voice) != TextToSpeech.SUCCESS) return false
            tts.setSpeechRate(.94f)
            current = id; callback = onState
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) { stop(); false } else true
        }.getOrElse { stop(); false }
    }
    fun stop() { current = null; callback = null; runCatching { engine?.stop() } }
    fun close() { closed = true; stop(); engine?.shutdown(); engine = null; ready = false }
}

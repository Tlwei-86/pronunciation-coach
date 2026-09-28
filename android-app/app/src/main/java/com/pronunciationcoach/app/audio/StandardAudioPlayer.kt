package com.pronunciationcoach.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Enhanced production-ready standard audio playback component with robust TextToSpeech handling,
 * state machine safeguards, and clear callback lifecycle.
 */
class StandardAudioPlayer(
    private val context: Context,
    private val onInitListener: ((Boolean) -> Unit)? = null
) : IStandardAudioPlayer {

    private var tts: TextToSpeech? = null

    @Volatile
    override var isInitialized: Boolean = false
        private set

    @Volatile
    override var isPlaying: Boolean = false
        private set

    @Volatile
    private var initFailed: Boolean = false

    private var pendingWord: String? = null
    private var pendingOnStart: (() -> Unit)? = null
    private var pendingOnComplete: (() -> Unit)? = null

    private var activeUtteranceId: String? = null
    private var currentOnStart: (() -> Unit)? = null
    private var currentOnComplete: (() -> Unit)? = null

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                onTtsSuccess()
            } else {
                onTtsFailure()
            }
        }
    }

    private fun onTtsSuccess() {
        val engine = tts ?: return
        val result = engine.setLanguage(Locale.US)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            initFailed = true
            isInitialized = false
            onInitListener?.invoke(false)
            return
        }

        engine.setPitch(1.0f)
        engine.setSpeechRate(0.92f) // slightly slower for instructional clarity
        setupProgressListener()

        isInitialized = true
        initFailed = false
        onInitListener?.invoke(true)

        // Consume any pending request that arrived while TTS was initializing
        val word = pendingWord
        val startCb = pendingOnStart
        val compCb = pendingOnComplete
        pendingWord = null
        pendingOnStart = null
        pendingOnComplete = null

        if (!word.isNullOrEmpty()) {
            playWord(word, startCb, compCb)
        }
    }

    private fun onTtsFailure() {
        isInitialized = false
        initFailed = true
        isPlaying = false
        val comp = pendingOnComplete
        pendingWord = null
        pendingOnStart = null
        pendingOnComplete = null
        comp?.invoke()
        onInitListener?.invoke(false)
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == activeUtteranceId) {
                    isPlaying = true
                    currentOnStart?.invoke()
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == activeUtteranceId) {
                    isPlaying = false
                    currentOnComplete?.invoke()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == activeUtteranceId) {
                    isPlaying = false
                    currentOnComplete?.invoke()
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == activeUtteranceId) {
                    isPlaying = false
                    currentOnComplete?.invoke()
                }
            }
        })
    }

    /**
     * Plays the given standard word or sentence in American English.
     */
    override fun playWord(
        text: String,
        onStart: (() -> Unit)?,
        onComplete: (() -> Unit)?
    ) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            onComplete?.invoke()
            return
        }

        if (!isInitialized) {
            if (initFailed) {
                // Cannot play if TTS initialization previously failed
                isPlaying = false
                onComplete?.invoke()
                return
            }
            pendingWord = cleanText
            pendingOnStart = onStart
            pendingOnComplete = onComplete
            return
        }

        stop()

        val utteranceId = "word_${UUID.randomUUID()}"
        activeUtteranceId = utteranceId
        currentOnStart = onStart
        currentOnComplete = onComplete

        val engine = tts
        if (engine == null) {
            isPlaying = false
            onComplete?.invoke()
            return
        }

        val queueResult = engine.speak(
            cleanText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )

        if (queueResult != TextToSpeech.SUCCESS) {
            isPlaying = false
            currentOnComplete?.invoke()
        }
    }

    /**
     * Immediately stops playback and invokes completion callback to restore UI state.
     */
    override fun stop() {
        val wasPlaying = isPlaying
        isPlaying = false
        activeUtteranceId = null
        try {
            tts?.stop()
        } catch (_: Exception) {}

        if (wasPlaying) {
            val cb = currentOnComplete
            currentOnStart = null
            currentOnComplete = null
            cb?.invoke()
        } else {
            currentOnStart = null
            currentOnComplete = null
        }
    }

    /**
     * Releases TextToSpeech resources.
     */
    override fun release() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
    }
}

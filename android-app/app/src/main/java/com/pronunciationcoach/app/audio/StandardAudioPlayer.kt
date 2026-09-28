package com.pronunciationcoach.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Standard native American English pronunciation player.
 * Uses Android TextToSpeech configured for Locale.US at instructional pace (0.85f speed)
 * with exclusive accessibility audio focus attributes.
 */
class StandardAudioPlayer(
    context: Context,
    private val onInitListener: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener, IStandardAudioPlayer {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var isInitialized = false
    private var initFailed = false
    private var pendingWord: String? = null
    private var pendingOnStart: (() -> Unit)? = null
    private var pendingOnComplete: (() -> Unit)? = null

    private var activeUtteranceId: String? = null
    private var currentOnStart: (() -> Unit)? = null
    private var currentOnComplete: (() -> Unit)? = null

    @Volatile
    override var isPlaying: Boolean = false
        private set

    init {
        setupProgressListener()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ttsEngine = tts
            if (ttsEngine != null) {
                val result = ttsEngine.setLanguage(Locale.US)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Set instructional slow pace for phoneme clarity (0.85f)
                    ttsEngine.setSpeechRate(0.85f)
                    ttsEngine.setPitch(1.0f)

                    // AudioAttributes USAGE_ASSISTANCE_ACCESSIBILITY (Spec Section 4.1)
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    ttsEngine.setAudioAttributes(audioAttributes)

                    isInitialized = true
                    initFailed = false
                    onInitListener?.invoke(true)

                    // Execute any pending play request
                    pendingWord?.let { word ->
                        val start = pendingOnStart
                        val comp = pendingOnComplete
                        pendingWord = null
                        pendingOnStart = null
                        pendingOnComplete = null
                        playWord(word, start, comp)
                    }
                    return
                }
            }
        }
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
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {\n            override fun onStart(utteranceId: String?) {
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
        onStart: (() -> Unit)? = null,
        onComplete: (() -> Unit)? = null
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

        val utteranceId = UUID.randomUUID().toString()
        activeUtteranceId = utteranceId
        currentOnStart = onStart
        currentOnComplete = onComplete

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        val res = tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (res != TextToSpeech.SUCCESS) {
            isPlaying = false
            onComplete?.invoke()
        }
    }

    /**
     * Stops current playback immediately.
     */
    override fun stop() {
        tts?.stop()
        isPlaying = false
    }

    /**
     * Releases TextToSpeech resources.
     */
    override fun release() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        currentOnStart = null
        currentOnComplete = null
    }
}

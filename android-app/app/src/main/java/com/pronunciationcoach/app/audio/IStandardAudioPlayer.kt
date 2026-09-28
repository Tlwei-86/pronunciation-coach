package com.pronunciationcoach.app.audio

/**
 * Interface contract for native standard pronunciation playback.
 */
interface IStandardAudioPlayer {
    val isPlaying: Boolean
    fun playWord(
        text: String,
        onStart: (() -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    )
    fun stop()
    fun release()
}

package com.pronunciationcoach.app.audio

import java.io.File

/**
 * Interface contract for user recorded audio playback engine.
 */
interface IUserAudioPlaybackEngine {
    val isPlaying: Boolean
    fun hasLastRecording(): Boolean
    fun getLastRecordingFile(): File?
    fun saveRecording(
        pcmData: ByteArray,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): File?
    fun playLastRecording(
        onStart: (() -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    )
    fun stopPlayback()
    fun release()
}

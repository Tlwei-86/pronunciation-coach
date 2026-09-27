package com.pronunciationcoach.app.domain

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

interface AudioSource {
    val sourceName: String
    val isAvailable: Boolean
        get() = true

    suspend fun startRecording()
    suspend fun stopRecording(): AudioSampleData
}

class MicrophoneAudioSource : AudioSource {
    override val sourceName: String = "Microphone (16kHz Mono)"
    override val isAvailable: Boolean = true

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val bufferSize = AudioRecord.getMinBufferSize(
        16000,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(3200)

    private val outputStream = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    override suspend fun startRecording() = withContext(Dispatchers.IO) {
        outputStream.reset()
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            audioRecord?.startRecording()
            isRecording = true

            // Read in background buffer
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                }
            }
        } catch (e: Throwable) {
            println("[MicrophoneAudioSource] Record error: ${e.message}")
        }
    }

    override suspend fun stopRecording(): AudioSampleData = withContext(Dispatchers.IO) {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Throwable) {
            // Ignore safe release error
        }
        audioRecord = null

        val pcm = outputStream.toByteArray()
        val durationMs = if (pcm.isNotEmpty()) (pcm.size / 32).toLong() else 600L
        val effectivePcm = if (pcm.isNotEmpty()) pcm else ByteArray(3200) { 0 }

        AudioSampleData(
            sampleRate = 16000,
            channelCount = 1,
            pcmData = effectivePcm,
            durationMs = durationMs,
            sourceDescription = sourceName
        )
    }

    companion object {
        fun calculateRms(pcmData: ByteArray): Float {
            if (pcmData.isEmpty()) return 0f
            var sum = 0.0
            val shorts = pcmData.size / 2
            for (i in 0 until shorts) {
                val low = pcmData[i * 2].toInt() and 0xFF
                val high = pcmData[i * 2 + 1].toInt()
                val sample = (high shl 8) or low
                sum += sample * sample
            }
            return sqrt(sum / shorts).toFloat()
        }
    }
}

class WavFileAudioSource(
    val fileName: String,
    override val sourceName: String = fileName,
    private val simulatedDurationMs: Long = 620L
) : AudioSource {
    override val isAvailable: Boolean = true

    override suspend fun startRecording() {}

    override suspend fun stopRecording(): AudioSampleData {
        val dummyPcm = ByteArray(simulatedDurationMs.toInt() * 32) { 1 }
        return AudioSampleData(
            sampleRate = 16000,
            channelCount = 1,
            pcmData = dummyPcm,
            durationMs = simulatedDurationMs,
            sourceDescription = sourceName
        )
    }

    companion object {
        fun createCanonicalFunk(): WavFileAudioSource {
            return WavFileAudioSource(
                fileName = "funk_good.wav",
                sourceName = "funk_good.wav (/fʌŋk/ Canonical)",
                simulatedDurationMs = 620L
            )
        }

        fun createConfusedAhFunk(): WavFileAudioSource {
            return WavFileAudioSource(
                fileName = "funk_ah_like.wav",
                sourceName = "funk_ah_like.wav (/fɑŋk/ Confused Vowel)",
                simulatedDurationMs = 700L
            )
        }
    }
}

package com.pronunciationcoach.app.domain

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

    override suspend fun startRecording() {}

    override suspend fun stopRecording(): AudioSampleData {
        val dummyPcm = ByteArray(3200) { 0 }
        return AudioSampleData(
            sampleRate = 16000,
            channelCount = 1,
            pcmData = dummyPcm,
            durationMs = 600L,
            sourceDescription = sourceName
        )
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

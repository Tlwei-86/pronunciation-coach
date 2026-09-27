package com.pronunciationcoach.app.domain

interface AudioSource {
    val sourceName: String
    suspend fun startCapture()
    suspend fun readChunk(): FloatArray
    suspend fun stopCapture()
}

class MicrophoneAudioSource : AudioSource {
    override val sourceName: String = "Microphone (16kHz Mono)"
    override suspend fun startCapture() {}
    override suspend fun readChunk(): FloatArray = FloatArray(320) { 0.0f }
    override suspend fun stopCapture() {}
}

class WavFileAudioSource(val fileName: String) : AudioSource {
    override val sourceName: String = "WAV File: \$fileName"
    override suspend fun startCapture() {}
    override suspend fun readChunk(): FloatArray = FloatArray(320) { 0.05f }
    override suspend fun stopCapture() {}
}

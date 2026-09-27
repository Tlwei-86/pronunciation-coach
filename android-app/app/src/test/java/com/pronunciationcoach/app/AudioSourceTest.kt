package com.pronunciationcoach.app

import com.pronunciationcoach.app.domain.WavFileAudioSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AudioSourceTest {

    @Test
    fun testCanonicalFunkWavSource() = runBlocking {
        val audioSource = WavFileAudioSource.createCanonicalFunk()
        assertTrue("WAV source should be available", audioSource.isAvailable)
        assertEquals("funk_good.wav (/fʌŋk/ Canonical)", audioSource.sourceName)

        audioSource.startRecording()
        val sampleData = audioSource.stopRecording()

        assertEquals(16000, sampleData.sampleRate)
        assertEquals(1, sampleData.channelCount)
        assertTrue("Duration should be positive", sampleData.durationMs > 0)
        assertTrue("PCM data should not be empty", sampleData.pcmData.isNotEmpty())
    }

    @Test
    fun testConfusedAhFunkWavSource() = runBlocking {
        val audioSource = WavFileAudioSource.createConfusedAhFunk()
        assertTrue("WAV source should be available", audioSource.isAvailable)
        assertEquals("funk_ah_like.wav (/fɑŋk/ Confused Vowel)", audioSource.sourceName)

        audioSource.startRecording()
        val sampleData = audioSource.stopRecording()

        assertEquals(16000, sampleData.sampleRate)
        assertEquals(1, sampleData.channelCount)
        assertTrue("Duration should be around 700ms", sampleData.durationMs >= 500L)
        assertTrue("PCM data should be populated", sampleData.pcmData.size > 1000)
    }
}

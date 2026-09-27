package com.pronunciationcoach.app

import com.pronunciationcoach.app.core.PronunciationCoreBridge
import com.pronunciationcoach.app.domain.MicrophoneAudioSource
import com.pronunciationcoach.app.domain.WavFileAudioSource
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DomainPipelineTest {
    @Test
    fun testAudioSourceAbstraction() {
        val mic = MicrophoneAudioSource()
        assertEquals("Microphone (16kHz Mono)", mic.sourceName)

        val wav = WavFileAudioSource("funk_good.wav")
        assertTrue(wav.sourceName.contains("funk_good.wav"))
    }

    @Test
    fun testFallbackScoringOutputFormat() {
        val jsonStr = PronunciationCoreBridge.safeScoreFunk(0.92f, 0.05f, 0.45f, 0.10f)
        val json = JSONObject(jsonStr)
        assertEquals("funk", json.getString("target_word"))
        assertTrue(json.getInt("overall_score") in 0..100)
        assertTrue(json.has("phoneme_scores"))
        assertTrue(json.has("guidance"))
    }
}

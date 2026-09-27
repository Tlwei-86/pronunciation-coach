package com.pronunciationcoach.app.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class AcousticFeatureExtractorTest {

    private fun generateSinePcm(
        freqHz: Double,
        durationSec: Double,
        sampleRate: Int = 16000,
        amplitude: Double = 16000.0
    ): ByteArray {
        val totalSamples = (sampleRate * durationSec).toInt()
        val pcm = ByteArray(totalSamples * 2)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val sample = (sin(2.0 * PI * freqHz * t) * amplitude).toInt().coerceIn(-32768, 32767).toShort()
            pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    @Test
    fun testFormantExtractionVowel() {
        val sampleRate = 16000
        val durationSec = 0.3
        val totalSamples = (sampleRate * durationSec).toInt()
        val pcm = ByteArray(totalSamples * 2)

        // Synthesize vowel with simulated formants F1=600Hz, F2=1300Hz, F3=2500Hz
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val s1 = sin(2.0 * PI * 600.0 * t) * 8000.0
            val s2 = sin(2.0 * PI * 1300.0 * t) * 6000.0
            val s3 = sin(2.0 * PI * 2500.0 * t) * 4000.0
            val total = (s1 + s2 + s3).toInt().coerceIn(-32768, 32767).toShort()
            pcm[i * 2] = (total.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((total.toInt() shr 8) and 0xFF).toByte()
        }

        val formants = AcousticFeatureExtractor.extractFormants(pcm)
        assertTrue("F1 should be in range 400..900Hz (got ${formants.f1})", formants.f1 in 400f..900f)
        assertTrue("F2 should be in range 1000..1800Hz (got ${formants.f2})", formants.f2 in 1000f..1800f)
        assertTrue("Formant clarity should be positive", formants.clarityScore > 0f)

        // Verify categorical analysis for vowel
        val cat = AcousticFeatureExtractor.analyzePhonemeCategory(pcm, "ʌ")
        assertEquals("Vowel", cat.category)
        assertNotNull(cat.formants)
    }

    @Test
    fun testFricativeSpectralCentroid() {
        val sampleRate = 16000
        val durationSec = 0.25
        val totalSamples = (sampleRate * durationSec).toInt()
        val pcm = ByteArray(totalSamples * 2)

        // High frequency sibilance (4500Hz to 7000Hz)
        val rng = Random(42)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val highSine = sin(2.0 * PI * 5500.0 * t) * 10000.0
            val noise = (rng.nextDouble() - 0.5) * 6000.0
            val total = (highSine + noise).toInt().coerceIn(-32768, 32767).toShort()
            pcm[i * 2] = (total.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((total.toInt() shr 8) and 0xFF).toByte()
        }

        val fricative = AcousticFeatureExtractor.extractFricativeFeatures(pcm, "s")
        assertTrue("Fricative centroid should be high (>2500Hz, got ${fricative.spectralCentroid})", fricative.spectralCentroid > 2500f)
        assertTrue("Fricative HF ratio should be prominent (>0.30, got ${fricative.highFrequencyRatio})", fricative.highFrequencyRatio > 0.30f)

        val cat = AcousticFeatureExtractor.analyzePhonemeCategory(pcm, "s")
        assertEquals("Fricative", cat.category)
        assertNotNull(cat.fricative)
    }

    @Test
    fun testPlosiveBurstAndGap() {
        val sampleRate = 16000
        val totalSamples = (sampleRate * 0.25).toInt() // 250ms
        val pcm = ByteArray(totalSamples * 2)

        // 100ms silence followed by an impulsive 30ms burst
        val silenceSamples = (sampleRate * 0.10).toInt()
        val burstSamples = (sampleRate * 0.03).toInt()

        for (i in silenceSamples until (silenceSamples + burstSamples)) {
            val t = i.toDouble() / sampleRate
            val burst = (sin(2.0 * PI * 1800.0 * t) * 20000.0).toInt().coerceIn(-32768, 32767).toShort()
            pcm[i * 2] = (burst.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((burst.toInt() shr 8) and 0xFF).toByte()
        }

        val plosive = AcousticFeatureExtractor.extractPlosiveFeatures(pcm)
        assertTrue("Plosive burst ratio should detect sudden spike (>1.5)", plosive.burstSpikeRatio > 1.5f)
        assertTrue("Plosive silence gap should be detected (>30ms)", plosive.silenceGapMs >= 30f)

        val cat = AcousticFeatureExtractor.analyzePhonemeCategory(pcm, "p")
        assertEquals("Plosive", cat.category)
        assertNotNull(cat.plosive)
    }

    @Test
    fun testNasalMurmur() {
        val pcm = generateSinePcm(freqHz = 280.0, durationSec = 0.3)

        val nasal = AcousticFeatureExtractor.extractNasalFeatures(pcm)
        assertTrue("Nasal murmur energy should be non-zero", nasal.nasalMurmurEnergy > 0f)
        assertTrue("Anti-formant ratio should be non-zero", nasal.antiFormantRatio > 0f)

        val cat = AcousticFeatureExtractor.analyzePhonemeCategory(pcm, "m")
        assertEquals("Nasal", cat.category)
        assertNotNull(cat.nasal)
    }

    @Test
    fun testApproximantF3Drop() {
        val sampleRate = 16000
        val durationSec = 0.3
        val totalSamples = (sampleRate * durationSec).toInt()
        val pcm = ByteArray(totalSamples * 2)

        // Rhotic /r/ characteristic: lowered F3 (<2100Hz)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val s1 = sin(2.0 * PI * 500.0 * t) * 8000.0
            val s2 = sin(2.0 * PI * 1200.0 * t) * 7000.0
            val s3 = sin(2.0 * PI * 1900.0 * t) * 6000.0 // Low F3
            val total = (s1 + s2 + s3).toInt().coerceIn(-32768, 32767).toShort()
            pcm[i * 2] = (total.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((total.toInt() shr 8) and 0xFF).toByte()
        }

        val approximant = AcousticFeatureExtractor.extractApproximantFeatures(pcm, "r")
        assertTrue("Should compute score for rhotic approximant", approximant.score > 0f)

        val cat = AcousticFeatureExtractor.analyzePhonemeCategory(pcm, "r")
        assertEquals("Approximant", cat.category)
        assertNotNull(cat.approximant)
    }

    @Test
    fun testBackwardCompatibilityAcousticAcuity() {
        val pcm = generateSinePcm(freqHz = 600.0, durationSec = 0.2)
        val acuity = AcousticFeatureExtractor.analyzePcmBuffer(pcm)

        assertTrue(acuity.targetProb in 0.0f..1.0f)
        assertTrue(acuity.confusionProb in 0.0f..1.0f)
        assertTrue(acuity.f1Hz > 0f)
        assertTrue(acuity.f2Hz > 0f)
    }
}

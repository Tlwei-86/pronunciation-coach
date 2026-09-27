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

        val features = AcousticFeatureExtractor.extractCategoryFeatures(
            pcmData = pcm,
            sampleRate = sampleRate,
            phonemeType = PhonemeType.VOWEL
        )

        assertNotNull(features.formants)
        val formants = features.formants!!
        assertTrue("F1 should be near 600Hz (got ${formants.f1Hz})", formants.f1Hz in 400f..800f)
        assertTrue("F2 should be near 1300Hz (got ${formants.f2Hz})", formants.f2Hz in 1100f..1600f)
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

        val features = AcousticFeatureExtractor.extractCategoryFeatures(
            pcmData = pcm,
            sampleRate = sampleRate,
            phonemeType = PhonemeType.FRICATIVE
        )

        assertNotNull(features.fricativeCentroid)
        assertNotNull(features.fricativeHfRatio)
        assertTrue(
            "Fricative centroid should be high (>2500Hz, got ${features.fricativeCentroid})",
            features.fricativeCentroid!! > 2500f
        )
        assertTrue(
            "Fricative HF ratio should be prominent (>0.30, got ${features.fricativeHfRatio})",
            features.fricativeHfRatio!! > 0.30f
        )
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

        val features = AcousticFeatureExtractor.extractCategoryFeatures(
            pcmData = pcm,
            sampleRate = sampleRate,
            phonemeType = PhonemeType.PLOSIVE
        )

        assertNotNull(features.plosiveBurstRatio)
        assertNotNull(features.plosiveSilenceGapMs)
        assertTrue("Plosive burst ratio should detect sudden spike (>1.5)", features.plosiveBurstRatio!! > 1.5f)
        assertTrue("Plosive silence gap should be detected (>30ms)", features.plosiveSilenceGapMs!! >= 30f)
    }

    @Test
    fun testNasalMurmur() {
        val pcm = generateSinePcm(freqHz = 280.0, durationSec = 0.3)

        val features = AcousticFeatureExtractor.extractCategoryFeatures(
            pcmData = pcm,
            sampleRate = 16000,
            phonemeType = PhonemeType.NASAL
        )

        assertNotNull(features.nasalMurmurRatio)
        assertTrue("Nasal murmur ratio should be high for 280Hz tone (>1.0)", features.nasalMurmurRatio!! > 1.0f)
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

        val features = AcousticFeatureExtractor.extractCategoryFeatures(
            pcmData = pcm,
            sampleRate = sampleRate,
            phonemeType = PhonemeType.APPROXIMANT
        )

        assertNotNull(features.approximantF3Drop)
        assertTrue("Should detect rhotic F3 plunge for F3=1900Hz", features.approximantF3Drop == true)
    }

    @Test
    fun testBackwardCompatibilityAcousticAcuity() {
        val pcm = generateSinePcm(freqHz = 600.0, durationSec = 0.2)
        val acuity = AcousticFeatureExtractor.analyzePcmBuffer(pcm)

        assertTrue(acuity.targetScore in 0..100)
        assertTrue(acuity.confusionScore in 0..100)
        assertTrue(acuity.f1EstimateHz > 0f)
        assertTrue(acuity.f2EstimateHz > 0f)
    }
}

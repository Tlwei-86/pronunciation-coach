package com.pronunciationcoach.app

import com.pronunciationcoach.app.audio.TongueEstimator
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class TongueEstimatorTest {

    @Test
    fun testArticulatoryFormulaOptimalMidCentral() {
        // Ideal /ʌ/ formants: F1 ~ 600Hz, F2 ~ 1250Hz, jawOpen ~ 0.22, lipRoundness ~ 0.12
        val eval = TongueEstimator.evaluateFromFormants(
            f1 = 600f,
            f2 = 1250f,
            jawOpen = 0.22f,
            lipRoundness = 0.12f
        )

        // height = 1.0f - (((600 - 250)/650)*0.65 + 0.22*0.35)
        // (350/650)*0.65 = 0.35, 0.22*0.35 = 0.077 -> total = 0.427 -> height ≈ 0.573
        assertTrue("Height should be in mid-central range [0.45, 0.65], got ${eval.height}", eval.height in 0.45f..0.65f)

        // backness = (((1250 - 800)/1400)*0.70 + (1.0 - 0.12)*0.30)
        // (450/1400)*0.70 = 0.225 + 0.264 = 0.489
        assertTrue("Backness should be in central range [0.40, 0.60], got ${eval.backness}", eval.backness in 0.40f..0.60f)

        assertTrue("Score for canonical /ʌ/ should be high (>= 80), got ${eval.score}", eval.score >= 80)
        assertTrue(eval.guidance.contains("舌位居中") || eval.guidance.contains("适宜"))
    }

    @Test
    fun testArticulatoryFormulaLowBackShift() {
        // Over-opened /ɑ/-like formants: F1 ~ 850Hz, F2 ~ 1050Hz, jawOpen ~ 0.55, lipRoundness ~ 0.18
        val eval = TongueEstimator.evaluateFromFormants(
            f1 = 850f,
            f2 = 1050f,
            jawOpen = 0.55f,
            lipRoundness = 0.18f
        )

        assertTrue("Height for /ɑ/ should be low (< 0.35), got ${eval.height}", eval.height < 0.35f)
        assertTrue("Score should be degraded (< 75), got ${eval.score}", eval.score < 75)
        assertTrue(eval.guidance.contains("低") || eval.guidance.contains("/ɑ/"))
    }

    @Test
    fun testSynthesizedPcmFormantTracking() {
        // Generate 16kHz sine wave sum with F1=600Hz and F2=1250Hz
        val sampleRate = 16000
        val durationMs = 200
        val numSamples = sampleRate * durationMs / 1000
        val pcm = ByteArray(numSamples * 2)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val s1 = 0.5 * sin(2.0 * PI * 600.0 * t)
            val s2 = 0.3 * sin(2.0 * PI * 1250.0 * t)
            val sampleVal = ((s1 + s2) * 32767.0).toInt().coerceIn(-32768, 32767)
            pcm[i * 2] = (sampleVal and 0xFF).toByte()
            pcm[i * 2 + 1] = ((sampleVal shr 8) and 0xFF).toByte()
        }

        val (f1, f2) = TongueEstimator.estimateFormants(pcm, 16000f)
        assertTrue("Estimated F1 should be near 600Hz, got $f1", f1 in 450f..750f)
        assertTrue("Estimated F2 should be near 1250Hz, got $f2", f2 in 1000f..1500f)

        val eval = TongueEstimator.evaluate(pcm, jawOpen = 0.25f, lipRoundness = 0.12f)
        assertNotNull(eval)
        assertTrue(eval.score > 70)
    }
}

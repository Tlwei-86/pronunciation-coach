package com.pronunciationcoach.app.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

data class AcousticAcuity(
    val targetProb: Float,
    val confusionProb: Float,
    val detectedPhoneme: String,
    val dominantFrequency: Float,
    val spectralTilt: Float,
    val vowelQuality: String
)

object AcousticFeatureExtractor {
    // 16kHz sample rate standard
    private const val SAMPLE_RATE = 16000f

    /**
     * Extracts spectral formant and vowel features from 16-bit PCM audio buffer.
     * Differentiates mid-central vowel /ʌ/ from low-back open vowel /ɑ/.
     */
    fun analyzePcmBuffer(pcmData: ByteArray): AcousticAcuity {
        if (pcmData.size < 640) {
            return AcousticAcuity(0.10f, 0.50f, "/?/", 0f, 0f, "Insufficient Audio")
        }

        // 1. Convert byte array to float samples [-1.0, 1.0]
        val numSamples = pcmData.size / 2
        val samples = FloatArray(numSamples)
        var maxAmplitude = 0f
        for (i in 0 until numSamples) {
            val low = pcmData[i * 2].toInt() and 0xFF
            val high = pcmData[i * 2 + 1].toInt()
            val sampleInt = (high shl 8) or low
            val sampleFloat = sampleInt / 32768.0f
            samples[i] = sampleFloat
            val absVal = abs(sampleFloat)
            if (absVal > maxAmplitude) maxAmplitude = absVal
        }

        // Silent audio check
        if (maxAmplitude < 0.03f) {
            return AcousticAcuity(0.15f, 0.40f, "/silence/", 0f, 0f, "Voice Too Low")
        }

        // 2. Perform Short-Time Spectral Energy Distribution (Formant Band Analysis)
        // Band 1 (Low Formant / F1 candidate): 400Hz - 700Hz (Characteristic of /ʌ/)
        // Band 2 (High F1 / Open Throat candidate): 750Hz - 1100Hz (Characteristic of /ɑ/ and broad vowels)
        // Band 3 (High Frequencies / Fricative / Consonants): 2000Hz - 4500Hz
        val nFft = 512
        val step = 256
        var band1Energy = 0.0
        var band2Energy = 0.0
        var bandHighEnergy = 0.0
        var totalFrames = 0

        var pos = 0
        while (pos + nFft <= numSamples) {
            // Apply Hann Window & calculate discrete band energy
            var eBand1 = 0.0
            var eBand2 = 0.0
            var eHigh = 0.0

            for (k in 1..nFft / 2) {
                val freq = k * (SAMPLE_RATE / nFft)
                var real = 0.0
                var imag = 0.0
                for (t in 0 until nFft) {
                    val w = 0.5 * (1.0 - cos(2.0 * Math.PI * t / (nFft - 1)))
                    val s = samples[pos + t] * w
                    val angle = 2.0 * Math.PI * k * t / nFft
                    real += s * cos(angle)
                    imag -= s * Math.sin(angle)
                }
                val power = real * real + imag * imag
                when {
                    freq in 450.0..720.0 -> eBand1 += power
                    freq in 750.0..1150.0 -> eBand2 += power
                    freq in 2000.0..4000.0 -> eHigh += power
                }
            }
            band1Energy += eBand1
            band2Energy += eBand2
            bandHighEnergy += eHigh
            totalFrames++
            pos += step
        }

        if (totalFrames == 0) {
            return AcousticAcuity(0.20f, 0.50f, "/?/", 0f, 0f, "Frame Analysis Error")
        }

        val avgB1 = (band1Energy / totalFrames).toFloat()
        val avgB2 = (band2Energy / totalFrames).toFloat()

        // 3. Formant Ratio Decision:
        // Proper /ʌ/ maintains focused energy in F1 around 600Hz (B1 > B2)
        // Over-opened /ɑ/ shifts energy higher into 800-1000Hz (B2 >= B1)
        val ratio = if (avgB1 + avgB2 > 0) avgB1 / (avgB1 + avgB2) else 0.5f

        val targetProb: Float
        val confusionProb: Float
        val detectedPhoneme: String
        val vowelQuality: String

        if (ratio >= 0.58f) {
            // High confidence in /ʌ/
            targetProb = (0.75f + (ratio - 0.58f) * 0.5f).coerceIn(0.80f, 0.95f)
            confusionProb = (1.0f - targetProb).coerceAtLeast(0.05f)
            detectedPhoneme = "/ʌ/"
            vowelQuality = "Canonical /ʌ/ (Mid-Central Balanced)"
        } else if (ratio in 0.42f..0.58f) {
            // Borderline / Moderate deviation
            targetProb = (0.50f + (ratio - 0.42f) * 1.5f).coerceIn(0.50f, 0.74f)
            confusionProb = 0.38f
            detectedPhoneme = "/ə/"
            vowelQuality = "Slight Deviation (/ə/ or Neutral)"
        } else {
            // Heavy shift towards /ɑ/ (over-opened jaw)
            targetProb = (0.25f + ratio * 0.4f).coerceIn(0.20f, 0.48f)
            confusionProb = (0.85f - ratio * 0.5f).coerceIn(0.60f, 0.90f)
            detectedPhoneme = "/ɑ/"
            vowelQuality = "Noticeable /ɑ/ Shift (Over-opened Throat)"
        }

        return AcousticAcuity(
            targetProb = targetProb,
            confusionProb = confusionProb,
            detectedPhoneme = detectedPhoneme,
            dominantFrequency = if (ratio >= 0.58f) 620f else 880f,
            spectralTilt = ratio,
            vowelQuality = vowelQuality
        )
    }
}

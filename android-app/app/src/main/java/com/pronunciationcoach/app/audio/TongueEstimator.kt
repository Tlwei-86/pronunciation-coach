package com.pronunciationcoach.app.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tongue position evaluation data representing articulatory posture in vowel quadrilateral space.
 */
data class TongueEvaluation(
    val height: Float,
    val backness: Float,
    val score: Int,
    val guidance: String
)

/**
 * Acoustic-to-Articulatory Tongue Inversion & Formant Tracker.
 *
 * Utilizes LPC (Linear Predictive Coding) with Levinson-Durbin autocorrelation recursion
 * to estimate primary vocal tract resonances (F1 & F2), and inverts formant frequencies
 * combined with visual jaw/lip cues into tongue height and backness coordinates.
 */
object TongueEstimator {

    private const val DEFAULT_SAMPLE_RATE = 16000f
    private const val LPC_ORDER = 12

    /**
     * Estimates primary formant resonances F1 (first formant) and F2 (second formant) in Hz
     * from 16-bit mono PCM audio data.
     */
    fun estimateFormants(
        pcmData: ByteArray,
        sampleRate: Float = DEFAULT_SAMPLE_RATE
    ): Pair<Float, Float> {
        if (pcmData.size < 640) {
            // Insufficient samples, return canonical /ʌ/ baseline defaults
            return Pair(600f, 1250f)
        }

        val numSamples = pcmData.size / 2
        val rawSamples = FloatArray(numSamples)
        var maxAmp = 0f
        for (i in 0 until numSamples) {
            val low = pcmData[i * 2].toInt() and 0xFF
            val high = pcmData[i * 2 + 1].toInt()
            val sampleInt = (high shl 8) or low
            val sampleFloat = sampleInt / 32768.0f
            rawSamples[i] = sampleFloat
            val a = abs(sampleFloat)
            if (a > maxAmp) maxAmp = a
        }

        if (maxAmp < 0.02f) {
            // Near silence
            return Pair(600f, 1250f)
        }

        // Apply pre-emphasis filter to boost higher frequencies: y[n] = x[n] - 0.97 * x[n-1]
        val emphasized = FloatArray(numSamples)
        emphasized[0] = rawSamples[0]
        for (i in 1 until numSamples) {
            emphasized[i] = rawSamples[i] - 0.97f * rawSamples[i - 1]
        }

        // Select the center high-energy segment (up to 1024 samples)
        val frameSize = minOf(1024, numSamples)
        val startOffset = max(0, (numSamples - frameSize) / 2)
        val windowed = FloatArray(frameSize)
        for (i in 0 until frameSize) {
            // Hann window
            val w = 0.5f * (1.0f - cos(2.0f * PI.toFloat() * i / (frameSize - 1)))
            windowed[i] = emphasized[startOffset + i] * w
        }

        // Compute Autocorrelation R[0..p]
        val r = DoubleArray(LPC_ORDER + 1)
        for (k in 0..LPC_ORDER) {
            var sum = 0.0
            for (n in 0 until frameSize - k) {
                sum += windowed[n] * windowed[n + k]
            }
            r[k] = sum
        }

        if (r[0] <= 1e-9) {
            return Pair(600f, 1250f)
        }

        // Levinson-Durbin Recursion to solve for LPC coefficients
        val aCoeffs = DoubleArray(LPC_ORDER + 1)
        val aPrev = DoubleArray(LPC_ORDER + 1)
        aCoeffs[0] = 1.0
        var error = r[0]

        for (i in 1..LPC_ORDER) {
            var lambda = 0.0
            for (j in 0 until i) {
                lambda += aCoeffs[j] * r[i - j]
            }
            val k = -lambda / error
            aPrev[0] = 1.0
            for (j in 1 until i) {
                aPrev[j] = aCoeffs[j]
            }
            for (j in 1 until i) {
                aCoeffs[j] = aPrev[j] + k * aPrev[i - j]
            }
            aCoeffs[i] = k
            error *= (1.0 - k * k)
            if (error <= 0.0) break
        }

        // Sample LPC frequency spectrum from 200 Hz to 3000 Hz in 25 Hz steps
        val minFreq = 200f
        val maxFreq = 3000f
        val stepHz = 25f
        val numSteps = ((maxFreq - minFreq) / stepHz).toInt()
        val powerSpectrum = FloatArray(numSteps)

        for (step in 0 until numSteps) {
            val freq = minFreq + step * stepHz
            val omega = (2.0 * PI * freq / sampleRate)
            var real = 1.0
            var imag = 0.0
            for (k in 1..LPC_ORDER) {
                val angle = k * omega
                real += aCoeffs[k] * cos(angle)
                imag -= aCoeffs[k] * sin(angle)
            }
            val magSquared = real * real + imag * imag
            // LPC spectral envelope is 1 / |A(e^jw)|^2
            powerSpectrum[step] = (1.0 / max(1e-12, magSquared)).toFloat()
        }

        // Find Formant 1: peak in 250 Hz .. 1050 Hz
        var f1 = 600f
        var maxP1 = 0f
        val f1MinIdx = ((250f - minFreq) / stepHz).toInt().coerceAtLeast(1)
        val f1MaxIdx = ((1050f - minFreq) / stepHz).toInt().coerceAtMost(numSteps - 2)

        for (i in f1MinIdx..f1MaxIdx) {
            if (powerSpectrum[i] > powerSpectrum[i - 1] && powerSpectrum[i] > powerSpectrum[i + 1]) {
                if (powerSpectrum[i] > maxP1) {
                    maxP1 = powerSpectrum[i]
                    f1 = minFreq + i * stepHz
                }
            }
        }

        // Find Formant 2: peak in max(f1 + 200Hz, 850Hz) .. 2500 Hz
        var f2 = 1250f
        var maxP2 = 0f
        val f2StartHz = max(f1 + 200f, 850f)
        val f2MinIdx = ((f2StartHz - minFreq) / stepHz).toInt().coerceAtLeast(1)
        val f2MaxIdx = ((2500f - minFreq) / stepHz).toInt().coerceAtMost(numSteps - 2)

        for (i in f2MinIdx..f2MaxIdx) {
            if (powerSpectrum[i] > powerSpectrum[i - 1] && powerSpectrum[i] > powerSpectrum[i + 1]) {
                if (powerSpectrum[i] > maxP2) {
                    maxP2 = powerSpectrum[i]
                    f2 = minFreq + i * stepHz
                }
            }
        }

        // Fallback smoothing if no sharp second peak detected
        if (maxP2 <= 0f) {
            f2 = (f1 * 2.1f).coerceIn(1100f, 1500f)
        }

        return Pair(f1, f2)
    }

    /**
     * Evaluates tongue height and backness using the acoustic-articulatory inversion formulas:
     *   height = 1.0f - (((f1 - 250f)/650f)*0.65f + jawOpen*0.35f).coerceIn(0f, 1f)
     *   backness = (((f2 - 800f)/1400f)*0.70f + (1.0f - lipRoundness)*0.30f).coerceIn(0f, 1f)
     */
    fun evaluate(
        pcmData: ByteArray,
        jawOpen: Float,
        lipRoundness: Float
    ): TongueEvaluation {
        val (f1, f2) = estimateFormants(pcmData)
        return evaluateFromFormants(f1, f2, jawOpen, lipRoundness)
    }

    /**
     * Direct mathematical evaluation from known formants and visual metrics.
     */
    fun evaluateFromFormants(
        f1: Float,
        f2: Float,
        jawOpen: Float,
        lipRoundness: Float
    ): TongueEvaluation {
        val height = 1.0f - (((f1 - 250f) / 650f) * 0.65f + jawOpen * 0.35f).coerceIn(0f, 1f)
        val backness = (((f2 - 800f) / 1400f) * 0.70f + (1.0f - lipRoundness) * 0.30f).coerceIn(0f, 1f)

        // Target for central unrounded vowel /ʌ/
        val targetHeight = 0.54f
        val targetBackness = 0.46f

        val distH = abs(height - targetHeight)
        val distB = abs(backness - targetBackness)
        val euclidean = sqrt(distH * distH + distB * distB)

        val score = ((1.0f - euclidean * 1.55f) * 100f).toInt().coerceIn(30, 98)

        val guidance = when {
            height < 0.35f -> "舌位过低平陷 (偏向 /ɑ/)：请微收下巴，将舌体中部向上微抬至口腔中部。"
            height > 0.72f -> "舌位偏高：舌面顶得过高，请放松舌肌与下颌，保持适度开度。"
            backness > 0.68f -> "舌位偏前 (偏向 /ɛ/ 或 /æ/)：请微向后收拢舌体。"
            backness < 0.32f -> "舌位过度后缩：舌根后压过多，请居中放松发音。"
            else -> "舌位居中高度适宜：完美呈现央元音 /ʌ/ 舌位结构。"
        }

        return TongueEvaluation(
            height = height,
            backness = backness,
            score = score,
            guidance = guidance
        )
    }
}

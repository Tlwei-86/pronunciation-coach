package com.pronunciationcoach.app.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class AcousticAcuity(
    val targetProb: Float,
    val confusionProb: Float,
    val detectedPhoneme: String,
    val dominantFrequency: Float,
    val spectralTilt: Float,
    val vowelQuality: String,
    val f1Hz: Float = 0f,
    val f2Hz: Float = 0f,
    val f3Hz: Float = 0f
)

/**
 * Formant estimation result for vowels and approximants.
 */
data class FormantEstimation(
    val f1: Float,
    val f2: Float,
    val f3: Float,
    val clarityScore: Float
)

/**
 * Acoustic features for fricatives (/s, z, f, v, θ, ð, ʃ, ʒ, h/).
 */
data class FricativeFeatures(
    val spectralCentroid: Float,
    val highFrequencyRatio: Float,
    val isSibilant: Boolean,
    val score: Float
)

/**
 * Acoustic features for plosives (/p, b, t, d, k, g/).
 */
data class PlosiveFeatures(
    val silenceGapMs: Float,
    val burstSpikeRatio: Float,
    val votMs: Float,
    val score: Float
)

/**
 * Acoustic features for nasals (/m, n, ŋ/).
 */
data class NasalFeatures(
    val nasalMurmurEnergy: Float,
    val antiFormantRatio: Float,
    val score: Float
)

/**
 * Acoustic features for approximants (/l, r, w, j/).
 */
data class ApproximantFeatures(
    val f3DropHz: Float,
    val lateralRatio: Float,
    val score: Float
)

/**
 * Unified phoneme acoustic analysis result covering all 44 phonemes.
 */
data class PhonemeAcousticFeatures(
    val phoneme: String,
    val category: String,
    val score: Float,
    val formants: FormantEstimation? = null,
    val fricative: FricativeFeatures? = null,
    val plosive: PlosiveFeatures? = null,
    val nasal: NasalFeatures? = null,
    val approximant: ApproximantFeatures? = null,
    val explanation: String = ""
)

object AcousticFeatureExtractor {
    private const val SAMPLE_RATE = 16000f

    /**
     * Extracts spectral formant and vowel features from 16-bit PCM audio buffer.
     * Differentiates mid-central vowel /ʌ/ from low-back open vowel /ɑ/.
     * Also tracks F1, F2, F3.
     */
    fun analyzePcmBuffer(pcmData: ByteArray): AcousticAcuity {
        if (pcmData.size < 640) {
            return AcousticAcuity(0.10f, 0.50f, "/?/", 0f, 0f, "Insufficient Audio")
        }

        val samples = decodePcmToFloat(pcmData)
        var maxAmplitude = 0f
        for (s in samples) {
            val a = abs(s)
            if (a > maxAmplitude) maxAmplitude = a
        }

        // Silent audio check
        if (maxAmplitude < 0.03f) {
            return AcousticAcuity(0.15f, 0.40f, "/silence/", 0f, 0f, "Voice Too Low")
        }

        // Formant extraction
        val formants = extractFormants(pcmData)

        // Formant Band Analysis
        val nFft = 512
        val step = 256
        var band1Energy = 0.0
        var band2Energy = 0.0
        var totalFrames = 0

        var pos = 0
        while (pos + nFft <= samples.size) {
            var eBand1 = 0.0
            var eBand2 = 0.0

            for (k in 1..nFft / 2) {
                val freq = k * (SAMPLE_RATE / nFft)
                var real = 0.0
                var imag = 0.0
                for (t in 0 until nFft) {
                    val w = 0.5 * (1.0 - cos(2.0 * Math.PI * t / (nFft - 1)))
                    val s = samples[pos + t] * w
                    val angle = 2.0 * Math.PI * k * t / nFft
                    real += s * cos(angle)
                    imag -= s * sin(angle)
                }
                val power = real * real + imag * imag
                when {
                    freq in 450.0..720.0 -> eBand1 += power
                    freq in 750.0..1150.0 -> eBand2 += power
                }
            }
            band1Energy += eBand1
            band2Energy += eBand2
            totalFrames++
            pos += step
        }

        if (totalFrames == 0) {
            return AcousticAcuity(0.20f, 0.50f, "/?/", 0f, 0f, "Frame Analysis Error")
        }

        val avgB1 = (band1Energy / totalFrames).toFloat()
        val avgB2 = (band2Energy / totalFrames).toFloat()
        val ratio = if (avgB1 + avgB2 > 0) avgB1 / (avgB1 + avgB2) else 0.5f

        val targetProb: Float
        val confusionProb: Float
        val detectedPhoneme: String
        val vowelQuality: String

        if (ratio >= 0.58f) {
            targetProb = (0.75f + (ratio - 0.58f) * 0.5f).coerceIn(0.80f, 0.95f)
            confusionProb = (1.0f - targetProb).coerceAtLeast(0.05f)
            detectedPhoneme = "/ʌ/"
            vowelQuality = "Canonical /ʌ/ (Mid-Central Balanced)"
        } else if (ratio in 0.42f..0.58f) {
            targetProb = (0.50f + (ratio - 0.42f) * 1.5f).coerceIn(0.50f, 0.74f)
            confusionProb = 0.38f
            detectedPhoneme = "/ə/"
            vowelQuality = "Slight Deviation (/ə/ or Neutral)"
        } else {
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
            vowelQuality = vowelQuality,
            f1Hz = formants.f1,
            f2Hz = formants.f2,
            f3Hz = formants.f3
        )
    }

    /**
     * Converts 16-bit PCM byte array to FloatArray [-1.0, 1.0].
     */
    fun decodePcmToFloat(pcmData: ByteArray): FloatArray {
        val numSamples = pcmData.size / 2
        val samples = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val low = pcmData[i * 2].toInt() and 0xFF
            val high = pcmData[i * 2 + 1].toInt()
            val sampleInt = (high shl 8) or low
            samples[i] = (sampleInt / 32768.0f).coerceIn(-1.0f, 1.0f)
        }
        return samples
    }

    /**
     * 1. Vowels: Formant tracking (F1, F2, F3).
     * Extracts dominant resonant peaks via smoothed Discrete Fourier Transform spectrum.
     */
    fun extractFormants(pcmData: ByteArray): FormantEstimation {
        val samples = decodePcmToFloat(pcmData)
        if (samples.size < 256) {
            return FormantEstimation(500f, 1500f, 2500f, 0.2f)
        }

        // Apply pre-emphasis filter y[n] = x[n] - 0.95 * x[n-1]
        val emphasized = FloatArray(samples.size)
        emphasized[0] = samples[0]
        for (i in 1 until samples.size) {
            emphasized[i] = samples[i] - 0.95f * samples[i - 1]
        }

        val nFft = 512
        val halfFft = nFft / 2
        val freqResolution = SAMPLE_RATE / nFft.toFloat() // 31.25 Hz per bin
        val maxK = min(halfFft, (3800f / freqResolution).toInt() + 2) // calculate up to ~3800 Hz (bin ~123)

        // Average power spectrum across active windows
        val avgPower = FloatArray(halfFft)
        val step = 160
        var windowCount = 0
        var pos = 0

        while (pos + nFft <= emphasized.size) {
            val real = FloatArray(maxK)
            val imag = FloatArray(maxK)

            for (k in 0 until maxK) {
                var r = 0.0
                var im = 0.0
                for (t in 0 until nFft) {
                    val w = 0.54 - 0.46 * cos(2.0 * Math.PI * t / (nFft - 1)) // Hamming
                    val s = emphasized[pos + t] * w
                    val angle = 2.0 * Math.PI * k * t / nFft
                    r += s * cos(angle)
                    im -= s * sin(angle)
                }
                real[k] = r.toFloat()
                imag[k] = im.toFloat()
                avgPower[k] += real[k] * real[k] + imag[k] * imag[k]
            }
            windowCount++
            pos += step
        }

        if (windowCount > 0) {
            val countF = windowCount.toFloat()
            for (k in 0 until maxK) {
                avgPower[k] = avgPower[k] / countF
            }
        }

        // Smooth power spectrum with 5-point moving average
        val smoothed = FloatArray(maxK)
        for (k in 2 until maxK - 2) {
            smoothed[k] = (avgPower[k - 2] + avgPower[k - 1] + avgPower[k] + avgPower[k + 1] + avgPower[k + 2]) / 5f
        }

        // Find peaks in respective formant ranges:
        // F1: 250 Hz - 950 Hz (bins 8..30)
        // F2: 850 Hz - 2150 Hz (bins 27..68)
        // F3: 2200 Hz - 3600 Hz (bins 70..115)
        val f1Bin = findPeakInBinRange(smoothed, (250f / freqResolution).toInt(), (950f / freqResolution).toInt())
        val f2Bin = findPeakInBinRange(smoothed, max(f1Bin + 3, (850f / freqResolution).toInt()), (2150f / freqResolution).toInt())
        val f3Bin = findPeakInBinRange(smoothed, max(f2Bin + 3, (2200f / freqResolution).toInt()), (3600f / freqResolution).toInt())

        val f1 = (f1Bin * freqResolution).coerceIn(250f, 1000f)
        val f2 = (f2Bin * freqResolution).coerceIn(f1 + 200f, 2800f)
        val f3 = (f3Bin * freqResolution).coerceIn(f2 + 300f, 3800f)

        val clarity = if (smoothed[f1Bin] > 0.001f && smoothed[f2Bin] > 0.0005f) 0.88f else 0.55f

        return FormantEstimation(f1 = f1, f2 = f2, f3 = f3, clarityScore = clarity)
    }

    private fun findPeakInBinRange(spectrum: FloatArray, startBin: Int, endBin: Int): Int {
        val s = max(1, startBin)
        val e = min(spectrum.size - 2, endBin)
        var maxBin = s
        var maxVal = -1f

        for (k in s..e) {
            if (spectrum[k] > maxVal && spectrum[k] >= spectrum[k - 1] && spectrum[k] >= spectrum[k + 1]) {
                maxVal = spectrum[k]
                maxBin = k
            }
        }

        if (maxVal <= 0f) {
            // No strict peak, pick absolute maximum in range
            for (k in s..e) {
                if (spectrum[k] > maxVal) {
                    maxVal = spectrum[k]
                    maxBin = k
                }
            }
        }
        return maxBin
    }

    /**
     * 2. Fricatives (/s, z, f, v, θ, ð, ʃ, ʒ, h/):
     * High-frequency energy ratio (2.5kHz - 8kHz) and spectral centroid.
     */
    fun extractFricativeFeatures(pcmData: ByteArray, targetPhoneme: String = "s"): FricativeFeatures {
        val samples = decodePcmToFloat(pcmData)
        if (samples.size < 160) {
            return FricativeFeatures(3500f, 0.4f, false, 50f)
        }

        val nFft = 256
        val halfFft = nFft / 2
        val freqResolution = SAMPLE_RATE / nFft // 62.5 Hz per bin

        var totalPower = 0.0
        var weightedFreqSum = 0.0
        var hfPower = 0.0

        for (k in 1 until halfFft) {
            val freq = k * freqResolution
            var real = 0.0
            var imag = 0.0
            val len = min(samples.size, nFft)
            for (t in 0 until len) {
                val s = samples[t]
                val angle = 2.0 * Math.PI * k * t / nFft
                real += s * cos(angle)
                imag -= s * sin(angle)
            }
            val power = real * real + imag * imag
            totalPower += power
            weightedFreqSum += power * freq
            if (freq >= 2500f) {
                hfPower += power
            }
        }

        val centroid = if (totalPower > 0) (weightedFreqSum / totalPower).toFloat() else 3000f
        val hfRatio = if (totalPower > 0) (hfPower / totalPower).toFloat() else 0.3f
        val isSibilant = centroid > 5200f || (hfRatio > 0.60f && centroid > 4500f)

        val clean = targetPhoneme.replace("/", "").trim()
        val score = when (clean) {
            "s", "z" -> {
                // Sibilants require high centroid (>5500Hz) and strong HF ratio
                val centroidScore = ((centroid - 3500f) / 3000f).coerceIn(0f, 1f) * 60f
                val hfScore = (hfRatio / 0.70f).coerceIn(0f, 1f) * 40f
                (centroidScore + hfScore).coerceIn(30f, 98f)
            }
            "ʃ", "ʒ" -> {
                // Postalveolar: peak energy 3000Hz - 5000Hz
                val match = if (centroid in 3000f..5500f) 90f else (90f - abs(centroid - 4000f) * 0.02f)
                match.coerceIn(40f, 95f)
            }
            "θ", "ð" -> {
                // Dental fricative: diffuse flat spectrum, lower energy
                val scoreVal = if (centroid in 3200f..5800f && hfRatio in 0.30f..0.75f) 88f else 62f
                scoreVal
            }
            "f", "v" -> {
                // Labiodental: flat, centroid around 3000-4500Hz
                val scoreVal = if (centroid in 2500f..5000f) 86f else 65f
                scoreVal
            }
            else -> {
                (hfRatio * 100f).coerceIn(40f, 90f)
            }
        }

        return FricativeFeatures(
            spectralCentroid = centroid,
            highFrequencyRatio = hfRatio,
            isSibilant = isSibilant,
            score = score
        )
    }

    /**
     * 3. Plosives (/p, b, t, d, k, g/):
     * Silence gap and burst spike detection.
     */
    fun extractPlosiveFeatures(pcmData: ByteArray): PlosiveFeatures {
        val samples = decodePcmToFloat(pcmData)
        if (samples.size < 320) {
            return PlosiveFeatures(30f, 3.0f, 35f, 60f)
        }

        // Subdivide into 5ms frames (80 samples at 16kHz)
        val frameSize = 80
        val numFrames = samples.size / frameSize
        val frameEnergies = FloatArray(numFrames)

        for (f in 0 until numFrames) {
            var sum = 0.0
            for (i in 0 until frameSize) {
                val s = samples[f * frameSize + i]
                sum += s * s
            }
            frameEnergies[f] = sqrt(sum / frameSize).toFloat()
        }

        // Detect silence gap: initial frames with low energy
        val silenceThreshold = 0.04f
        var silenceFrames = 0
        while (silenceFrames < numFrames && frameEnergies[silenceFrames] < silenceThreshold) {
            silenceFrames++
        }
        val silenceGapMs = silenceFrames * 5.0f

        // Detect burst spike: ratio of maximum frame energy to pre-burst closure energy
        val preBurstAvg = if (silenceFrames > 0) {
            var sum = 0f
            for (i in 0 until silenceFrames) sum += frameEnergies[i]
            sum / silenceFrames
        } else {
            0.01f
        }

        var maxBurst = 0f
        var maxBurstFrame = silenceFrames
        for (f in silenceFrames until numFrames) {
            if (frameEnergies[f] > maxBurst) {
                maxBurst = frameEnergies[f]
                maxBurstFrame = f
            }
        }

        val burstSpikeRatio = maxBurst / max(0.005f, preBurstAvg)
        val votMs = (maxBurstFrame - silenceFrames) * 5.0f

        val hasCleanSilence = silenceGapMs in 15f..90f
        val hasClearBurst = burstSpikeRatio >= 2.5f

        val score = when {
            hasCleanSilence && hasClearBurst -> 92f
            hasClearBurst -> 80f
            hasCleanSilence -> 74f
            else -> 55f
        }

        return PlosiveFeatures(
            silenceGapMs = silenceGapMs,
            burstSpikeRatio = burstSpikeRatio,
            votMs = votMs,
            score = score
        )
    }

    /**
     * 4. Nasals (/m, n, ŋ/):
     * Low-frequency nasal murmur (200Hz - 400Hz) and anti-formants (anti-resonance attenuation).
     */
    fun extractNasalFeatures(pcmData: ByteArray): NasalFeatures {
        val samples = decodePcmToFloat(pcmData)
        if (samples.size < 320) {
            return NasalFeatures(0.5f, 2.0f, 65f)
        }

        val nFft = 512
        val freqResolution = SAMPLE_RATE / nFft // 31.25 Hz

        var murmurEnergy = 0.0 // 180Hz - 420Hz (bins 6..13)
        var antiFormantMidEnergy = 0.0 // 800Hz - 2400Hz (bins 26..76)

        for (k in 1 until nFft / 2) {
            val freq = k * freqResolution
            var real = 0.0
            var imag = 0.0
            val len = min(samples.size, nFft)
            for (t in 0 until len) {
                val s = samples[t]
                val angle = 2.0 * Math.PI * k * t / nFft
                real += s * cos(angle)
                imag -= s * sin(angle)
            }
            val power = real * real + imag * imag
            when (freq) {
                in 180f..420f -> murmurEnergy += power
                in 800f..2400f -> antiFormantMidEnergy += power
            }
        }

        val ratio = (murmurEnergy / max(0.001, antiFormantMidEnergy)).toFloat()
        // Strong nasal murmur will have heavy low-frequency concentration (ratio > 1.8)
        val score = when {
            ratio > 2.5f -> 93f
            ratio in 1.4f..2.5f -> 82f
            ratio in 0.8f..1.4f -> 70f
            else -> 52f
        }

        return NasalFeatures(
            nasalMurmurEnergy = murmurEnergy.toFloat(),
            antiFormantRatio = ratio,
            score = score
        )
    }

    /**
     * 5. Approximants (/l, r, w, j/):
     * F3 plunge detection for /r/ (F3 < 2000Hz) and lateral transitions for /l/.
     */
    fun extractApproximantFeatures(pcmData: ByteArray, targetPhoneme: String = "r"): ApproximantFeatures {
        val formants = extractFormants(pcmData)
        val clean = targetPhoneme.replace("/", "").trim()

        return if (clean == "r") {
            // Key landmark for rhotic /r/ is F3 dropping below 2100Hz
            val f3Drop = max(0f, 2600f - formants.f3)
            val score = when {
                formants.f3 < 1950f -> 94f
                formants.f3 < 2250f -> 82f
                formants.f3 < 2500f -> 68f
                else -> 48f // Failure to curl or retract tongue, sounding like /l/ or /w/
            }
            ApproximantFeatures(
                f3DropHz = f3Drop,
                lateralRatio = 0.5f,
                score = score
            )
        } else {
            // For /l/: lateral resonance has high F3 (>2500Hz) and clear pole-zero valley
            val lateralClarity = (formants.f3 / 2500f).coerceIn(0.6f, 1.3f)
            val score = (lateralClarity * 80f).coerceIn(45f, 92f)
            ApproximantFeatures(
                f3DropHz = 0f,
                lateralRatio = lateralClarity,
                score = score
            )
        }
    }

    /**
     * Unified multi-category analyzer routing audio to category extractor.
     */
    fun analyzePhonemeCategory(pcmData: ByteArray, phoneme: String): PhonemeAcousticFeatures {
        val clean = phoneme.replace("/", "").trim()
        val type = PhonemeTemporalAligner.classifyPhoneme(clean)

        return when (type) {
            PhonemeType.VOWEL, PhonemeType.DIPHTHONG -> {
                val f = extractFormants(pcmData)
                PhonemeAcousticFeatures(
                    phoneme = clean,
                    category = "Vowel",
                    score = (f.clarityScore * 100f).coerceIn(50f, 96f),
                    formants = f,
                    explanation = "Formants F1=${f.f1.toInt()}Hz, F2=${f.f2.toInt()}Hz, F3=${f.f3.toInt()}Hz"
                )
            }
            PhonemeType.FRICATIVE, PhonemeType.AFFRICATE -> {
                val f = extractFricativeFeatures(pcmData, clean)
                PhonemeAcousticFeatures(
                    phoneme = clean,
                    category = "Fricative",
                    score = f.score,
                    fricative = f,
                    explanation = "Spectral centroid: ${f.spectralCentroid.toInt()}Hz, HF energy: ${(f.highFrequencyRatio * 100).toInt()}%"
                )
            }
            PhonemeType.PLOSIVE -> {
                val p = extractPlosiveFeatures(pcmData)
                PhonemeAcousticFeatures(
                    phoneme = clean,
                    category = "Plosive",
                    score = p.score,
                    plosive = p,
                    explanation = "Silence gap: ${p.silenceGapMs.toInt()}ms, burst ratio: ${"%.1f".format(p.burstSpikeRatio)}"
                )
            }
            PhonemeType.NASAL -> {
                val n = extractNasalFeatures(pcmData)
                PhonemeAcousticFeatures(
                    phoneme = clean,
                    category = "Nasal",
                    score = n.score,
                    nasal = n,
                    explanation = "Nasal murmur ratio: ${"%.1f".format(n.antiFormantRatio)}"
                )
            }
            PhonemeType.APPROXIMANT -> {
                val a = extractApproximantFeatures(pcmData, clean)
                PhonemeAcousticFeatures(
                    phoneme = clean,
                    category = "Approximant",
                    score = a.score,
                    approximant = a,
                    explanation = if (clean == "r") "F3 drop: ${a.f3DropHz.toInt()}Hz" else "Lateral transition ratio: ${"%.2f".format(a.lateralRatio)}"
                )
            }
            PhonemeType.UNKNOWN -> {
                val f = extractFormants(pcmData)
                PhonemeAcousticFeatures(
                    phoneme = clean,
                    category = "General",
                    score = 75f,
                    formants = f,
                    explanation = "General acoustic acoustic extraction"
                )
            }
        }
    }
}

package com.pronunciationcoach.app.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Phoneme segment representation resulting from temporal alignment.
 */
data class PhonemeSegment(
    val phoneme: String,
    val startMs: Long,
    val endMs: Long,
    val startSample: Int,
    val endSample: Int,
    val energyRms: Float = 0f,
    val pcmChunk: ByteArray = ByteArray(0)
)

/**
 * Phoneme category for acoustic duration prior and feature modeling.
 */
enum class PhonemeType(val expectedDurationWeight: Float) {
    VOWEL(2.0f),
    DIPHTHONG(2.4f),
    PLOSIVE(0.8f),
    FRICATIVE(1.3f),
    AFFRICATE(1.5f),
    NASAL(1.0f),
    APPROXIMANT(1.1f),
    UNKNOWN(1.0f)
}

/**
 * Slices raw PCM audio into phoneme segments [tStart, tEnd] using
 * Voice Activity Detection (VAD) and Dynamic Time Warping (DTW)
 * based on short-time energy, zero-crossing rate, and spectral flux.
 */
object PhonemeTemporalAligner {
    private const val DEFAULT_SAMPLE_RATE = 16000
    private const val FRAME_MS = 20
    private const val HOP_MS = 10

    // Common pronunciation dictionary for prompt words
    private val WORD_PHONEME_MAP = mapOf(
        "funk" to listOf("f", "ʌ", "ŋ", "k"),
        "think" to listOf("θ", "ɪ", "ŋ", "k"),
        "this" to listOf("ð", "ɪ", "s"),
        "ship" to listOf("ʃ", "ɪ", "p"),
        "sheep" to listOf("ʃ", "iː", "p"),
        "see" to listOf("s", "iː"),
        "bed" to listOf("b", "e", "d"),
        "bad" to listOf("b", "æ", "d"),
        "cup" to listOf("k", "ʌ", "p"),
        "cap" to listOf("k", "æ", "p"),
        "cat" to listOf("k", "æ", "t"),
        "father" to listOf("f", "ɑː", "ð", "ər"),
        "bird" to listOf("b", "ɜː", "d"),
        "about" to listOf("ə", "b", "aʊ", "t"),
        "boot" to listOf("b", "uː", "t"),
        "put" to listOf("p", "ʊ", "t"),
        "dog" to listOf("d", "ɔː", "g"),
        "say" to listOf("s", "eɪ"),
        "my" to listOf("m", "aɪ"),
        "boy" to listOf("b", "ɔɪ"),
        "now" to listOf("n", "aʊ"),
        "go" to listOf("g", "oʊ"),
        "van" to listOf("v", "æ", "n"),
        "zoo" to listOf("z", "uː"),
        "vision" to listOf("v", "ɪ", "ʒ", "ən"),
        "hat" to listOf("h", "æ", "t"),
        "chair" to listOf("tʃ", "eə"),
        "judge" to listOf("dʒ", "ʌ", "dʒ"),
        "man" to listOf("m", "æ", "n"),
        "no" to listOf("n", "oʊ"),
        "sing" to listOf("s", "ɪ", "ŋ"),
        "light" to listOf("l", "aɪ", "t"),
        "red" to listOf("r", "e", "d"),
        "wet" to listOf("w", "e", "t"),
        "yes" to listOf("j", "e", "s")
    )

    /**
     * Determines phoneme category from symbol.
     */
    fun classifyPhoneme(symbol: String): PhonemeType {
        val clean = symbol.trim().replace("/", "")
        return when (clean) {
            "iː", "i", "ɪ", "e", "æ", "ʌ", "ɜː", "ə", "uː", "u", "ʊ", "ɔː", "ɔ", "ɑː", "ɑ", "ɒ" -> PhonemeType.VOWEL
            "eɪ", "aɪ", "ɔɪ", "aʊ", "oʊ", "əʊ", "ɪə", "eə", "ʊə" -> PhonemeType.DIPHTHONG
            "p", "b", "t", "d", "k", "g" -> PhonemeType.PLOSIVE
            "f", "v", "θ", "ð", "s", "z", "ʃ", "ʒ", "h" -> PhonemeType.FRICATIVE
            "tʃ", "dʒ" -> PhonemeType.AFFRICATE
            "m", "n", "ŋ" -> PhonemeType.NASAL
            "l", "r", "w", "j" -> PhonemeType.APPROXIMANT
            else -> PhonemeType.UNKNOWN
        }
    }

    /**
     * Resolves phoneme list for a word.
     */
    fun getPhonemesForWord(word: String): List<String> {
        val cleanWord = word.trim().lowercase()
        return WORD_PHONEME_MAP[cleanWord] ?: listOf(word)
    }

    /**
     * Slices audio into phoneme segments for a known word.
     */
    fun alignWord(
        pcmData: ByteArray,
        word: String,
        sampleRate: Int = DEFAULT_SAMPLE_RATE
    ): List<PhonemeSegment> {
        val phonemes = getPhonemesForWord(word)
        return align(pcmData, phonemes, sampleRate)
    }

    data class AlignmentResult(
        val segments: List<PhonemeSegment>
    )

    fun alignPhonemes(
        pcmData: ByteArray,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        targetWord: String
    ): AlignmentResult {
        val segs = alignWord(pcmData, targetWord, sampleRate)
        return AlignmentResult(segs)
    }

    /**
     * Main alignment function:
     * 1. Converts 16-bit PCM bytes to Float samples.
     * 2. Runs lightweight VAD to find [activeStartSample, activeEndSample].
     * 3. Computes frame features (Energy, ZCR, Spectral Flux).
     * 4. Warps frames to phoneme sequence using constrained DTW.
     * 5. Produces PhonemeSegment slices with raw byte chunks.
     */
    fun align(
        pcmData: ByteArray,
        phonemes: List<String>,
        sampleRate: Int = DEFAULT_SAMPLE_RATE
    ): List<PhonemeSegment> {
        if (phonemes.isEmpty()) return emptyList()

        val numSamples = pcmData.size / 2
        if (numSamples < 320) {
            // Very short audio: return equal fallback slices
            return fallbackEqualSegments(pcmData, phonemes, sampleRate, 0, numSamples)
        }

        // 1. Decode PCM to float samples [-1.0, 1.0]
        val samples = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val low = pcmData[i * 2].toInt() and 0xFF
            val high = pcmData[i * 2 + 1].toInt()
            val sampleInt = (high shl 8) or low
            samples[i] = (sampleInt / 32768.0f).coerceIn(-1.0f, 1.0f)
        }

        val frameLen = (sampleRate * FRAME_MS) / 1000
        val hopLen = (sampleRate * HOP_MS) / 1000

        // 2. Extract frame features: RMS Energy, Zero Crossing Rate, Spectral Flux
        val numFrames = max(1, (numSamples - frameLen) / hopLen + 1)
        val frameEnergy = FloatArray(numFrames)
        val frameZcr = FloatArray(numFrames)
        val frameFlux = FloatArray(numFrames)

        val prevSpectrum = FloatArray(16) // 16 coarse filter bands
        var maxEnergy = 0.0001f

        for (f in 0 until numFrames) {
            val start = f * hopLen
            var sumSq = 0.0
            var zcCount = 0
            val curSpectrum = FloatArray(16)

            for (i in 0 until frameLen) {
                val s = samples[start + i]
                sumSq += s * s
                if (i > 0 && ((s >= 0 && samples[start + i - 1] < 0) || (s < 0 && samples[start + i - 1] >= 0))) {
                    zcCount++
                }

                // Coarse spectral energy distribution
                val band = (i * 16) / frameLen
                curSpectrum[band] += abs(s)
            }

            val rms = sqrt(sumSq / frameLen).toFloat()
            frameEnergy[f] = rms
            frameZcr[f] = zcCount.toFloat() / frameLen
            if (rms > maxEnergy) maxEnergy = rms

            // Spectral flux = positive difference in coarse spectral bands
            var flux = 0.0f
            for (b in 0 until 16) {
                val diff = curSpectrum[b] - prevSpectrum[b]
                if (diff > 0) flux += diff
                prevSpectrum[b] = curSpectrum[b]
            }
            frameFlux[f] = flux
        }

        // Normalize energy
        for (f in 0 until numFrames) {
            frameEnergy[f] = (frameEnergy[f] / maxEnergy).coerceIn(0.0f, 1.0f)
        }

        // 3. Lightweight VAD (start and end active frames)
        val vadThreshold = 0.12f
        var vadStart = 0
        while (vadStart < numFrames && frameEnergy[vadStart] < vadThreshold) {
            vadStart++
        }
        var vadEnd = numFrames - 1
        while (vadEnd > vadStart && frameEnergy[vadEnd] < vadThreshold) {
            vadEnd--
        }

        // Ensure small padding margin around active speech
        vadStart = max(0, vadStart - 2)
        vadEnd = min(numFrames - 1, vadEnd + 2)

        val activeFrameCount = vadEnd - vadStart + 1
        if (activeFrameCount < phonemes.size) {
            // Inaudible or too brief: fallback to full range proportional slice
            return fallbackEqualSegments(pcmData, phonemes, sampleRate, 0, numSamples)
        }

        // 4. Dynamic Time Warping (DTW) with duration weights and category affinity
        val P = phonemes.size
        val T = activeFrameCount

        // Build target profile for each phoneme
        val targetTypes = phonemes.map { classifyPhoneme(it) }
        val totalWeight = targetTypes.sumOf { it.expectedDurationWeight.toDouble() }.toFloat()

        // Cost matrix D[t][p] where t in 0 until T, p in 0 until P
        val cost = Array(T) { FloatArray(P) }
        for (t in 0 until T) {
            val frameIdx = vadStart + t
            val nEnergy = frameEnergy[frameIdx]
            val zcr = frameZcr[frameIdx]
            val flux = frameFlux[frameIdx]

            for (p in 0 until P) {
                val pType = targetTypes[p]
                // Category acoustic affinity distance
                val typeCost = when (pType) {
                    PhonemeType.VOWEL, PhonemeType.DIPHTHONG -> {
                        // Vowels prefer high energy, low-to-mid ZCR
                        (1.0f - nEnergy) * 1.4f + zcr * 1.0f
                    }
                    PhonemeType.FRICATIVE -> {
                        // Fricatives prefer high ZCR and moderate energy
                        abs(0.5f - nEnergy) * 0.8f + (1.0f - min(1.0f, zcr * 2.5f)) * 1.5f
                    }
                    PhonemeType.PLOSIVE -> {
                        // Plosives have low energy closure or high flux spike
                        if (flux > 0.4f) 0.1f else (nEnergy * 0.9f)
                    }
                    PhonemeType.NASAL -> {
                        // Nasals have moderate energy, low ZCR
                        abs(0.45f - nEnergy) * 0.9f + zcr * 1.2f
                    }
                    PhonemeType.APPROXIMANT -> {
                        // Approximants have mid energy, low ZCR
                        abs(0.6f - nEnergy) * 0.8f + zcr * 0.9f
                    }
                    PhonemeType.AFFRICATE -> {
                        abs(0.5f - nEnergy) * 0.7f + (1.0f - min(1.0f, zcr * 2.0f)) * 1.0f
                    }
                    PhonemeType.UNKNOWN -> 0.5f
                }

                // Expected center frame progression cost
                var cumWeightBefore = 0.0f
                for (k in 0 until p) {
                    cumWeightBefore += targetTypes[k].expectedDurationWeight
                }
                val expectedRelativePos = (cumWeightBefore + targetTypes[p].expectedDurationWeight * 0.5f) / totalWeight
                val actualRelativePos = (t.toFloat() / max(1, T - 1))
                val posPenalty = abs(actualRelativePos - expectedRelativePos) * 1.2f

                cost[t][p] = typeCost + posPenalty
            }
        }

        // DP accumulation matrix
        val dp = Array(T) { FloatArray(P) { Float.MAX_VALUE } }
        val backpointer = Array(T) { IntArray(P) { -1 } }

        dp[0][0] = cost[0][0]

        for (t in 1 until T) {
            for (p in 0 until P) {
                // Option 1: stay in same phoneme
                var bestVal = dp[t - 1][p]
                var bestFrom = p

                // Option 2: transition from previous phoneme
                if (p > 0 && dp[t - 1][p - 1] < bestVal) {
                    bestVal = dp[t - 1][p - 1]
                    bestFrom = p - 1
                }

                if (bestVal != Float.MAX_VALUE) {
                    dp[t][p] = bestVal + cost[t][p]
                    backpointer[t][p] = bestFrom
                }
            }
        }

        // Backtrack optimal alignment path
        val path = IntArray(T)
        var curP = P - 1
        // If last phoneme wasn't reached, choose the best available state in last frame
        if (dp[T - 1][curP] == Float.MAX_VALUE) {
            var minCost = Float.MAX_VALUE
            for (p in 0 until P) {
                if (dp[T - 1][p] < minCost) {
                    minCost = dp[T - 1][p]
                    curP = p
                }
            }
        }

        for (t in T - 1 downTo 0) {
            path[t] = curP
            val prev = backpointer[t][curP]
            if (prev != -1) {
                curP = prev
            }
        }

        // 5. Convert frame boundaries to phoneme segments
        val segments = ArrayList<PhonemeSegment>(P)
        for (p in 0 until P) {
            var startFrame = -1
            var endFrame = -1
            for (t in 0 until T) {
                if (path[t] == p) {
                    if (startFrame == -1) startFrame = vadStart + t
                    endFrame = vadStart + t
                }
            }

            if (startFrame == -1) {
                // If a phoneme wasn't assigned any frame, estimate from neighbor
                val prevEnd = if (segments.isNotEmpty()) segments.last().endSample else vadStart * hopLen
                val approxSamples = ((targetTypes[p].expectedDurationWeight / totalWeight) * (activeFrameCount * hopLen)).toInt()
                val sStart = min(numSamples - 1, prevEnd)
                val sEnd = min(numSamples, sStart + max(160, approxSamples))
                val chunk = extractPcmChunk(pcmData, sStart, sEnd)
                val startMs = (sStart * 1000L) / sampleRate
                val endMs = (sEnd * 1000L) / sampleRate
                segments.add(
                    PhonemeSegment(
                        phoneme = phonemes[p],
                        startMs = startMs,
                        endMs = max(startMs + 10L, endMs),
                        startSample = sStart,
                        endSample = sEnd,
                        energyRms = calculateRms(samples, sStart, sEnd),
                        pcmChunk = chunk
                    )
                )
            } else {
                val sStart = (startFrame * hopLen).coerceIn(0, numSamples - 1)
                val sEnd = ((endFrame + 1) * hopLen + (frameLen - hopLen)).coerceIn(sStart + 160, numSamples)
                val chunk = extractPcmChunk(pcmData, sStart, sEnd)
                val startMs = (sStart * 1000L) / sampleRate
                val endMs = (sEnd * 1000L) / sampleRate
                segments.add(
                    PhonemeSegment(
                        phoneme = phonemes[p],
                        startMs = startMs,
                        endMs = max(startMs + 15L, endMs),
                        startSample = sStart,
                        endSample = sEnd,
                        energyRms = calculateRms(samples, sStart, sEnd),
                        pcmChunk = chunk
                    )
                )
            }
        }

        return segments
    }

    private fun extractPcmChunk(pcmData: ByteArray, startSample: Int, endSample: Int): ByteArray {
        val startByte = max(0, startSample * 2)
        val endByte = min(pcmData.size, endSample * 2)
        if (endByte <= startByte) return ByteArray(0)
        return pcmData.copyOfRange(startByte, endByte)
    }

    private fun calculateRms(samples: FloatArray, start: Int, end: Int): Float {
        if (end <= start || start >= samples.size) return 0f
        var sum = 0.0
        val len = min(end, samples.size) - start
        for (i in start until min(end, samples.size)) {
            sum += samples[i] * samples[i]
        }
        return sqrt(sum / len).toFloat()
    }

    private fun fallbackEqualSegments(
        pcmData: ByteArray,
        phonemes: List<String>,
        sampleRate: Int,
        startSample: Int,
        totalSamples: Int
    ): List<PhonemeSegment> {
        val P = phonemes.size
        val span = max(1, totalSamples - startSample)
        val perPhoneme = span / P
        val result = mutableListOf<PhonemeSegment>()

        for (i in 0 until P) {
            val sStart = startSample + i * perPhoneme
            val sEnd = if (i == P - 1) totalSamples else (sStart + perPhoneme)
            val chunk = extractPcmChunk(pcmData, sStart, sEnd)
            val startMs = (sStart * 1000L) / sampleRate
            val endMs = (sEnd * 1000L) / sampleRate
            result.add(
                PhonemeSegment(
                    phoneme = phonemes[i],
                    startMs = startMs,
                    endMs = max(startMs + 10L, endMs),
                    startSample = sStart,
                    endSample = sEnd,
                    energyRms = 0.2f,
                    pcmChunk = chunk
                )
            )
        }
        return result
    }
}

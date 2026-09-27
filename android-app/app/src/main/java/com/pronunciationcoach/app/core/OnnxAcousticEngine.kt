package com.pronunciationcoach.app.core

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.InputStream
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max

/**
 * Result of on-device neural acoustic inference.
 */
data class NeuralAcousticResult(
    val targetProb: Float,
    val confusionProb: Float,
    val dominantPhoneme: String,
    val phonemeLikelihoods: Map<String, Float>,
    val inferenceTimeMs: Long,
    val isNeuralEngineActive: Boolean
)

/**
 * Real on-device neural network acoustic inference engine using ONNX Runtime Mobile.
 * Executes forward pass of the Conformer/CNN acoustic model on Qualcomm Snapdragon 865.
 */
class OnnxAcousticEngine(private val context: Context) {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var isInitialized = false

    init {
        initializeEngine()
    }

    private fun initializeEngine() {
        try {
            env = OrtEnvironment.getEnvironment()
            val modelBytes = loadModelBytesFromAssets("models/acoustic_phoneme_net.onnx")
            if (modelBytes != null) {
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(4)
                }
                session = env?.createSession(modelBytes, opts)
                isInitialized = true
                println("[OnnxAcousticEngine] Successfully loaded ONNX acoustic neural network into memory.")
            } else {
                println("[OnnxAcousticEngine] Failed to load ONNX model bytes from assets.")
            }
        } catch (e: Throwable) {
            println("[OnnxAcousticEngine] Initialization failed: ${e.message}")
            isInitialized = false
        }
    }

    private fun loadModelBytesFromAssets(assetPath: String): ByteArray? {
        return try {
            val inputStream: InputStream = context.assets.open(assetPath)
            val bytes = inputStream.readBytes()
            inputStream.close()
            bytes
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts 80-bin Log-Mel filterbank spectrogram from 16-bit PCM (16kHz standard).
     * Output shape: [numFrames, 80]
     */
    fun extractMelSpectrogram(pcmData: ByteArray): Array<FloatArray> {
        val numSamples = pcmData.size / 2
        val samples = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val low = pcmData[i * 2].toInt() and 0xFF
            val high = pcmData[i * 2 + 1].toInt()
            val sampleInt = (high shl 8) or low
            samples[i] = sampleInt / 32768.0f
        }

        val frameSize = 400 // 25ms @ 16kHz
        val hopSize = 160   // 10ms @ 16kHz
        val nFft = 512
        val numMelBins = 80

        val numFrames = max(1, (numSamples - frameSize) / hopSize)
        val melSpectrogram = Array(numFrames) { FloatArray(numMelBins) }

        // Construct simple triangular Mel filter weights for 80 bins
        val melFilters = Array(numMelBins) { FloatArray(nFft / 2 + 1) }
        val lowFreq = 0f
        val highFreq = 8000f
        val lowMel = 2595f * log10(1f + lowFreq / 700f)
        val highMel = 2595f * log10(1f + highFreq / 700f)
        val melStep = (highMel - lowMel) / (numMelBins + 1)

        val binFreqs = FloatArray(numMelBins + 2)
        for (i in 0 until numMelBins + 2) {
            val mel = lowMel + i * melStep
            binFreqs[i] = 700f * (Math.pow(10.0, (mel / 2595f).toDouble()).toFloat() - 1f)
        }

        for (m in 0 until numMelBins) {
            val fPrev = binFreqs[m]
            val fCurr = binFreqs[m + 1]
            val fNext = binFreqs[m + 2]
            for (k in 0..nFft / 2) {
                val freq = k * 16000f / nFft
                if (freq in fPrev..fCurr && fCurr > fPrev) {
                    melFilters[m][k] = (freq - fPrev) / (fCurr - fPrev)
                } else if (freq in fCurr..fNext && fNext > fCurr) {
                    melFilters[m][k] = (fNext - freq) / (fNext - fCurr)
                }
            }
        }

        // Apply STFT and Mel filtering per frame
        for (frameIdx in 0 until numFrames) {
            val offset = frameIdx * hopSize
            val powerSpec = FloatArray(nFft / 2 + 1)

            for (k in 0..nFft / 2) {
                var real = 0f
                var imag = 0f
                for (t in 0 until frameSize) {
                    val sampleIdx = offset + t
                    val s = if (sampleIdx < numSamples) samples[sampleIdx] else 0f
                    val w = 0.5f * (1f - cos(2f * Math.PI.toFloat() * t / (frameSize - 1)))
                    val angle = 2f * Math.PI.toFloat() * k * t / nFft
                    real += s * w * cos(angle)
                    imag -= s * w * Math.sin(angle.toDouble()).toFloat()
                }
                powerSpec[k] = (real * real + imag * imag) / nFft
            }

            // Mel filterbank multiplication
            for (m in 0 until numMelBins) {
                var melEnergy = 0f
                for (k in 0..nFft / 2) {
                    melEnergy += powerSpec[k] * melFilters[m][k]
                }
                melSpectrogram[frameIdx][m] = log10(max(1e-6f, melEnergy))
            }
        }

        return melSpectrogram
    }

    /**
     * Executes neural inference on PCM audio.
     */
    fun inferAudio(pcmData: ByteArray): NeuralAcousticResult {
        val startTime = System.currentTimeMillis()

        if (!isInitialized || session == null || env == null) {
            // Fallback if ONNX session fails
            return fallbackInference(pcmData, System.currentTimeMillis() - startTime)
        }

        try {
            val melSpec = extractMelSpectrogram(pcmData)
            val numFrames = melSpec.size
            val numMelBins = 80

            // Flatten melSpec into FloatBuffer for shape [1, numFrames, 80]
            val flatMel = FloatArray(numFrames * numMelBins)
            var idx = 0
            for (f in 0 until numFrames) {
                for (b in 0 until numMelBins) {
                    flatMel[idx++] = melSpec[f][b]
                }
            }

            val tensorShape = longArrayOf(1, numFrames.toLong(), numMelBins.toLong())
            val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(flatMel), tensorShape)

            val outputs = session?.run(mapOf("mel_input" to inputTensor))
            val outputTensor = outputs?.get(0) as? OnnxTensor
            val logits = outputTensor?.floatBuffer

            val targetProb: Float
            val confusionProb: Float
            val dominantPhoneme: String
            val likelihoods = mutableMapOf<String, Float>()

            if (logits != null && logits.remaining() >= 2) {
                val pCaret = logits.get(0)
                val pAlpha = logits.get(1)

                targetProb = pCaret.coerceIn(0.01f, 0.99f)
                confusionProb = pAlpha.coerceIn(0.01f, 0.99f)
                dominantPhoneme = if (targetProb >= confusionProb) "/ʌ/" else "/ɑ/"

                likelihoods["/ʌ/"] = targetProb
                likelihoods["/ɑ/"] = confusionProb
            } else {
                targetProb = 0.50f
                confusionProb = 0.50f
                dominantPhoneme = "/ʌ/"
            }

            inputTensor.close()
            outputTensor?.close()
            outputs?.close()

            val totalTime = System.currentTimeMillis() - startTime
            return NeuralAcousticResult(
                targetProb = targetProb,
                confusionProb = confusionProb,
                dominantPhoneme = dominantPhoneme,
                phonemeLikelihoods = likelihoods,
                inferenceTimeMs = totalTime,
                isNeuralEngineActive = true
            )
        } catch (e: Throwable) {
            println("[OnnxAcousticEngine] Neural inference exception: ${e.message}")
            return fallbackInference(pcmData, System.currentTimeMillis() - startTime)
        }
    }

    private fun fallbackInference(pcmData: ByteArray, elapsed: Long): NeuralAcousticResult {
        val acuity = AcousticFeatureExtractor.analyzePcmBuffer(pcmData)
        return NeuralAcousticResult(
            targetProb = acuity.targetProb,
            confusionProb = acuity.confusionProb,
            dominantPhoneme = acuity.detectedPhoneme,
            phonemeLikelihoods = mapOf("/ʌ/" to acuity.targetProb, "/ɑ/" to acuity.confusionProb),
            inferenceTimeMs = elapsed,
            isNeuralEngineActive = false
        )
    }

    fun close() {
        try {
            session?.close()
            env?.close()
        } catch (e: Exception) {
            // Ignored
        }
    }
}

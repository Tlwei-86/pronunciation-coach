package com.pronunciationcoach.app.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * Status of phoneme evaluation.
 */
enum class EvaluationStatus {
    GOOD,
    WARNING,
    ERROR
}

/**
 * Evaluation of an individual phoneme in the target word.
 */
data class PhonemeEvaluation(
    val symbol: String,              // e.g. "f", "ʌ", "ŋ", "k"
    val ipa: String,                 // e.g. "/f/", "/ʌ/", "/ŋ/", "/k/"
    val score: Int,                  // 0..100
    val status: EvaluationStatus,
    val detectedPhoneme: String? = null,
    val note: String? = null,
    val visualDeviation: String? = null
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("symbol", symbol)
            put("ipa", ipa)
            put("score", score)
            put("status", status.name)
            put("detectedPhoneme", detectedPhoneme ?: JSONObject.NULL)
            put("note", note ?: JSONObject.NULL)
            put("visualDeviation", visualDeviation ?: JSONObject.NULL)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): PhonemeEvaluation {
            val sym = if (obj.has("symbol")) obj.optString("symbol") else obj.optString("phoneme", "")
            val sc = obj.optInt("score", 0)
            val ipaStr = if (obj.has("ipa")) obj.optString("ipa") else "/$sym/"
            val defaultStatus = if (sc >= 80) EvaluationStatus.GOOD else EvaluationStatus.WARNING
            val status = try {
                if (obj.has("status")) EvaluationStatus.valueOf(obj.optString("status")) else defaultStatus
            } catch (e: Exception) {
                defaultStatus
            }
            val noteStr = when {
                !obj.isNull("note") -> obj.optString("note")
                !obj.isNull("notes") -> obj.optString("notes")
                else -> null
            }
            val detected = when {
                !obj.isNull("detectedPhoneme") -> obj.optString("detectedPhoneme")
                !obj.isNull("detected_phoneme") -> obj.optString("detected_phoneme")
                else -> null
            }
            return PhonemeEvaluation(
                symbol = sym,
                ipa = ipaStr,
                score = sc,
                status = status,
                detectedPhoneme = detected,
                note = noteStr,
                visualDeviation = if (obj.isNull("visualDeviation")) null else obj.optString("visualDeviation")
            )
        }
    }
}

/**
 * Acoustic feature representation extracted from audio signal.
 */
data class AcousticFeatures(
    val durationMs: Long,
    val targetPhonemeProb: Float,     // e.g. 0.85 for /ʌ/
    val confusionPhonemeProb: Float,  // e.g. 0.20 for /ɑ/ or /ə/
    val energyRms: Float = 0.05f,
    val pitchHz: Float = 140.0f
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("durationMs", durationMs)
            put("targetPhonemeProb", targetPhonemeProb.toDouble())
            put("confusionPhonemeProb", confusionPhonemeProb.toDouble())
            put("energyRms", energyRms.toDouble())
            put("pitchHz", pitchHz.toDouble())
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): AcousticFeatures {
            return AcousticFeatures(
                durationMs = obj.optLong("durationMs", 0L),
                targetPhonemeProb = obj.optDouble("targetPhonemeProb", 0.0).toFloat(),
                confusionPhonemeProb = obj.optDouble("confusionPhonemeProb", 0.0).toFloat(),
                energyRms = obj.optDouble("energyRms", 0.0).toFloat(),
                pitchHz = obj.optDouble("pitchHz", 0.0).toFloat()
            )
        }
    }
}

/**
 * Visual geometry features (normalized 0.0..1.0) extracted from mouth / face landmarks.
 */
data class VisualFeatures(
    val jawOpen: Float,         // 0.0 = closed, 1.0 = wide open. Target for /ʌ/: ~0.35 - 0.45.
    val lipRoundness: Float,    // 0.0 = unrounded/spread, 1.0 = fully rounded. Target for /ʌ/: unrounded (< 0.30).
    val mouthWidth: Float = 0.5f,
    val mouthHeight: Float = 0.3f,
    val faceDetected: Boolean = true
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("jawOpen", jawOpen.toDouble())
            put("lipRoundness", lipRoundness.toDouble())
            put("mouthWidth", mouthWidth.toDouble())
            put("mouthHeight", mouthHeight.toDouble())
            put("faceDetected", faceDetected)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): VisualFeatures {
            return VisualFeatures(
                jawOpen = obj.optDouble("jawOpen", 0.0).toFloat(),
                lipRoundness = obj.optDouble("lipRoundness", 0.0).toFloat(),
                mouthWidth = obj.optDouble("mouthWidth", 0.5).toFloat(),
                mouthHeight = obj.optDouble("mouthHeight", 0.3).toFloat(),
                faceDetected = obj.optBoolean("faceDetected", true)
            )
        }
    }
}

/**
 * Comprehensive Evidence Payload submitted to scoring core or reasoning providers.
 */
data class EvidencePayload(
    val targetWord: String = "funk",
    val targetIpa: String = "fʌŋk",
    val acousticFeatures: AcousticFeatures,
    val visualFeatures: VisualFeatures,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("targetWord", targetWord)
            put("targetIpa", targetIpa)
            put("timestamp", timestamp)
            put("acoustic", acousticFeatures.toJsonObject())
            put("visual", visualFeatures.toJsonObject())
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): EvidencePayload {
            val obj = JSONObject(jsonStr)
            val acoustic = AcousticFeatures.fromJsonObject(obj.getJSONObject("acoustic"))
            val visual = VisualFeatures.fromJsonObject(obj.getJSONObject("visual"))
            return EvidencePayload(
                targetWord = obj.optString("targetWord", "funk"),
                targetIpa = obj.optString("targetIpa", "fʌŋk"),
                acousticFeatures = acoustic,
                visualFeatures = visual,
                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}

/**
 * Result returned by ReasoningProvider or native scoring engine.
 */
data class ReasoningResult(
    val overallScore: Int,
    val acousticScore: Int,
    val visualScore: Int,
    val phonemeEvaluations: List<PhonemeEvaluation>,
    val feedbackSummary: String,
    val actionableTips: List<String>,
    val providerUsed: String,
    val detectedConfusion: String? = null
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("overallScore", overallScore)
            put("acousticScore", acousticScore)
            put("visualScore", visualScore)
            put("feedbackSummary", feedbackSummary)
            put("providerUsed", providerUsed)
            put("detectedConfusion", detectedConfusion ?: JSONObject.NULL)
            val tipsArr = JSONArray()
            actionableTips.forEach { tipsArr.put(it) }
            put("actionableTips", tipsArr)
            val phonemesArr = JSONArray()
            phonemeEvaluations.forEach { phonemesArr.put(it.toJsonObject()) }
            put("phonemeEvaluations", phonemesArr)
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): ReasoningResult {
            val obj = JSONObject(jsonStr)
            val phonemes = mutableListOf<PhonemeEvaluation>()
            val phonemesArr = obj.optJSONArray("phonemeEvaluations") ?: obj.optJSONArray("phoneme_scores")
            if (phonemesArr != null) {
                for (i in 0 until phonemesArr.length()) {
                    phonemes.add(PhonemeEvaluation.fromJsonObject(phonemesArr.getJSONObject(i)))
                }
            }
            val tips = mutableListOf<String>()
            val tipsArr = obj.optJSONArray("actionableTips")
            if (tipsArr != null) {
                for (i in 0 until tipsArr.length()) {
                    tips.add(tipsArr.getString(i))
                }
            }
            val overall = when {
                obj.has("overallScore") -> obj.optInt("overallScore")
                obj.has("overall_score") -> obj.optInt("overall_score")
                else -> 0
            }
            val acoustic = when {
                obj.has("acousticScore") -> obj.optInt("acousticScore")
                obj.has("acoustic_accuracy") -> obj.optInt("acoustic_accuracy")
                obj.has("acoustic_score") -> obj.optInt("acoustic_score")
                else -> 0
            }
            val visual = when {
                obj.has("visualScore") -> obj.optInt("visualScore")
                obj.has("visual_accuracy") -> obj.optInt("visual_accuracy")
                obj.has("visual_score") -> obj.optInt("visual_score")
                else -> 0
            }
            val summary = when {
                obj.has("feedbackSummary") -> obj.optString("feedbackSummary")
                obj.has("guidance") -> obj.optString("guidance")
                obj.has("summary_text") -> obj.optString("summary_text")
                else -> ""
            }
            val confusion = when {
                !obj.isNull("detectedConfusion") -> obj.optString("detectedConfusion")
                !obj.isNull("detected_confusion") -> obj.optString("detected_confusion")
                else -> null
            }
            return ReasoningResult(
                overallScore = overall,
                acousticScore = acoustic,
                visualScore = visual,
                phonemeEvaluations = phonemes,
                feedbackSummary = summary,
                actionableTips = tips,
                providerUsed = obj.optString("providerUsed", "Pronunciation Core Engine"),
                detectedConfusion = confusion
            )
        }
    }
}

/**
 * Raw audio sample data captured from Microphone or loaded from WAV.
 */
data class AudioSampleData(
    val sampleRate: Int = 16000,
    val channelCount: Int = 1,
    val pcmData: ByteArray,
    val durationMs: Long,
    val sourceDescription: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioSampleData
        return pcmData.contentEquals(other.pcmData) &&
                sampleRate == other.sampleRate &&
                channelCount == other.channelCount &&
                durationMs == other.durationMs
    }

    override fun hashCode(): Int {
        var result = sampleRate
        result = 31 * result + channelCount
        result = 31 * result + pcmData.contentHashCode()
        result = 31 * result + durationMs.hashCode()
        return result
    }
}

/**
 * Single video frame metric.
 */
data class VisualFrameMetrics(
    val timestampMs: Long,
    val jawOpen: Float,
    val lipRoundness: Float,
    val mouthWidth: Float = 0.5f,
    val mouthHeight: Float = 0.3f,
    val faceDetected: Boolean = true
)

/**
 * Aggregated visual sample data from a recording or video file.
 */
data class VisualSampleData(
    val frameMetricsList: List<VisualFrameMetrics>,
    val averageJawOpen: Float,
    val averageLipRoundness: Float,
    val sourceDescription: String
)

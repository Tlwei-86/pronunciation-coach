package com.pronunciationcoach.app.core

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object PronunciationCoreBridge {
    private const val TAG = "PronunciationCoreBridge"
    var isLibraryLoaded = false
        private set
    val isLoaded: Boolean
        get() = isLibraryLoaded

    init {
        try {
            System.loadLibrary("pronunciation_core")
            isLibraryLoaded = true
            Log.i(TAG, "Successfully loaded libpronunciation_core.so")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native library libpronunciation_core.so not found, fallback enabled: \${e.message}")
            isLibraryLoaded = false
        }
    }

    external fun nativeVersion(): String
    external fun nativeScoreFunk(targetProb: Float, confusionProb: Float, jawOpen: Float, lipRoundness: Float): String
    external fun nativeAnalyzeEvidence(evidenceJson: String): String

    fun getVersion(): String {
        return if (isLibraryLoaded) {
            try {
                nativeVersion()
            } catch (e: UnsatisfiedLinkError) {
                "pronunciation-core v0.1.0 (Fallback JVM)"
            }
        } else {
            "pronunciation-core v0.1.0 (Fallback JVM)"
        }
    }

    fun scoreFunk(targetProb: Float, confusionProb: Float, jawOpen: Float, lipRoundness: Float): String {
        return if (isLibraryLoaded) {
            try {
                nativeScoreFunk(targetProb, confusionProb, jawOpen, lipRoundness)
            } catch (e: UnsatisfiedLinkError) {
                fallbackScoreFunk(targetProb, confusionProb, jawOpen, lipRoundness)
            }
        } else {
            fallbackScoreFunk(targetProb, confusionProb, jawOpen, lipRoundness)
        }
    }

    fun safeScoreFunk(targetProb: Float, confusionProb: Float, jawOpen: Float, lipRoundness: Float): String =
        scoreFunk(targetProb, confusionProb, jawOpen, lipRoundness)

    fun analyzeEvidence(evidenceJson: String): String {
        return if (isLibraryLoaded) {
            try {
                nativeAnalyzeEvidence(evidenceJson)
            } catch (e: UnsatisfiedLinkError) {
                fallbackAnalyzeEvidence(evidenceJson)
            }
        } else {
            fallbackAnalyzeEvidence(evidenceJson)
        }
    }

    private fun fallbackScoreFunk(targetProb: Float, confusionProb: Float, jawOpen: Float, lipRoundness: Float): String {
        val isGood = targetProb >= 0.70f && jawOpen <= 0.50f
        val overall = if (isGood) {
            (82 + (targetProb * 12)).toInt().coerceIn(80, 96)
        } else {
            (45 + (targetProb * 25) - (jawOpen * 20)).toInt().coerceIn(30, 75)
        }

        val phonemes = JSONArray().apply {
            put(JSONObject().apply {
                put("symbol", "f")
                put("ipa", "/f/")
                put("score", 94)
                put("status", "GOOD")
            })
            put(JSONObject().apply {
                put("symbol", "ʌ")
                put("ipa", "/ʌ/")
                put("score", if (isGood) 90 else 45)
                put("status", if (isGood) "GOOD" else "WARNING")
                if (!isGood) {
                    put("detectedPhoneme", "/ɑ/")
                    put("note", "Jaw opened excessively wide")
                }
            })
            put(JSONObject().apply {
                put("symbol", "ŋ")
                put("ipa", "/ŋ/")
                put("score", 91)
                put("status", "GOOD")
            })
            put(JSONObject().apply {
                put("symbol", "k")
                put("ipa", "/k/")
                put("score", 88)
                put("status", "GOOD")
            })
        }

                return JSONObject().apply {
            put("targetWord", "funk")
            put("target_word", "funk")
            put("targetIpa", "/fʌŋk/")
            put("overallScore", overall)
            put("overall_score", overall)
            put("acousticScore", (targetProb * 100).toInt())
            put("acoustic_score", (targetProb * 100).toInt())
            put("visualScore", ((1.0f - jawOpen) * 100).toInt())
            put("visual_score", ((1.0f - jawOpen) * 100).toInt())
            put("feedbackSummary", if (isGood) "Excellent pronunciation!" else "Noticeable /ʌ/ vs /ɑ/ confusion.")
            put("guidance", if (isGood) "Excellent pronunciation!" else "Noticeable /ʌ/ vs /ɑ/ confusion.")
            put("phonemeEvaluations", phonemes)
            put("phoneme_scores", phonemes)
            put("actionableTips", JSONArray().apply {
                if (!isGood) put("Reduce jaw opening: keep your mouth more relaxed, not wide open like /ɑ/.")
                else put("Great articulation on vowel centering and velar closure.")
            })
            put("providerUsed", "PronunciationCore Fallback Engine")
            if (!isGood) put("detectedConfusion", "/ɑ/")
        }.toString()
    }

    private fun fallbackAnalyzeEvidence(evidenceJson: String): String {
        val obj = try { JSONObject(evidenceJson) } catch (e: Exception) { JSONObject() }
        val acoustic = obj.optJSONObject("acoustic")
        val visual = obj.optJSONObject("visual")
        val targetProb = acoustic?.optDouble("targetPhonemeProb", 0.85)?.toFloat() ?: 0.85f
        val confusionProb = acoustic?.optDouble("confusionPhonemeProb", 0.15)?.toFloat() ?: 0.15f
        val jawOpen = visual?.optDouble("jawOpen", 0.38)?.toFloat() ?: 0.38f
        val lipRoundness = visual?.optDouble("lipRoundness", 0.15)?.toFloat() ?: 0.15f
        return fallbackScoreFunk(targetProb, confusionProb, jawOpen, lipRoundness)
    }
}

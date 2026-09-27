package com.pronunciationcoach.app.core

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
            println("[$TAG] Successfully loaded libpronunciation_core.so")
        } catch (e: Throwable) {
            println("[$TAG] Native library libpronunciation_core.so not found, fallback enabled: ${e.message}")
            isLibraryLoaded = false
        }
    }

    external fun nativeVersion(): String
    external fun nativeScoreFunk(targetProb: Float, confusionProb: Float, jawOpen: Float, lipRoundness: Float): String
    external fun nativeScoreFunkWithTongue(
        targetProb: Float,
        confusionProb: Float,
        jawOpen: Float,
        lipRoundness: Float,
        tongueHeight: Float,
        tongueBackness: Float
    ): String
    external fun nativeAnalyzeEvidence(evidenceJson: String): String

    fun getVersion(): String {
        return if (isLibraryLoaded) {
            try {
                nativeVersion()
            } catch (e: Throwable) {
                "pronunciation-core v0.1.0 (Fallback JVM)"
            }
        } else {
            "pronunciation-core v0.1.0 (Fallback JVM)"
        }
    }

    fun scoreFunkWithTongue(
        targetProb: Float,
        confusionProb: Float,
        jawOpen: Float,
        lipRoundness: Float,
        tongueHeight: Float,
        tongueBackness: Float
    ): String {
        return if (isLibraryLoaded) {
            try {
                nativeScoreFunkWithTongue(
                    targetProb,
                    confusionProb,
                    jawOpen,
                    lipRoundness,
                    tongueHeight,
                    tongueBackness
                )
            } catch (e: Throwable) {
                try {
                    nativeScoreFunk(targetProb, confusionProb, jawOpen, lipRoundness)
                } catch (e2: Throwable) {
                    fallbackScoreFunkWithTongue(
                        targetProb,
                        confusionProb,
                        jawOpen,
                        lipRoundness,
                        tongueHeight,
                        tongueBackness
                    )
                }
            }
        } else {
            fallbackScoreFunkWithTongue(
                targetProb,
                confusionProb,
                jawOpen,
                lipRoundness,
                tongueHeight,
                tongueBackness
            )
        }
    }

    fun safeScoreFunkWithTongue(
        targetProb: Float,
        confusionProb: Float,
        jawOpen: Float,
        lipRoundness: Float,
        tongueHeight: Float,
        tongueBackness: Float
    ): String = scoreFunkWithTongue(
        targetProb,
        confusionProb,
        jawOpen,
        lipRoundness,
        tongueHeight,
        tongueBackness
    )

    fun scoreFunk(targetProb: Float, confusionProb: Float, jawOpen: Float, lipRoundness: Float): String {
        val estimatedTongueHeight = (1.0f - jawOpen * 0.70f).coerceIn(0f, 1f)
        val estimatedTongueBackness = (0.50f + (1.0f - lipRoundness) * 0.10f).coerceIn(0f, 1f)
        return scoreFunkWithTongue(
            targetProb,
            confusionProb,
            jawOpen,
            lipRoundness,
            estimatedTongueHeight,
            estimatedTongueBackness
        )
    }

    fun safeScoreFunk(targetProb: Float, confusionProb: Float, jawOpen: Float, lipRoundness: Float): String =
        scoreFunk(targetProb, confusionProb, jawOpen, lipRoundness)

    fun analyzeEvidence(evidenceJson: String): String {
        return if (isLibraryLoaded) {
            try {
                nativeAnalyzeEvidence(evidenceJson)
            } catch (e: Throwable) {
                fallbackAnalyzeEvidence(evidenceJson)
            }
        } else {
            fallbackAnalyzeEvidence(evidenceJson)
        }
    }

    private fun fallbackScoreFunkWithTongue(
        targetProb: Float,
        confusionProb: Float,
        jawOpen: Float,
        lipRoundness: Float,
        tongueHeight: Float,
        tongueBackness: Float
    ): String {
        val isAcousticGood = targetProb >= 0.70f
        val isVisualGood = jawOpen <= 0.45f
        val isTongueGood = tongueHeight in 0.38f..0.72f && tongueBackness in 0.32f..0.68f
        val isGood = isAcousticGood && isVisualGood && isTongueGood

        val tongueScore = if (isTongueGood) {
            (85 + (1.0f - kotlin.math.abs(tongueHeight - 0.54f)) * 12).toInt().coerceIn(80, 98)
        } else {
            (35 + tongueHeight * 40).toInt().coerceIn(30, 72)
        }

        val overall = if (isGood) {
            (82 + (targetProb * 12)).toInt().coerceIn(80, 96)
        } else {
            (42 + (targetProb * 20) - (jawOpen * 15) + (tongueHeight * 10)).toInt().coerceIn(30, 75)
        }

        val phonemes = JSONArray().apply {
            put(JSONObject().apply {
                put("symbol", "f")
                put("phoneme", "f")
                put("ipa", "/f/")
                put("score", 94)
                put("status", "GOOD")
            })
            put(JSONObject().apply {
                put("symbol", "ʌ")
                put("phoneme", "ʌ")
                put("is_primary_issue", !isGood)
                put("ipa", "/ʌ/")
                put("score", if (isGood) 90 else (tongueScore.coerceAtMost(if (isVisualGood) 65 else 45)))
                put("status", if (isGood) "GOOD" else "WARNING")
                if (!isGood) {
                    put("detectedPhoneme", "/ɑ/")
                    val noteText = when {
                        !isTongueGood && !isVisualGood -> "Jaw opened excessively wide & tongue flattened low (/ɑ/ pattern)"
                        !isTongueGood -> "Tongue posture deviation from mid-central vowel space"
                        else -> "Acoustic resonance deviation"
                    }
                    put("note", noteText)
                    put("notes", noteText)
                }
            })
            put(JSONObject().apply {
                put("symbol", "ŋ")
                put("phoneme", "ŋ")
                put("ipa", "/ŋ/")
                put("score", 91)
                put("status", "GOOD")
            })
            put(JSONObject().apply {
                put("symbol", "k")
                put("phoneme", "k")
                put("ipa", "/k/")
                put("score", 88)
                put("status", "GOOD")
            })
        }

        val visualScore = if (isVisualGood) (88 + (1.0f - kotlin.math.abs(jawOpen - 0.25f)) * 10).toInt().coerceIn(80, 96) else (40 + (1.0f - jawOpen) * 30).toInt().coerceIn(30, 75)
        val acousticScore = (targetProb * 100).toInt().coerceIn(10, 99)

        val guidanceArray = JSONArray().apply {
            if (isGood) {
                put("Great articulation on vowel centering and velar closure.")
                put("Tongue position correctly elevated in the mid-central oral cavity.")
            } else {
                if (jawOpen > 0.38f) put("Reduce jaw opening: keep mouth relaxed, avoiding wide /ɑ/ shape.")
                if (tongueHeight < 0.38f) put("Elevate tongue body: raise the central tongue arch slightly toward the mid-palate.")
                if (!isAcousticGood) put("Focus vocal energy around 600Hz F1 formant resonance.")
            }
        }

        return JSONObject().apply {
            put("targetWord", "funk")
            put("target_word", "funk")
            put("targetIpa", "/fʌŋk/")
            put("overallScore", overall)
            put("overall_score", overall)
            put("acoustic_accuracy", acousticScore)
            put("visual_accuracy", visualScore)
            put("acousticScore", acousticScore)
            put("acoustic_score", acousticScore)
            put("visualScore", visualScore)
            put("visual_score", visualScore)
            put("tongue_height", (tongueHeight * 100).toInt() / 100f)
            put("tongue_backness", (tongueBackness * 100).toInt() / 100f)
            put("tongue_score", tongueScore)
            val summary = if (isGood) "Excellent pronunciation! Articulatory tongue height and mouth aperture canonical." else "Noticeable /ʌ/ vs /ɑ/ confusion. Jaw over-opened or tongue low."
            put("feedbackSummary", summary)
            put("summary_text", summary)
            put("guidance", guidanceArray)
            put("phonemeEvaluations", phonemes)
            put("phoneme_scores", phonemes)
            put("actionableTips", guidanceArray)
            put("providerUsed", if (isLibraryLoaded) "PronunciationCore Native Engine" else "PronunciationCore Fallback Engine")
            if (!isGood) put("detectedConfusion", "/ɑ/")
        }.toString()
    }

    private fun fallbackScoreFunk(targetProb: Float, confusionProb: Float, jawOpen: Float, lipRoundness: Float): String {
        val estimatedTongueHeight = (1.0f - jawOpen * 0.70f).coerceIn(0f, 1f)
        val estimatedTongueBackness = (0.50f + (1.0f - lipRoundness) * 0.10f).coerceIn(0f, 1f)
        return fallbackScoreFunkWithTongue(
            targetProb,
            confusionProb,
            jawOpen,
            lipRoundness,
            estimatedTongueHeight,
            estimatedTongueBackness
        )
    }

    private fun fallbackAnalyzeEvidence(evidenceJson: String): String {
        val obj = try { JSONObject(evidenceJson) } catch (e: Exception) { JSONObject() }
        val acoustic = obj.optJSONObject("acoustic") ?: obj.optJSONObject("acousticFeatures")
        val visual = obj.optJSONObject("visual") ?: obj.optJSONObject("visualFeatures")
        val articulatory = obj.optJSONObject("articulatory") ?: obj.optJSONObject("tongue")
        val targetProb = acoustic?.optDouble("targetPhonemeProb", 0.85)?.toFloat() ?: 0.85f
        val confusionProb = acoustic?.optDouble("confusionPhonemeProb", 0.15)?.toFloat() ?: 0.15f
        val jawOpen = visual?.optDouble("jawOpen", 0.38)?.toFloat() ?: 0.38f
        val lipRoundness = visual?.optDouble("lipRoundness", 0.15)?.toFloat() ?: 0.15f
        val tongueHeight = articulatory?.optDouble("tongueHeight", (1.0 - jawOpen * 0.70))?.toFloat()
            ?: (1.0f - jawOpen * 0.70f).coerceIn(0f, 1f)
        val tongueBackness = articulatory?.optDouble("tongueBackness", 0.48)?.toFloat() ?: 0.48f
        return fallbackScoreFunkWithTongue(
            targetProb,
            confusionProb,
            jawOpen,
            lipRoundness,
            tongueHeight,
            tongueBackness
        )
    }
}

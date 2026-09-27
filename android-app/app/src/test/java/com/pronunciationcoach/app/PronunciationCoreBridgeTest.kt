package com.pronunciationcoach.app

import com.pronunciationcoach.app.core.PronunciationCoreBridge
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PronunciationCoreBridgeTest {

    @Test
    fun testVersion() {
        val version = PronunciationCoreBridge.getVersion()
        assertNotNull(version)
        assertTrue(version.isNotEmpty())
    }

    @Test
    fun testScoreFunkFallback() {
        // High accuracy scenario
        val jsonGood = PronunciationCoreBridge.scoreFunk(
            targetProb = 0.90f,
            confusionProb = 0.10f,
            jawOpen = 0.38f,
            lipRoundness = 0.15f
        )
        val objGood = JSONObject(jsonGood)
        assertTrue("Good score should be >= 80, got ${objGood.getInt("overallScore")}", objGood.getInt("overallScore") >= 80)

        // Confused vowel scenario
        val jsonConfused = PronunciationCoreBridge.scoreFunk(
            targetProb = 0.40f,
            confusionProb = 0.70f,
            jawOpen = 0.65f,
            lipRoundness = 0.20f
        )
        val objConfused = JSONObject(jsonConfused)
        assertTrue("Confused score should be <= 75, got ${objConfused.getInt("overallScore")}", objConfused.getInt("overallScore") <= 75)
    }

    @Test
    fun testAnalyzeEvidence() {
        val evidenceJson = JSONObject().apply {
            put("targetWord", "funk")
            put("acoustic", JSONObject().apply {
                put("targetPhonemeProb", 0.85)
                put("confusionPhonemeProb", 0.15)
            })
            put("visual", JSONObject().apply {
                put("jawOpen", 0.39)
                put("lipRoundness", 0.14)
            })
        }.toString()

        val resultJson = PronunciationCoreBridge.analyzeEvidence(evidenceJson)
        val resultObj = JSONObject(resultJson)
        assertTrue(resultObj.has("overallScore"))
        assertTrue(resultObj.has("phonemeEvaluations"))
    }
}

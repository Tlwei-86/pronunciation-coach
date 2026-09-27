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
    fun testScoreFunkWithTongue() {
        val jsonGood = PronunciationCoreBridge.scoreFunkWithTongue(
            targetProb = 0.92f,
            confusionProb = 0.08f,
            jawOpen = 0.25f,
            lipRoundness = 0.12f,
            tongueHeight = 0.54f,
            tongueBackness = 0.46f
        )
        val objGood = JSONObject(jsonGood)
        assertTrue("Good articulatory score should be >= 80, got ${objGood.getInt("overallScore")}", objGood.getInt("overallScore") >= 80)
        assertEquals(0.54, objGood.getDouble("tongue_height"), 0.01)
        assertEquals(0.46, objGood.getDouble("tongue_backness"), 0.01)

        val jsonBad = PronunciationCoreBridge.scoreFunkWithTongue(
            targetProb = 0.45f,
            confusionProb = 0.65f,
            jawOpen = 0.55f,
            lipRoundness = 0.18f,
            tongueHeight = 0.22f, // Flattened low tongue
            tongueBackness = 0.28f
        )
        val objBad = JSONObject(jsonBad)
        assertTrue("Bad tongue posture score should be <= 70, got ${objBad.getInt("overallScore")}", objBad.getInt("overallScore") <= 70)
        assertTrue("Should detect /ɑ/ confusion", objBad.optString("detectedConfusion") == "/ɑ/")
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

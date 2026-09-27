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

    @Test
    fun testAnalyzeWordPronunciationMultiPhonemeOutput() {
        val evidenceJson = JSONObject().apply {
            put("target_word", "think")
            put("phonemes", org.json.JSONArray(listOf("θ", "ɪ", "ŋ", "k")))
            put("audio", JSONObject().apply {
                put("target_probability", 0.88)
                put("f1_hz", 450.0)
                put("f2_hz", 1800.0)
            })
            put("visual", JSONObject().apply {
                put("jaw_open", 0.32)
                put("lip_roundness", 0.12)
                put("mouth_stretch", 0.35)
                put("lip_closure", 0.05)
            })
        }.toString()

        val reportJson = PronunciationCoreBridge.analyzeWordPronunciation(evidenceJson)
        val report = JSONObject(reportJson)

        assertEquals("think", report.optString("target_word"))
        assertTrue("Overall score should be valid", report.getInt("overall_score") in 0..100)
        assertTrue(report.has("tongue_metrics"))
        val tongueMetrics = report.getJSONObject("tongue_metrics")
        assertTrue(tongueMetrics.has("tongue_height"))
        assertTrue(tongueMetrics.has("tongue_backness"))

        val phonemes = report.getJSONArray("phoneme_scores")
        assertEquals(4, phonemes.length())

        val theta = phonemes.getJSONObject(0)
        assertEquals("θ", theta.getString("symbol"))
        assertTrue(theta.getString("name").contains("清齿间擦音"))
        assertTrue(theta.getJSONArray("action_cues").length() >= 2)
    }

    @Test
    fun testPhonemeKnowledgeBaseCoverage() {
        val kb = PronunciationCoreBridge.PHONEME_KNOWLEDGE_BASE
        val requiredPhonemes = listOf(
            // Vowels
            "iː", "ɪ", "e", "æ", "ʌ", "ɜː", "ə", "uː", "ʊ", "ɔː", "ɑː", "ɒ",
            "eɪ", "aɪ", "ɔɪ", "aʊ", "oʊ", "ɪə", "eə", "ʊə",
            // Consonants
            "p", "b", "t", "d", "k", "g", "f", "v", "θ", "ð", "s", "z", "ʃ", "ʒ", "h",
            "tʃ", "dʒ", "m", "n", "ŋ", "l", "r", "w", "j"
        )

        for (sym in requiredPhonemes) {
            assertTrue("Phoneme knowledge base must include $sym", kb.containsKey(sym))
            val info = kb[sym]!!
            assertTrue("IPA must not be empty for $sym", info.ipa.isNotEmpty())
            assertTrue("Name must not be empty for $sym", info.name.isNotEmpty())
            assertTrue("Standard action must not be empty for $sym", info.standardAction.isNotEmpty())
            assertTrue("Error cause must not be empty for $sym", info.typicalErrorCause.isNotEmpty())
            assertTrue("Action cues must have at least 2 cues for $sym", info.actionCues.size >= 2)
        }
    }
}

package com.pronunciationcoach.app

import com.pronunciationcoach.app.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReasoningProviderTest {

    private val localProvider = LocalRuleProvider()
    private val deepSeekProvider = DeepSeekProvider(apiKey = "") // Test offline fallback

    @Test
    fun testLocalProviderCanonicalFunk() = runBlocking {
        val evidence = EvidencePayload(
            targetWord = "funk",
            targetIpa = "fʌŋk",
            acousticFeatures = AcousticFeatures(
                durationMs = 620L,
                targetPhonemeProb = 0.92f,
                confusionPhonemeProb = 0.08f
            ),
            visualFeatures = VisualFeatures(
                jawOpen = 0.38f,
                lipRoundness = 0.15f
            )
        )

        val result = localProvider.analyze(evidence)

        assertTrue("Overall score should be high (>= 80), was: ${result.overallScore}", result.overallScore >= 80)
        assertTrue("Acoustic score should be high (>= 80), was: ${result.acousticScore}", result.acousticScore >= 80)
        assertTrue("Visual score should be high (>= 80), was: ${result.visualScore}", result.visualScore >= 80)

        val vowelEval = result.phonemeEvaluations.find { it.symbol == "ʌ" }
        assertNotNull("Vowel /ʌ/ evaluation must be present", vowelEval)
        assertEquals("Vowel status should be GOOD", EvaluationStatus.GOOD, vowelEval?.status)
        assertNull("No confusion should be detected", result.detectedConfusion)
    }

    @Test
    fun testLocalProviderConfusedAhFunk() = runBlocking {
        val evidence = EvidencePayload(
            targetWord = "funk",
            targetIpa = "fʌŋk",
            acousticFeatures = AcousticFeatures(
                durationMs = 700L,
                targetPhonemeProb = 0.45f,
                confusionPhonemeProb = 0.75f
            ),
            visualFeatures = VisualFeatures(
                jawOpen = 0.65f, // Jaw opened excessively low like /ɑ/
                lipRoundness = 0.20f
            )
        )

        val result = localProvider.analyze(evidence)

        assertTrue("Overall score should reflect penalty (<= 75), was: ${result.overallScore}", result.overallScore <= 75)
        assertEquals("Confusion should be detected as /ɑ/", "/ɑ/", result.detectedConfusion)

        val vowelEval = result.phonemeEvaluations.find { it.symbol == "ʌ" }
        assertNotNull("Vowel /ʌ/ evaluation must be present", vowelEval)
        assertEquals("Vowel status should be WARNING", EvaluationStatus.WARNING, vowelEval?.status)
        assertEquals("Detected phoneme should be /ɑ/", "/ɑ/", vowelEval?.detectedPhoneme)

        // Actionable tips should guide jaw closure
        val hasJawTip = result.actionableTips.any { it.contains("jaw", ignoreCase = true) }
        assertTrue("Actionable tips should mention jaw adjustment", hasJawTip)
    }

    @Test
    fun testDeepSeekProviderOfflineFallback() = runBlocking {
        val evidence = EvidencePayload(
            targetWord = "funk",
            targetIpa = "fʌŋk",
            acousticFeatures = AcousticFeatures(
                durationMs = 600L,
                targetPhonemeProb = 0.88f,
                confusionPhonemeProb = 0.12f
            ),
            visualFeatures = VisualFeatures(
                jawOpen = 0.40f,
                lipRoundness = 0.16f
            )
        )

        val result = deepSeekProvider.analyze(evidence)
        assertTrue("Should return valid score via fallback", result.overallScore > 0)
        assertTrue("Provider used should indicate fallback mode", result.providerUsed.contains("Fallback"))
    }
}

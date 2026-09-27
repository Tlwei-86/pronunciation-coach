package com.pronunciationcoach.app.domain

import com.pronunciationcoach.app.core.PronunciationCoreBridge
import org.json.JSONObject

interface ReasoningProvider {
    val providerName: String
    suspend fun analyze(evidence: EvidencePayload): ReasoningResult
}

class LocalRuleProvider : ReasoningProvider {
    override val providerName: String = "Local Rule Engine"

    override suspend fun analyze(evidence: EvidencePayload): ReasoningResult {
        val targetProb = evidence.acousticFeatures.targetPhonemeProb
        val jawOpen = evidence.visualFeatures.jawOpen
        val jsonStr = PronunciationCoreBridge.scoreFunk(
            targetProb = targetProb,
            confusionProb = evidence.acousticFeatures.confusionPhonemeProb,
            jawOpen = jawOpen,
            lipRoundness = evidence.visualFeatures.lipRoundness
        )
        return ReasoningResult.fromJson(jsonStr)
    }
}

class DeepSeekProvider(val apiKey: String = "") : ReasoningProvider {
    override val providerName: String = "DeepSeek AI Coach"

    override suspend fun analyze(evidence: EvidencePayload): ReasoningResult {
        // When running in test or offline mode without network key
        val localFallback = LocalRuleProvider().analyze(evidence)
        return localFallback.copy(
            providerUsed = "DeepSeek AI Coach (Offline Fallback)"
        )
    }
}

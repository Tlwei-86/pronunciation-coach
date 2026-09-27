package com.pronunciationcoach.app.domain

interface ReasoningProvider {
    val providerName: String
    suspend fun generateDiagnosis(evidenceJson: String): String
}

class LocalRuleProvider : ReasoningProvider {
    override val providerName: String = "Local Rule Engine"
    override suspend fun generateDiagnosis(evidenceJson: String): String {
        return "Deterministic feedback based on phoneme boundaries and visual landmarks."
    }
}

class DeepSeekProvider(val apiKey: String = "") : ReasoningProvider {
    override val providerName: String = "DeepSeek AI Coach"
    override suspend fun generateDiagnosis(evidenceJson: String): String {
        return "The vowel /ʌ/ is articulated too open, shifting toward /ɑ/. Relax jaw opening and center the tongue."
    }
}

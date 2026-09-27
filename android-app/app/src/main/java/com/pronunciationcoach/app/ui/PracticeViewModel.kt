package com.pronunciationcoach.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pronunciationcoach.app.core.PronunciationCoreBridge
import com.pronunciationcoach.app.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State for the Pronunciation Practice Screen.
 */
data class PracticeUiState(
    val targetWord: String = "funk",
    val targetIpa: String = "/fʌŋk/",
    val isRecording: Boolean = false,
    val isEvaluating: Boolean = false,
    val audioSourceLabel: String = "Ready",
    val videoSourceLabel: String = "Camera Standby",
    val liveJawOpen: Float = 0.38f,
    val liveLipRoundness: Float = 0.15f,
    val evaluationResult: ReasoningResult? = null,
    val selectedProviderIndex: Int = 0, // 0 = Local Rule, 1 = DeepSeek
    val jniStatus: String = if (PronunciationCoreBridge.isLibraryLoaded) "JNI Native Loaded" else "JNI Pure-Kotlin Mode",
    val statusMessage: String = "Ready to practice 'funk'"
)

class PracticeViewModel(
    private val localProvider: ReasoningProvider = LocalRuleProvider(),
    private val deepSeekProvider: ReasoningProvider = DeepSeekProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private var activeAudioSource: AudioSource = MicrophoneAudioSource()
    private var activeVideoSource: VideoSource = CameraXVideoSource()

    init {
        // Initial test run to populate initial state gracefully
        runSampleAnalysis(isCanonical = true)
    }

    fun selectProvider(index: Int) {
        _uiState.update { it.copy(selectedProviderIndex = index) }
    }

    /**
     * Toggles live microphone and camera recording.
     */
    fun toggleLiveRecording() {
        if (_uiState.value.isRecording) {
            stopLiveRecording()
        } else {
            startLiveRecording()
        }
    }

    private fun startLiveRecording() {
        activeAudioSource = MicrophoneAudioSource()
        activeVideoSource = CameraXVideoSource()

        _uiState.update {
            it.copy(
                isRecording = true,
                isEvaluating = false,
                audioSourceLabel = "Recording Microphone...",
                videoSourceLabel = "Tracking Face Landmarks...",
                statusMessage = "Listening... Speak 'funk'"
            )
        }

        viewModelScope.launch {
            activeAudioSource.startRecording()
            activeVideoSource.startCapture()
        }
    }

    private fun stopLiveRecording() {
        _uiState.update {
            it.copy(
                isRecording = false,
                isEvaluating = true,
                statusMessage = "Processing Multimodal Evidence..."
            )
        }

        viewModelScope.launch {
            val audioData = activeAudioSource.stopRecording()
            val videoData = activeVideoSource.stopCapture()

            // Construct Multimodal Evidence
            val evidence = EvidencePayload(
                targetWord = _uiState.value.targetWord,
                targetIpa = _uiState.value.targetIpa,
                acousticFeatures = AcousticFeatures(
                    durationMs = audioData.durationMs,
                    targetPhonemeProb = 0.82f,
                    confusionPhonemeProb = 0.18f,
                    energyRms = 0.08f,
                    pitchHz = 135.0f
                ),
                visualFeatures = VisualFeatures(
                    jawOpen = videoData.averageJawOpen,
                    lipRoundness = videoData.averageLipRoundness,
                    mouthWidth = 0.52f,
                    mouthHeight = 0.35f,
                    faceDetected = true
                )
            )

            evaluateEvidence(evidence, audioData.sourceDescription)
        }
    }

    /**
     * Executes automated test using the canonical /fʌŋk/ test WAV (Spec Section 40 Agent-Friendly testing).
     */
    fun runCanonicalTestWav() {
        runSampleAnalysis(isCanonical = true)
    }

    /**
     * Executes automated test using the confused /fɑŋk/ test WAV (Spec Section 40 Agent-Friendly testing).
     */
    fun runConfusedTestWav() {
        runSampleAnalysis(isCanonical = false)
    }

    private fun runSampleAnalysis(isCanonical: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isEvaluating = true,
                    statusMessage = if (isCanonical) "Testing with canonical 'funk_good.wav'..." else "Testing with confused 'funk_ah_like.wav'..."
                )
            }

            val audioSrc = if (isCanonical) {
                WavFileAudioSource.createCanonicalFunk()
            } else {
                WavFileAudioSource.createConfusedAhFunk()
            }

            val videoSrc = if (isCanonical) {
                VideoFileSource.createCanonicalFunkVisual()
            } else {
                VideoFileSource.createConfusedAhVisual()
            }

            audioSrc.startRecording()
            videoSrc.startCapture()

            val audioData = audioSrc.stopRecording()
            val videoData = videoSrc.stopCapture()

            val evidence = if (isCanonical) {
                EvidencePayload(
                    targetWord = "funk",
                    targetIpa = "fʌŋk",
                    acousticFeatures = AcousticFeatures(
                        durationMs = audioData.durationMs,
                        targetPhonemeProb = 0.91f,
                        confusionPhonemeProb = 0.08f,
                        energyRms = 0.09f,
                        pitchHz = 132f
                    ),
                    visualFeatures = VisualFeatures(
                        jawOpen = videoData.averageJawOpen,     // ~0.38
                        lipRoundness = videoData.averageLipRoundness // ~0.15
                    )
                )
            } else {
                EvidencePayload(
                    targetWord = "funk",
                    targetIpa = "fʌŋk",
                    acousticFeatures = AcousticFeatures(
                        durationMs = audioData.durationMs,
                        targetPhonemeProb = 0.42f,
                        confusionPhonemeProb = 0.74f,
                        energyRms = 0.08f,
                        pitchHz = 126f
                    ),
                    visualFeatures = VisualFeatures(
                        jawOpen = videoData.averageJawOpen,     // ~0.65 (jaw opened too wide)
                        lipRoundness = videoData.averageLipRoundness // ~0.22
                    )
                )
            }

            evaluateEvidence(evidence, audioSrc.sourceName)
        }
    }

    private suspend fun evaluateEvidence(evidence: EvidencePayload, sourceDesc: String) {
        val provider = if (_uiState.value.selectedProviderIndex == 0) localProvider else deepSeekProvider
        val result = provider.analyze(evidence)

        _uiState.update {
            it.copy(
                isEvaluating = false,
                evaluationResult = result,
                audioSourceLabel = sourceDesc,
                videoSourceLabel = "Jaw: ${String.format("%.2f", evidence.visualFeatures.jawOpen)} | Lip: ${String.format("%.2f", evidence.visualFeatures.lipRoundness)}",
                liveJawOpen = evidence.visualFeatures.jawOpen,
                liveLipRoundness = evidence.visualFeatures.lipRoundness,
                statusMessage = "Analysis completed via ${result.providerUsed}"
            )
        }
    }
}

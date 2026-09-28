package com.pronunciationcoach.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pronunciationcoach.app.audio.IStandardAudioPlayer
import com.pronunciationcoach.app.audio.IUserAudioPlaybackEngine
import com.pronunciationcoach.app.audio.StandardAudioPlayer
import com.pronunciationcoach.app.audio.UserAudioPlaybackEngine
import com.pronunciationcoach.app.core.AcousticFeatureExtractor
import com.pronunciationcoach.app.core.PhonemeTemporalAligner
import com.pronunciationcoach.app.core.PronunciationCoreBridge
import com.pronunciationcoach.app.domain.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * UI State for the Pronunciation Coach Practice Screen.
 */
data class PracticeUiState(
    val targetWord: String = "think",
    val targetIpa: String = "/θɪŋk/",
    val selectedCategory: String = "齿间擦音 /θ, ð/",
    val wordDescription: String = "清齿间音：舌尖轻探门牙，吹出柔和清气流",
    val isRecording: Boolean = false,
    val isEvaluating: Boolean = false,
    val isFaceDetected: Boolean = false,
    val faceStatusText: String = "请将面部对准前置镜头",
    val liveJawOpen: Float = 0.38f,
    val liveLipRoundness: Float = 0.15f,
    val isPlayingStandard: Boolean = false,
    val isPlayingUserRecording: Boolean = false,
    val hasUserRecording: Boolean = false,
    val evaluationResult: ReasoningResult? = null,
    val selectedPhonemeSymbol: String? = null,
    val selectedProviderIndex: Int = 0, // 0 = Local Rule / Core, 1 = DeepSeek
    val statusMessage: String = "点击下方按钮开始练习"
) {
    /**
     * Currently active phoneme evaluation corresponding to selectedPhonemeSymbol.
     */
    val selectedPhoneme: PhonemeEvaluation?
        get() = evaluationResult?.phonemeEvaluations?.find { it.symbol == selectedPhonemeSymbol }
            ?: evaluationResult?.phonemeEvaluations?.firstOrNull()

    /**
     * Primary issue phoneme (lowest score or explicit primary issue).
     */
    val primaryIssuePhoneme: PhonemeEvaluation?
        get() = evaluationResult?.phonemeEvaluations?.find { it.isPrimaryIssue }
            ?: evaluationResult?.phonemeEvaluations?.minByOrNull { it.score }
}

class PracticeViewModel(
    private val context: Context? = null,
    private val localProvider: ReasoningProvider = LocalRuleProvider(),
    private val deepSeekProvider: ReasoningProvider = DeepSeekProvider(),
    audioPlayer: IStandardAudioPlayer? = null,
    playbackEngine: IUserAudioPlaybackEngine? = null,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val standardAudioPlayer: IStandardAudioPlayer? = audioPlayer ?: context?.let { StandardAudioPlayer(it) }
    private val userAudioPlaybackEngine: IUserAudioPlaybackEngine? = playbackEngine ?: context?.let { UserAudioPlaybackEngine(it) }

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private var activeAudioSource: AudioSource = MicrophoneAudioSource()
    private var activeVideoSource: VideoSource = CameraXVideoSource()

    // Scheme 3: Micro-change dampening timestamp
    private var lastFaceUpdateTimestamp = 0L

    init {
        // Run initial canonical analysis so the UI gracefully displays demonstration state
        runInitialDemonstration()
    }

    private fun runInitialDemonstration() {
        val defaultWord = DEFAULT_PRACTICE_WORDS.firstOrNull() ?: PracticeWord("think", "/θɪŋk/", "齿间擦音 /θ, ð/")
        _uiState.update {
            it.copy(
                targetWord = defaultWord.word,
                targetIpa = defaultWord.ipa,
                selectedCategory = defaultWord.category,
                wordDescription = defaultWord.description
            )
        }
    }

    /**
     * Selects a curated practice word from the library.
     */
    fun selectPracticeWord(word: PracticeWord) {
        stopAllAudio()
        _uiState.update {
            it.copy(
                targetWord = word.word,
                targetIpa = word.ipa,
                selectedCategory = word.category,
                wordDescription = word.description,
                evaluationResult = null,
                selectedPhonemeSymbol = null,
                hasUserRecording = false,
                statusMessage = "已切换到词汇: ${word.word}"
            )
        }
    }

    /**
     * Sets a user custom word and resolves its phonemes.
     */
    fun setCustomWord(wordText: String, ipaText: String? = null) {
        val clean = wordText.trim().lowercase()
        if (clean.isEmpty()) return

        stopAllAudio()
        val phonemes = PhonemeTemporalAligner.getPhonemesForWord(clean)
        val resolvedIpa = ipaText?.trim()?.takeIf { it.isNotEmpty() }
            ?: ("/" + phonemes.joinToString("") + "/")

        _uiState.update {
            it.copy(
                targetWord = clean,
                targetIpa = resolvedIpa,
                selectedCategory = "自定义练习",
                wordDescription = "自定义词汇练习: $clean",
                evaluationResult = null,
                selectedPhonemeSymbol = null,
                hasUserRecording = false,
                statusMessage = "已设置自定义词汇: $clean"
            )
        }
    }

    /**
     * Changes the selected phoneme to inspect its articulatory guidance.
     */
    fun selectPhoneme(symbol: String) {
        val clean = symbol.replace("/", "").trim()
        _uiState.update { it.copy(selectedPhonemeSymbol = clean) }
    }

    companion object {
        const val FACE_METRIC_DELTA_THRESHOLD = 0.02f
        const val FACE_UPDATE_INTERVAL_MS = 100L
    }

    /**
     * Updates live face and mouth tracking metrics from CameraX overlay.
     * Integrates Scheme 3: Micro-change dampening and time-based throttling to protect
     * Compose and ART GC from excessive recomposition.
     */
    fun updateFaceMetrics(
        jawOpen: Float,
        lipRoundness: Float,
        isFaceDetected: Boolean,
        currentTimeMs: Long = try {
            android.os.SystemClock.elapsedRealtime()
        } catch (e: Throwable) {
            System.currentTimeMillis()
        }
    ) {
        val current = _uiState.value
        val detectionChanged = (isFaceDetected != current.isFaceDetected)
        val jawDelta = abs(jawOpen - current.liveJawOpen)
        val lipDelta = abs(lipRoundness - current.liveLipRoundness)
        val motionSignificant = jawDelta >= FACE_METRIC_DELTA_THRESHOLD || lipDelta >= FACE_METRIC_DELTA_THRESHOLD
        val timeElapsed = (currentTimeMs - lastFaceUpdateTimestamp) >= FACE_UPDATE_INTERVAL_MS

        if (detectionChanged || motionSignificant || timeElapsed) {
            lastFaceUpdateTimestamp = currentTimeMs
            _uiState.update {
                it.copy(
                    liveJawOpen = jawOpen,
                    liveLipRoundness = lipRoundness,
                    isFaceDetected = isFaceDetected,
                    faceStatusText = if (isFaceDetected) "面部已对准" else "请将面部对准前置镜头"
                )
            }
        }
    }

    /**
     * Plays the native standard English audio via StandardAudioPlayer.
     */
    fun playStandardPronunciation() {
        val player = standardAudioPlayer ?: return
        val word = _uiState.value.targetWord

        if (_uiState.value.isPlayingStandard) {
            player.stop()
            _uiState.update { it.copy(isPlayingStandard = false) }
            return
        }

        stopUserRecording()

        _uiState.update { it.copy(isPlayingStandard = true) }
        player.playWord(
            text = word,
            onStart = {
                _uiState.update { it.copy(isPlayingStandard = true) }
            },
            onComplete = {
                _uiState.update { it.copy(isPlayingStandard = false) }
            }
        )
    }

    fun stopStandardPronunciation() {
        standardAudioPlayer?.stop()
        _uiState.update { it.copy(isPlayingStandard = false) }
    }

    /**
     * Plays back the user's recorded audio via UserAudioPlaybackEngine.
     */
    fun playUserRecording() {
        val engine = userAudioPlaybackEngine ?: return
        if (!engine.hasLastRecording()) return

        if (_uiState.value.isPlayingUserRecording) {
            engine.stopPlayback()
            _uiState.update { it.copy(isPlayingUserRecording = false) }
            return
        }

        stopStandardPronunciation()

        _uiState.update { it.copy(isPlayingUserRecording = true) }
        engine.playLastRecording(
            onStart = {
                _uiState.update { it.copy(isPlayingUserRecording = true) }
            },
            onComplete = {
                _uiState.update { it.copy(isPlayingUserRecording = false) }
            }
        )
    }

    fun stopUserRecording() {
        userAudioPlaybackEngine?.stopPlayback()
        _uiState.update { it.copy(isPlayingUserRecording = false) }
    }

    private fun stopAllAudio() {
        stopStandardPronunciation()
        stopUserRecording()
    }

    /**
     * Toggles live recording from microphone and camera.
     */
    fun toggleLiveRecording() {
        if (_uiState.value.isRecording) {
            stopLiveRecording()
        } else {
            startLiveRecording()
        }
    }

    fun startLiveRecording() {
        stopAllAudio()
        activeAudioSource = MicrophoneAudioSource()
        activeVideoSource = CameraXVideoSource()

        _uiState.update {
            it.copy(
                isRecording = true,
                isEvaluating = false,
                statusMessage = "正在录制中，请清晰发音: ${_uiState.value.targetWord}"
            )
        }

        viewModelScope.launch(coroutineDispatcher) {
            activeAudioSource.startRecording()
            activeVideoSource.startCapture()
        }
    }

    fun stopLiveRecording() {
        _uiState.update {
            it.copy(
                isRecording = false,
                isEvaluating = true,
                statusMessage = "正在进行多模态发音测评..."
            )
        }

        viewModelScope.launch(coroutineDispatcher) {
            val audioData = activeAudioSource.stopRecording()
            val videoData = activeVideoSource.stopCapture()

            // Cache recording for instant user playback
            userAudioPlaybackEngine?.saveRecording(audioData.pcmData, audioData.sampleRate)

            _uiState.update { it.copy(hasUserRecording = true) }

            evaluateAudioPcmInternal(
                pcmData = audioData.pcmData,
                jawOpen = videoData.averageJawOpen,
                lipRoundness = videoData.averageLipRoundness
            )
        }
    }

    /**
     * Evaluates PCM audio data against target word and visual features.
     */
    fun evaluateAudioPcm(
        pcmData: ByteArray,
        jawOpen: Float = _uiState.value.liveJawOpen,
        lipRoundness: Float = _uiState.value.liveLipRoundness
    ) {
        viewModelScope.launch(coroutineDispatcher) {
            evaluateAudioPcmInternal(pcmData, jawOpen, lipRoundness)
        }
    }

    private suspend fun evaluateAudioPcmInternal(
        pcmData: ByteArray,
        jawOpen: Float,
        lipRoundness: Float
    ) {
        _uiState.update { it.copy(isEvaluating = true) }

        val targetWord = _uiState.value.targetWord
        val targetIpa = _uiState.value.targetIpa

        val result = withContext(coroutineDispatcher) {
            // 1. Acoustic and temporal alignment
            val segments = PhonemeTemporalAligner.alignWord(pcmData, targetWord)
            val formants = AcousticFeatureExtractor.extractFormants(pcmData)
            val acuity = AcousticFeatureExtractor.analyzePcmBuffer(pcmData)

            // 2. Build multi-phoneme evidence JSON
            val evidenceJson = JSONObject().apply {
                put("target_word", targetWord)
                put("target_ipa", targetIpa)
                put("audio_rms", acuity.rmsEnergy)
                put("audio_snr", acuity.snrDb)
                put("formant_f1", formants.f1)
                put("formant_f2", formants.f2)
                put("jaw_open", jawOpen)
                put("lip_roundness", lipRoundness)

                val phonemesArray = JSONArray()
                for (seg in segments) {
                    val pObj = JSONObject().apply {
                        put("symbol", seg.phoneme)
                        put("start_ms", seg.startMs)
                        put("end_ms", seg.endMs)
                        put("detected_f1", formants.f1)
                        put("detected_f2", formants.f2)
                        put("detected_jaw_open", jawOpen)
                        put("detected_lip_roundness", lipRoundness)
                    }
                    phonemesArray.put(pObj)
                }
                put("phonemes", phonemesArray)
            }

            // 3. Score with Rust Core Bridge
            val coreResultJson = PronunciationCoreBridge.evaluatePhonemes(evidenceJson.toString())
            val coreEvaluations = parseCoreEvaluations(coreResultJson)

            // 4. Construct Multi-Phoneme Domain Model
            val multiPhonemeEvidence = MultiPhonemeEvidence(
                targetWord = targetWord,
                targetIpa = targetIpa,
                audioFeatures = AudioFeatures(
                    mfcc = floatArrayOf(acuity.rmsEnergy.toFloat(), formants.f1, formants.f2),
                    pitch = floatArrayOf(),
                    formants = floatArrayOf(formants.f1, formants.f2),
                    energy = acuity.rmsEnergy.toFloat(),
                    spectralCentroid = 1200f
                ),
                videoFeatures = VideoFeatures(
                    lipDistance = jawOpen,
                    jawDisplacement = jawOpen,
                    lipRoundness = lipRoundness,
                    tongueVisible = false,
                    isFaceDetected = true,
                    lipContour = listOf()
                ),
                phonemeEvidence = segments.mapIndexed { index, seg ->
                    val coreEval = coreEvaluations.getOrNull(index)
                    PhonemeEvidence(
                        phoneme = seg.phoneme,
                        standardIpa = "/${seg.phoneme}/",
                        startTime = seg.startMs.toFloat() / 1000f,
                        endTime = seg.endMs.toFloat() / 1000f,
                        audioScore = (coreEval?.score ?: 75) / 100f,
                        visualScore = (coreEval?.score ?: 75) / 100f,
                        detectedF1 = formants.f1,
                        detectedF2 = formants.f2,
                        detectedJawOpen = jawOpen,
                        detectedLipRoundness = lipRoundness
                    )
                }
            )

            // 5. Select Reasoning Provider
            val provider = if (_uiState.value.selectedProviderIndex == 1) {
                deepSeekProvider
            } else {
                localProvider
            }

            provider.reason(multiPhonemeEvidence)
        }

        _uiState.update {
            it.copy(
                isEvaluating = false,
                evaluationResult = result,
                selectedPhonemeSymbol = result.phonemeEvaluations.firstOrNull()?.symbol,
                statusMessage = "测评完成，综合得分: ${result.overallScore}"
            )
        }
    }

    private fun parseCoreEvaluations(jsonStr: String): List<PhonemeEvaluation> {
        val list = mutableListOf<PhonemeEvaluation>()
        try {
            val root = JSONObject(jsonStr)
            val arr = root.optJSONArray("evaluations") ?: return list
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                list.add(
                    PhonemeEvaluation(
                        symbol = item.optString("symbol", ""),
                        score = item.optInt("score", 70),
                        feedback = item.optString("feedback", ""),
                        isPrimaryIssue = item.optBoolean("is_primary_issue", false),
                        targetF1 = item.optDouble("target_f1", 0.0).toFloat(),
                        targetF2 = item.optDouble("target_f2", 0.0).toFloat(),
                        detectedF1 = item.optDouble("detected_f1", 0.0).toFloat(),
                        detectedF2 = item.optDouble("detected_f2", 0.0).toFloat(),
                        targetJawOpen = item.optDouble("target_jaw_open", 0.4).toFloat(),
                        detectedJawOpen = item.optDouble("detected_jaw_open", 0.4).toFloat(),
                        targetLipRoundness = item.optDouble("target_lip_roundness", 0.2).toFloat(),
                        detectedLipRoundness = item.optDouble("detected_lip_roundness", 0.2).toFloat()
                    )
                )
            }
        } catch (e: Exception) {
            // Graceful fallback
        }
        return list
    }
}

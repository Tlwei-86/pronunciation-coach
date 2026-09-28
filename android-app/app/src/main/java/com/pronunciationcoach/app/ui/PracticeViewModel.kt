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

    private var lastFaceUpdateTimestamp = 0L

    /**
     * Updates live face and mouth tracking metrics from CameraX overlay.
     * Integrates Scheme 3: Micro-change dampening and time-based throttling to protect
     * Compose and ART GC from excessive recomposition.
     */
    fun updateFaceMetrics(jawOpen: Float, lipRoundness: Float, isFaceDetected: Boolean) {
        val current = _uiState.value
        val now = try {
            android.os.SystemClock.elapsedRealtime()
        } catch (e: Throwable) {
            System.currentTimeMillis()
        }

        val statusFlipped = isFaceDetected != current.isFaceDetected
        val jawChangedSignificantly = kotlin.math.abs(jawOpen - current.liveJawOpen) >= 0.02f
        val roundnessChangedSignificantly = kotlin.math.abs(lipRoundness - current.liveLipRoundness) >= 0.02f
        val timeElapsed = (now - lastFaceUpdateTimestamp) >= 100L

        // Skip update if movement is within micro-jitter threshold and interval has not elapsed
        if (!statusFlipped && !jawChangedSignificantly && !roundnessChangedSignificantly && !timeElapsed) {
            return
        }

        lastFaceUpdateTimestamp = now
        _uiState.update {
            it.copy(
                liveJawOpen = jawOpen,
                liveLipRoundness = lipRoundness,
                isFaceDetected = isFaceDetected,
                faceStatusText = if (isFaceDetected) "面部已对准" else "请将面部对准前置镜头"
            )
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

            val durationMs = (audioData.pcmData.size / 32).toLong()
            val rms = calculatePcmRms(audioData.pcmData)

            // VAD Guard: Discard silence or faint background jitter (<250ms or RMS < 0.012)
            if (durationMs < MIN_AUDIO_DURATION_MS || rms < MIN_AUDIO_RMS_THRESHOLD) {
                _uiState.update {
                    it.copy(
                        isEvaluating = false,
                        evaluationResult = null,
                        selectedPhonemeSymbol = null,
                        hasUserRecording = false,
                        statusMessage = "未检测到有效声音，请靠近麦克风大声朗读"
                    )
                }
                return@launch
            }

            // Cache recording for instant user playback only if valid voice was detected
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
        val durationMs = (pcmData.size / 32).toLong()
        val rms = calculatePcmRms(pcmData)

        // VAD Guard: Discard silence or ambient noise
        if (durationMs < MIN_AUDIO_DURATION_MS || rms < MIN_AUDIO_RMS_THRESHOLD) {
            _uiState.update {
                it.copy(
                    isEvaluating = false,
                    evaluationResult = null,
                    selectedPhonemeSymbol = null,
                    hasUserRecording = false,
                    statusMessage = "未检测到有效声音，请靠近麦克风大声朗读"
                )
            }
            return
        }

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
                put("targetWord", targetWord)
                put("target_ipa", targetIpa)
                put("targetIpa", targetIpa)

                val phonemesArr = JSONArray()
                for (seg in segments) {
                    val segFeatures = AcousticFeatureExtractor.analyzePhonemeCategory(seg.pcmChunk, seg.phoneme)
                    val pObj = JSONObject().apply {
                        put("symbol", seg.phoneme)
                        put("phoneme", seg.phoneme)
                        put("audio", JSONObject().apply {
                            put("target_probability", (segFeatures.score / 100f).coerceIn(0.1f, 0.99f))
                            put("f1_hz", formants.f1)
                            put("f2_hz", formants.f2)
                            put("duration_ms", seg.endMs - seg.startMs)
                        })
                        put("visual", JSONObject().apply {
                            put("jaw_open", jawOpen)
                            put("lip_roundness", lipRoundness)
                        })
                    }
                    phonemesArr.put(pObj)
                }
                put("phonemes", phonemesArr)

                put("audio", JSONObject().apply {
                    put("target_probability", acuity.targetProb)
                    put("f1_hz", formants.f1)
                    put("f2_hz", formants.f2)
                    put("duration_ms", (pcmData.size / 32).toLong())
                })
                put("visual", JSONObject().apply {
                    put("jaw_open", jawOpen)
                    put("lip_roundness", lipRoundness)
                })
            }.toString()

            // 3. Score via PronunciationCoreBridge
            val resultJson = PronunciationCoreBridge.analyzeWordPronunciation(evidenceJson)
            ReasoningResult.fromJson(resultJson)
        }

        // Determine lowest-scoring phoneme as default selected phoneme
        val lowestPhoneme = result.phonemeEvaluations.minByOrNull { it.score }

        _uiState.update {
            it.copy(
                isEvaluating = false,
                evaluationResult = result,
                selectedPhonemeSymbol = lowestPhoneme?.symbol ?: result.phonemeEvaluations.firstOrNull()?.symbol,
                statusMessage = "测评完成: 得分 ${result.overallScore} 分"
            )
        }
    }

    fun selectProvider(index: Int) {
        _uiState.update { it.copy(selectedProviderIndex = index) }
    }

    /**
     * Automated test for Canonical /fʌŋk/ test WAV.
     */
    fun runCanonicalTestWav() {
        viewModelScope.launch(coroutineDispatcher) {
            _uiState.update {
                it.copy(
                    targetWord = "funk",
                    targetIpa = "/fʌŋk/",
                    selectedCategory = "唇齿擦音 /f, v/",
                    isEvaluating = true
                )
            }
            val audioSrc = WavFileAudioSource.createCanonicalFunk()
            val videoSrc = VideoFileSource.createCanonicalFunkVisual()
            audioSrc.startRecording()
            videoSrc.startCapture()
            val audioData = audioSrc.stopRecording()
            val videoData = videoSrc.stopCapture()

            userAudioPlaybackEngine?.saveRecording(audioData.pcmData)
            _uiState.update { it.copy(hasUserRecording = true) }

            evaluateAudioPcmInternal(
                pcmData = audioData.pcmData,
                jawOpen = videoData.averageJawOpen,
                lipRoundness = videoData.averageLipRoundness
            )
        }
    }

    /**
     * Automated test for Confused /fɑːŋk/ test WAV.
     */
    fun runConfusedTestWav() {
        viewModelScope.launch(coroutineDispatcher) {
            _uiState.update {
                it.copy(
                    targetWord = "funk",
                    targetIpa = "/fʌŋk/",
                    selectedCategory = "唇齿擦音 /f, v/",
                    isEvaluating = true
                )
            }
            val audioSrc = WavFileAudioSource.createConfusedAhFunk()
            val videoSrc = VideoFileSource.createConfusedAhVisual()
            audioSrc.startRecording()
            videoSrc.startCapture()
            val audioData = audioSrc.stopRecording()
            val videoData = videoSrc.stopCapture()

            userAudioPlaybackEngine?.saveRecording(audioData.pcmData)
            _uiState.update { it.copy(hasUserRecording = true) }

            evaluateAudioPcmInternal(
                pcmData = audioData.pcmData,
                jawOpen = videoData.averageJawOpen,
                lipRoundness = videoData.averageLipRoundness
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAllAudio()
        standardAudioPlayer?.release()
        userAudioPlaybackEngine?.release()
    }

    companion object {
        const val MIN_AUDIO_DURATION_MS = 250L
        const val MIN_AUDIO_RMS_THRESHOLD = 0.012f

        /**
         * Computes Root-Mean-Square (RMS) amplitude from 16-bit PCM Mono audio data.
         * Normalized scale: 0.0 (silent) to 1.0 (maximum amplitude).
         */
        fun calculatePcmRms(pcmData: ByteArray): Float {
            if (pcmData.size < 2) return 0f
            var sumSquares = 0.0
            val sampleCount = pcmData.size / 2
            for (i in 0 until sampleCount) {
                val sample = (pcmData[i * 2].toInt() and 0xFF) or (pcmData[i * 2 + 1].toInt() shl 8)
                val normalized = sample.toShort() / 32768.0
                sumSquares += normalized * normalized
            }
            return kotlin.math.sqrt(sumSquares / sampleCount).toFloat()
        }
    }
}

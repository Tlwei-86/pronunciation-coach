package com.pronunciationcoach.app

import com.pronunciationcoach.app.audio.IStandardAudioPlayer
import com.pronunciationcoach.app.audio.IUserAudioPlaybackEngine
import com.pronunciationcoach.app.audio.UserAudioPlaybackEngine
import com.pronunciationcoach.app.domain.DEFAULT_PRACTICE_WORDS
import com.pronunciationcoach.app.domain.LocalRuleProvider
import com.pronunciationcoach.app.domain.PhonemeEvaluation
import com.pronunciationcoach.app.domain.PracticeWord
import com.pronunciationcoach.app.ui.PracticeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FakeStandardAudioPlayer : IStandardAudioPlayer {
    override var isPlaying: Boolean = false
    var lastPlayedText: String? = null
    var pendingOnComplete: (() -> Unit)? = null
    var stopCallCount: Int = 0

    override fun playWord(text: String, onStart: (() -> Unit)?, onComplete: (() -> Unit)?) {
        lastPlayedText = text
        isPlaying = true
        pendingOnComplete = onComplete
        onStart?.invoke()
    }

    fun completePlayback() {
        isPlaying = false
        val comp = pendingOnComplete
        pendingOnComplete = null
        comp?.invoke()
    }

    override fun stop() {
        stopCallCount++
        isPlaying = false
        pendingOnComplete = null
    }

    override fun release() {
        stop()
    }
}

class FakeUserAudioPlaybackEngine : IUserAudioPlaybackEngine {
    override var isPlaying: Boolean = false
    var hasRecording: Boolean = false
    var lastSavedPcmSize: Int = 0
    var pendingOnComplete: (() -> Unit)? = null
    var stopCallCount: Int = 0

    override fun hasLastRecording(): Boolean = hasRecording

    override fun getLastRecordingFile(): File? = null

    override fun saveRecording(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): File? {
        hasRecording = true
        lastSavedPcmSize = pcmData.size
        return null
    }

    override fun playLastRecording(onStart: (() -> Unit)?, onComplete: (() -> Unit)?) {
        if (!hasRecording) {
            onComplete?.invoke()
            return
        }
        isPlaying = true
        pendingOnComplete = onComplete
        onStart?.invoke()
    }

    fun completePlayback() {
        isPlaying = false
        val comp = pendingOnComplete
        pendingOnComplete = null
        comp?.invoke()
    }

    override fun stopPlayback() {
        stopCallCount++
        isPlaying = false
        pendingOnComplete = null
    }

    override fun release() {
        stopPlayback()
        hasRecording = false
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeAudioPlayer = FakeStandardAudioPlayer()
    private val fakePlaybackEngine = FakeUserAudioPlaybackEngine()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        audioPlayer: IStandardAudioPlayer? = fakeAudioPlayer,
        playbackEngine: IUserAudioPlaybackEngine? = fakePlaybackEngine
    ) = PracticeViewModel(
        localProvider = LocalRuleProvider(),
        audioPlayer = audioPlayer,
        playbackEngine = playbackEngine,
        coroutineDispatcher = testDispatcher
    )

    @Test
    fun testInitialState() = runTest(testDispatcher) {\n        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("think", state.targetWord)
        assertEquals("/θɪŋk/", state.targetIpa)
        assertFalse(state.isRecording)
        assertFalse(state.isEvaluating)
        assertFalse(state.isPlayingStandard)
        assertFalse(state.isPlayingUserRecording)
        assertFalse(state.hasUserRecording)
    }

    @Test
    fun testSelectPracticeWord() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val sheep = DEFAULT_PRACTICE_WORDS.find { it.word == "sheep" }
        assertNotNull("Sheep should exist in curated library", sheep)

        viewModel.selectPracticeWord(sheep!!)
        val state = viewModel.uiState.value
        assertEquals("sheep", state.targetWord)
        assertEquals("/ʃiːp/", state.targetIpa)
        assertEquals("前元音与微笑音", state.selectedCategory)
        assertNull("Evaluation result should be cleared on word switch", state.evaluationResult)
        assertFalse("Recording cache flag should be reset on word switch", state.hasUserRecording)
    }

    @Test
    fun testSetCustomWord() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.setCustomWord("voice")
        val state = viewModel.uiState.value
        assertEquals("voice", state.targetWord)
        assertEquals("自定义练习", state.selectedCategory)
        assertTrue(state.targetIpa.contains("v"))
        assertFalse(state.hasUserRecording)
    }

    @Test
    fun testSelectPhoneme() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.selectPhoneme("θ")
        assertEquals("θ", viewModel.uiState.value.selectedPhonemeSymbol)

        viewModel.selectPhoneme("/ŋ/")
        assertEquals("ŋ", viewModel.uiState.value.selectedPhonemeSymbol)
    }

    @Test
    fun testUpdateFaceMetrics() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.updateFaceMetrics(jawOpen = 0.42f, lipRoundness = 0.20f, isFaceDetected = true)
        val state = viewModel.uiState.value
        assertEquals(0.42f, state.liveJawOpen, 0.001f)
        assertEquals(0.20f, state.liveLipRoundness, 0.001f)
        assertTrue(state.isFaceDetected)
        assertEquals("面部已对准", state.faceStatusText)
    }

    @Test
    fun testPlayStandardPronunciationLifecycle() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        assertEquals("think", viewModel.uiState.value.targetWord)
        assertFalse(viewModel.uiState.value.isPlayingStandard)

        // 1. Trigger play
        viewModel.playStandardPronunciation()
        assertTrue("Playing state should be true", viewModel.uiState.value.isPlayingStandard)
        assertEquals("think", fakeAudioPlayer.lastPlayedText)

        // 2. Complete play
        fakeAudioPlayer.completePlayback()
        assertFalse("Playing state should return to false upon completion", viewModel.uiState.value.isPlayingStandard)
    }

    @Test
    fun testPlayStandardPronunciationToggleStop() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // Start playback
        viewModel.playStandardPronunciation()
        assertTrue(viewModel.uiState.value.isPlayingStandard)

        // Click again while playing -> immediate stop
        val priorStopCount = fakeAudioPlayer.stopCallCount
        viewModel.playStandardPronunciation()
        assertFalse("Re-clicking during playback should stop playing immediately", viewModel.uiState.value.isPlayingStandard)
        assertTrue("stop() should be called on player", fakeAudioPlayer.stopCallCount > priorStopCount)
    }

    @Test
    fun testPlayUserRecordingLifecycle() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // 1. Cannot play when no recording exists
        fakePlaybackEngine.hasRecording = false
        viewModel.playUserRecording()
        assertFalse("Should not play if hasLastRecording is false", viewModel.uiState.value.isPlayingUserRecording)

        // 2. Simulate recording cached
        fakePlaybackEngine.hasRecording = true
        viewModel.playUserRecording()
        assertTrue("User recording playback should be active", viewModel.uiState.value.isPlayingUserRecording)

        // 3. Complete playback
        fakePlaybackEngine.completePlayback()
        assertFalse("Should return to false upon completion", viewModel.uiState.value.isPlayingUserRecording)
    }

    @Test
    fun testMutualExclusionBetweenStandardAndUserAudio() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()
        fakePlaybackEngine.hasRecording = true

        // A. Start standard audio -> then play user audio -> standard audio should be stopped
        viewModel.playStandardPronunciation()
        assertTrue(viewModel.uiState.value.isPlayingStandard)
        assertFalse(viewModel.uiState.value.isPlayingUserRecording)

        val priorStandardStops = fakeAudioPlayer.stopCallCount
        viewModel.playUserRecording()
        assertTrue("Standard audio should be stopped when user audio starts", fakeAudioPlayer.stopCallCount > priorStandardStops)
        assertTrue("User audio should be playing", viewModel.uiState.value.isPlayingUserRecording)

        // B. Now play standard audio while user audio is playing -> user audio should be stopped
        val priorUserStops = fakePlaybackEngine.stopCallCount
        viewModel.playStandardPronunciation()
        assertTrue("User audio should be stopped when standard audio starts", fakePlaybackEngine.stopCallCount > priorUserStops)
        assertTrue("Standard audio should be playing", viewModel.uiState.value.isPlayingStandard)
    }

    @Test
    fun testStartRecordingStopsAllAudioPlayback() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()
        fakePlaybackEngine.hasRecording = true

        // Start playback
        viewModel.playStandardPronunciation()
        assertTrue(viewModel.uiState.value.isPlayingStandard)

        // Starting recording must kill all audio channels immediately
        val priorStandardStops = fakeAudioPlayer.stopCallCount
        val priorUserStops = fakePlaybackEngine.stopCallCount
        viewModel.startLiveRecording()

        assertTrue(viewModel.uiState.value.isRecording)
        assertTrue("Standard player stopped", fakeAudioPlayer.stopCallCount > priorStandardStops)
        assertTrue("User playback engine stopped", fakePlaybackEngine.stopCallCount > priorUserStops)
    }

    @Test
    fun testWavHeaderBinaryValidity() {
        val sampleRate = 16000
        val channels = 1
        val bitsPerSample = 16
        val pcmBytes = ByteArray(32000) // 1 second of 16kHz 16-bit mono

        val header = UserAudioPlaybackEngine.createWavHeader(
            totalAudioLen = pcmBytes.size.toLong(),
            totalDataLen = (pcmBytes.size + 36).toLong(),
            longSampleRate = sampleRate.toLong(),
            channels = channels,
            byteRate = (sampleRate * channels * bitsPerSample / 8).toLong(),
            bitsPerSample = bitsPerSample
        )

        assertEquals("Header must be exactly 44 bytes", 44, header.size)

        // Check RIFF chunk
        assertEquals('R'.code.toByte(), header[0])
        assertEquals('I'.code.toByte(), header[1])
        assertEquals('F'.code.toByte(), header[2])
        assertEquals('F'.code.toByte(), header[3])

        // Check total data len at pos 4
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(32000 + 36, buf.getInt(4))

        // Check WAVE fmt
        assertEquals('W'.code.toByte(), header[8])
        assertEquals('A'.code.toByte(), header[9])
        assertEquals('V'.code.toByte(), header[10])
        assertEquals('E'.code.toByte(), header[11])

        // Subchunk1Size = 16
        assertEquals(16, buf.getInt(16))
        // AudioFormat = 1 (PCM)
        assertEquals(1.toShort(), buf.getShort(20))
        // Channels = 1
        assertEquals(1.toShort(), buf.getShort(22))
        // SampleRate = 16000
        assertEquals(16000, buf.getInt(24))
        // ByteRate = 32000
        assertEquals(32000, buf.getInt(28))
        // BlockAlign = 2
        assertEquals(2.toShort(), buf.getShort(32))
        // BitsPerSample = 16
        assertEquals(16.toShort(), buf.getShort(34))

        // data sub-chunk
        assertEquals('d'.code.toByte(), header[36])
        assertEquals('a'.code.toByte(), header[37])
        assertEquals('t'.code.toByte(), header[38])
        assertEquals('a'.code.toByte(), header[39])
        assertEquals(32000, buf.getInt(40))
    }

    @Test
    fun testRunCanonicalTestWav() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.runCanonicalTestWav()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isEvaluating)
        val eval = state.evaluationResult
        assertNotNull(eval)
        assertTrue(
            "Canonical test score should be high (>= 75), was: ${eval?.overallScore}",
            (eval?.overallScore ?: 0) >= 75
        )
        assertNotNull(state.selectedPhonemeSymbol)
        assertTrue("Canonical run should cache recording", state.hasUserRecording)
    }

    @Test
    fun testRunConfusedTestWav() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.runConfusedTestWav()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isEvaluating)
        val eval = state.evaluationResult
        assertNotNull(eval)
        assertTrue((eval?.overallScore ?: 0) > 0)
        assertTrue("Confused run should cache recording", state.hasUserRecording)
    }

    @Test
    fun testCuratedWordLibraryCoversCategories() {
        val categories = DEFAULT_PRACTICE_WORDS.map { it.category }.toSet()
        assertTrue("Should contain 齿间擦音", categories.any { it.contains("齿间") })
        assertTrue("Should contain 唇齿擦音", categories.any { it.contains("唇齿") })
        assertTrue("Should contain 卷舌与边音", categories.any { it.contains("卷舌") })
        assertTrue("Should contain 前元音与微笑音", categories.any { it.contains("前元音") })
        assertTrue("Should contain 央后元音", categories.any { it.contains("央后元音") })
        assertTrue("Should contain 塞音爆破", categories.any { it.contains("塞音") })
        assertTrue("Should contain 鼻音", categories.any { it.contains("鼻音") })
        assertTrue("Should contain 双元音", categories.any { it.contains("双元音") })
    }

    @Test
    fun testPhonemeEvaluationEnrichedWithChineseGuidance() {
        val json = JSONObject().apply {
            put("symbol", "θ")
            put("score", 70)
            put("status", "WARNING")
        }
        val eval = PhonemeEvaluation.fromJsonObject(json)
        assertEquals("θ", eval.symbol)
        assertEquals("/θ/", eval.ipa)
        assertEquals(70, eval.score)
        assertTrue("Should contain Chinese standard action", eval.standardAction.isNotEmpty())
        assertTrue("Standard action should mention 门牙 or 舌尖", eval.standardAction.contains("门牙") || eval.standardAction.contains("舌尖"))
        assertTrue("Should contain action cues", eval.actionCues.isNotEmpty())
    }

    @Test
    fun testSelectProvider() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.selectProvider(1)
        assertEquals(1, viewModel.uiState.value.selectedProviderIndex)
    }
}

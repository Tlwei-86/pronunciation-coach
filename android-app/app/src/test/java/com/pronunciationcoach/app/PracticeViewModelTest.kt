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
    override var isInitialized: Boolean = true
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
    fun testInitialState() = runTest(testDispatcher) {
        val viewModel = createViewModel()
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
        val pcmData = ByteArray(3200) // 100ms dummy PCM audio

        val header = UserAudioPlaybackEngine.createWavHeader(
            totalAudioLen = pcmData.size.toLong(),
            totalDataLen = (pcmData.size + 36).toLong(),
            longSampleRate = sampleRate.toLong(),
            channels = channels,
            byteRate = (sampleRate * channels * bitsPerSample / 8).toLong(),
            bitsPerSample = bitsPerSample
        )

        assertEquals("Header must be exactly 44 bytes", 44, header.size)

        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val riffTag = String(header, 0, 4)
        assertEquals("RIFF", riffTag)

        val totalDataLen = buffer.getInt(4)
        assertEquals(pcmData.size + 36, totalDataLen)

        val waveTag = String(header, 8, 4)
        assertEquals("WAVE", waveTag)

        val fmtTag = String(header, 12, 4)
        assertEquals("fmt ", fmtTag)

        val audioFormat = buffer.getShort(20).toInt()
        assertEquals("PCM format code must be 1", 1, audioFormat)

        val numChannels = buffer.getShort(22).toInt()
        assertEquals(channels, numChannels)

        val rate = buffer.getInt(24)
        assertEquals(sampleRate, rate)

        val byteRate = buffer.getInt(28)
        assertEquals(sampleRate * channels * (bitsPerSample / 8), byteRate)

        val blockAlign = buffer.getShort(32).toInt()
        assertEquals(channels * (bitsPerSample / 8), blockAlign)

        val bits = buffer.getShort(34).toInt()
        assertEquals(bitsPerSample, bits)

        val dataTag = String(header, 36, 4)
        assertEquals("data", dataTag)

        val audioLen = buffer.getInt(40)
        assertEquals(pcmData.size, audioLen)
    }
}

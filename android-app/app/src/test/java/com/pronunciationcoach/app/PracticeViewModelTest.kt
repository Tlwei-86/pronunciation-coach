package com.pronunciationcoach.app

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

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = PracticeViewModel(
        localProvider = LocalRuleProvider(),
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

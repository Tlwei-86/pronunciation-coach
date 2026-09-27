package com.pronunciationcoach.app

import com.pronunciationcoach.app.domain.LocalRuleProvider
import com.pronunciationcoach.app.ui.PracticeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
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

    @Test
    fun testInitialState() = runTest(testDispatcher) {
        val viewModel = PracticeViewModel(localProvider = LocalRuleProvider())
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("funk", state.targetWord)
        assertEquals("/fʌŋk/", state.targetIpa)
        assertFalse(state.isRecording)
        assertFalse(state.isEvaluating)
        assertNotNull(state.evaluationResult)
    }

    @Test
    fun testRunCanonicalTestWav() = runTest(testDispatcher) {
        val viewModel = PracticeViewModel(localProvider = LocalRuleProvider())
        testScheduler.advanceUntilIdle()

        viewModel.runCanonicalTestWav()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isEvaluating)
        val eval = state.evaluationResult
        assertNotNull(eval)
        assertTrue("Canonical /fʌŋk/ score should be high (>= 80), was: ${eval?.overallScore}", (eval?.overallScore ?: 0) >= 80)
    }

    @Test
    fun testRunConfusedTestWav() = runTest(testDispatcher) {
        val viewModel = PracticeViewModel(localProvider = LocalRuleProvider())
        testScheduler.advanceUntilIdle()

        viewModel.runConfusedTestWav()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isEvaluating)
        val eval = state.evaluationResult
        assertNotNull(eval)
        assertEquals("/ɑ/", eval?.detectedConfusion)
    }

    @Test
    fun testSelectProvider() = runTest(testDispatcher) {
        val viewModel = PracticeViewModel(localProvider = LocalRuleProvider())
        viewModel.selectProvider(1)
        assertEquals(1, viewModel.uiState.value.selectedProviderIndex)
    }
}

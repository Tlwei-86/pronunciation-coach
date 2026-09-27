package com.pronunciationcoach.app

import com.pronunciationcoach.app.domain.VideoFileSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VideoSourceTest {

    @Test
    fun testCanonicalFunkVideoSource() = runBlocking {
        val videoSource = VideoFileSource.createCanonicalFunkVisual()
        assertTrue("Video source should be available", videoSource.isAvailable)

        videoSource.startCapture()
        val sampleData = videoSource.stopCapture()

        assertTrue("Frames list should not be empty", sampleData.frameMetricsList.isNotEmpty())
        // Jaw openness for canonical /ʌ/ should be moderate (~0.35..0.45)
        assertTrue(
            "Average jaw openness should be within expected range for /ʌ/ (${sampleData.averageJawOpen})",
            sampleData.averageJawOpen in 0.30f..0.45f
        )
        // Lip roundness should be unrounded (< 0.25)
        assertTrue(
            "Average lip roundness should be unrounded (${sampleData.averageLipRoundness})",
            sampleData.averageLipRoundness < 0.25f
        )
    }

    @Test
    fun testConfusedAhFunkVideoSource() = runBlocking {
        val videoSource = VideoFileSource.createConfusedAhVisual()
        assertTrue("Video source should be available", videoSource.isAvailable)

        videoSource.startCapture()
        val sampleData = videoSource.stopCapture()

        // Confused /ɑ/ has excessive jaw opening (> 0.50)
        assertTrue(
            "Jaw openness for /ɑ/ confusion should be high (${sampleData.averageJawOpen})",
            sampleData.averageJawOpen > 0.50f
        )
    }
}

package com.pronunciationcoach.app.domain

interface VideoSource {
    val sourceName: String
    val isAvailable: Boolean
        get() = true

    suspend fun startCapture()
    suspend fun stopCapture(): VisualSampleData
}

class CameraXVideoSource : VideoSource {
    override val sourceName: String = "CameraX Front Camera"
    override val isAvailable: Boolean = true

    override suspend fun startCapture() {}

    override suspend fun stopCapture(): VisualSampleData {
        val frames = listOf(
            VisualFrameMetrics(System.currentTimeMillis(), 0.38f, 0.15f)
        )
        return VisualSampleData(
            frameMetricsList = frames,
            averageJawOpen = 0.38f,
            averageLipRoundness = 0.15f,
            sourceDescription = sourceName
        )
    }
}

class VideoFileSource(
    val fileName: String,
    override val sourceName: String,
    private val jawOpen: Float,
    private val lipRoundness: Float
) : VideoSource {
    override val isAvailable: Boolean = true

    override suspend fun startCapture() {}

    override suspend fun stopCapture(): VisualSampleData {
        val frames = (0..5).map { idx ->
            VisualFrameMetrics(
                timestampMs = idx * 100L,
                jawOpen = jawOpen,
                lipRoundness = lipRoundness
            )
        }
        return VisualSampleData(
            frameMetricsList = frames,
            averageJawOpen = jawOpen,
            averageLipRoundness = lipRoundness,
            sourceDescription = sourceName
        )
    }

    companion object {
        fun createCanonicalFunkVisual(): VideoFileSource {
            return VideoFileSource(
                fileName = "funk_good_visual.mp4",
                sourceName = "Visual Reference: Canonical /ʌ/",
                jawOpen = 0.38f,
                lipRoundness = 0.15f
            )
        }

        fun createConfusedAhVisual(): VideoFileSource {
            return VideoFileSource(
                fileName = "funk_ah_visual.mp4",
                sourceName = "Visual Reference: Over-opened /ɑ/",
                jawOpen = 0.65f,
                lipRoundness = 0.20f
            )
        }
    }
}

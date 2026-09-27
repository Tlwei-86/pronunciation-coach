package com.pronunciationcoach.app.domain

data class MouthFeatures(
    val jawOpen: Float,
    val lipRoundness: Float,
    val mouthWidth: Float = 0.55f,
    val timestampMs: Long = System.currentTimeMillis()
)

interface VideoSource {
    val sourceName: String
    suspend fun startPreview()
    suspend fun captureCurrentFeatures(): MouthFeatures
    suspend fun stopPreview()
}

class CameraXVideoSource : VideoSource {
    override val sourceName: String = "CameraX Front Camera"
    override suspend fun startPreview() {}
    override suspend fun captureCurrentFeatures(): MouthFeatures = MouthFeatures(jawOpen = 0.50f, lipRoundness = 0.12f)
    override suspend fun stopPreview() {}
}

class VideoFileSource(val fileName: String) : VideoSource {
    override val sourceName: String = "MP4 Source: \$fileName"
    override suspend fun startPreview() {}
    override suspend fun captureCurrentFeatures(): MouthFeatures = MouthFeatures(jawOpen = 0.72f, lipRoundness = 0.08f)
    override suspend fun stopPreview() {}
}

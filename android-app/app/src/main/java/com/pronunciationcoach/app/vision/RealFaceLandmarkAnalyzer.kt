package com.pronunciationcoach.app.vision

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs
import kotlin.math.max

data class LiveFaceMouthMetrics(
    val jawOpen: Float,
    val lipRoundness: Float,
    val mouthWidthNormalized: Float,
    val isFaceDetected: Boolean,
    val statusText: String,
    val lipContourPoints: List<android.graphics.PointF> = emptyList(),
    val sourceImageWidth: Int = 0,
    val sourceImageHeight: Int = 0,
    val isFrontCamera: Boolean = true,
    val lipClosure: Float = 0f,
    val mouthStretch: Float = 0f
)

class RealFaceLandmarkAnalyzer(
    private val onMetricsUpdated: (LiveFaceMouthMetrics) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val INTERVAL_RECORDING_MS = 80L        // High performance (~12.5 FPS)
        private const val INTERVAL_IDLE_FACE_MS = 200L       // Idle preview with face detected (~5 FPS)
        private const val INTERVAL_STANDBY_NO_FACE_MS = 600L // Standby heartbeat (~1.6 FPS)
        private const val NO_FACE_TIMEOUT_MS = 2000L         // Timeout before falling back to standby
    }

    @Volatile
    private var isRecordingActive: Boolean = false

    fun setRecordingActive(active: Boolean) {
        isRecordingActive = active
    }

    // Configure Face Detector for detailed landmarks (Mouth left/right/bottom, Cheeks) and Contours
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .build()

    private val detector = FaceDetection.getClient(options)

    @Volatile
    private var isProcessing = false

    @Volatile
    private var lastProcessedTimestamp = 0L

    @Volatile
    private var lastFaceDetectedTimestamp = SystemClock.elapsedRealtime()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()

        // Scheme 2: Smart Adaptive Multi-tier FPS
        val targetInterval = when {
            isRecordingActive -> INTERVAL_RECORDING_MS
            (now - lastFaceDetectedTimestamp) > NO_FACE_TIMEOUT_MS -> INTERVAL_STANDBY_NO_FACE_MS
            else -> INTERVAL_IDLE_FACE_MS
        }

        // Scheme 1: Timestamp Throttling & concurrency check
        if (isProcessing || (now - lastProcessedTimestamp < targetInterval)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isProcessing = true
        lastProcessedTimestamp = now

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
        val imgWidth = if (isRotated) imageProxy.height else imageProxy.width
        val imgHeight = if (isRotated) imageProxy.width else imageProxy.height

        try {
            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isEmpty()) {
                        onMetricsUpdated(
                            LiveFaceMouthMetrics(
                                jawOpen = 0.40f,
                                lipRoundness = 0.10f,
                                mouthWidthNormalized = 0.45f,
                                isFaceDetected = false,
                                statusText = "未检测到人脸，请正对屏幕",
                                lipContourPoints = emptyList(),
                                sourceImageWidth = imgWidth,
                                sourceImageHeight = imgHeight,
                                isFrontCamera = true,
                                lipClosure = 0f,
                                mouthStretch = 0f
                            )
                        )
                    } else {
                        lastFaceDetectedTimestamp = SystemClock.elapsedRealtime()
                        val face = faces[0]
                        val metrics = calculateMouthMetrics(
                            face = face,
                            imageWidth = imgWidth,
                            imageHeight = imgHeight,
                            isFrontCamera = true
                        )
                        onMetricsUpdated(metrics)
                    }
                }
                .addOnFailureListener { e ->
                    println("[RealFaceLandmarkAnalyzer] Error: ${e.message}")
                }
                .addOnCompleteListener {
                    isProcessing = false
                    imageProxy.close()
                }
        } catch (e: Exception) {
            println("[RealFaceLandmarkAnalyzer] Exception during processing: ${e.message}")
            isProcessing = false
            imageProxy.close()
        }
    }

    fun close() {
        try {
            detector.close()
        } catch (e: Exception) {
            // Ignored
        }
    }

    private fun calculateMouthMetrics(
        face: Face,
        imageWidth: Int = 0,
        imageHeight: Int = 0,
        isFrontCamera: Boolean = true
    ): LiveFaceMouthMetrics {
        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position

        // Extract detailed 2D lip contour points
        val lipPoints = mutableListOf<android.graphics.PointF>()
        val upperLipBottomPoints = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points
        val lowerLipTopPoints = face.getContour(FaceContour.LOWER_LIP_TOP)?.points
        face.getContour(FaceContour.UPPER_LIP_TOP)?.points?.let { lipPoints.addAll(it) }
        upperLipBottomPoints?.let { lipPoints.addAll(it) }
        lowerLipTopPoints?.let { lipPoints.addAll(it) }
        face.getContour(FaceContour.LOWER_LIP_BOTTOM)?.points?.let { lipPoints.addAll(it) }

        val boundingBox = face.boundingBox
        val faceHeight = max(100f, boundingBox.height().toFloat())
        val faceWidth = max(100f, boundingBox.width().toFloat())

        if (mouthBottom != null && noseBase != null && mouthLeft != null && mouthRight != null) {
            // 1. Vertical jaw opening distance normalized against distance between nose base and mouth bottom
            val verticalJawDist = abs(mouthBottom.y - noseBase.y)
            val normalizedJaw = (verticalJawDist / faceHeight * 2.8f).coerceIn(0.10f, 0.95f)

            // 2. Horizontal mouth width
            val mouthWidth = abs(mouthRight.x - mouthLeft.x)
            val normalizedWidth = (mouthWidth / faceWidth * 1.5f).coerceIn(0.20f, 0.85f)

            // 3. Lip roundness (ratio of vertical opening to horizontal width)
            val roundness = (normalizedJaw / (normalizedWidth + 0.001f) * 0.25f).coerceIn(0.05f, 0.60f)

            // 4. Bilabial lip closure (for /p, b, m/)
            val innerLipGap = if (!upperLipBottomPoints.isNullOrEmpty() && !lowerLipTopPoints.isNullOrEmpty()) {
                val midUpper = upperLipBottomPoints[upperLipBottomPoints.size / 2].y
                val midLower = lowerLipTopPoints[lowerLipTopPoints.size / 2].y
                max(0f, midLower - midUpper)
            } else {
                max(0f, verticalJawDist - faceHeight * 0.14f)
            }
            val lipClosure = (1.0f - (innerLipGap / (faceHeight * 0.12f))).coerceIn(0.0f, 1.0f)

            // 5. Mouth stretch tension (smile tension for /iː/)
            val interOcularDist = if (leftEye != null && rightEye != null) {
                abs(rightEye.x - leftEye.x)
            } else {
                faceWidth * 0.42f
            }
            val mouthStretch = ((mouthWidth / (interOcularDist + 0.001f) - 0.70f) / 0.50f).coerceIn(0.0f, 1.0f)

            val status = when {
                lipClosure > 0.82f -> "🟢 双唇紧闭 (/p, b, m/ 准备就绪)"
                mouthStretch > 0.75f -> "🟢 嘴角展宽微笑 (/iː/ 展唇)"
                normalizedJaw > 0.60f -> "⚠️ 下巴张开过大 (类似 /ɑ/)"
                normalizedJaw < 0.25f -> "口型微闭 / 齿音"
                else -> "🟢 口型居中放松 (标准 /ʌ/)"
            }

            return LiveFaceMouthMetrics(
                jawOpen = normalizedJaw,
                lipRoundness = roundness,
                mouthWidthNormalized = normalizedWidth,
                isFaceDetected = true,
                statusText = status,
                lipContourPoints = lipPoints,
                sourceImageWidth = imageWidth,
                sourceImageHeight = imageHeight,
                isFrontCamera = isFrontCamera,
                lipClosure = lipClosure,
                mouthStretch = mouthStretch
            )
        } else {
            // Face detected but landmarks partially occluded
            return LiveFaceMouthMetrics(
                jawOpen = 0.40f,
                lipRoundness = 0.12f,
                mouthWidthNormalized = 0.45f,
                isFaceDetected = true,
                statusText = "检测到面部，请调整头部角度",
                lipContourPoints = lipPoints,
                sourceImageWidth = imageWidth,
                sourceImageHeight = imageHeight,
                isFrontCamera = isFrontCamera,
                lipClosure = 0f,
                mouthStretch = 0f
            )
        }
    }
}

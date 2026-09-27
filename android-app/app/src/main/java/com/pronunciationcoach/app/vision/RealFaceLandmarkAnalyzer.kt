package com.pronunciationcoach.app.vision

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
    val isFrontCamera: Boolean = true
)

class RealFaceLandmarkAnalyzer(
    private val onMetricsUpdated: (LiveFaceMouthMetrics) -> Unit
) : ImageAnalysis.Analyzer {

    // Configure Face Detector for detailed landmarks (Mouth left/right/bottom, Cheeks) and Contours
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .build()

    private val detector = FaceDetection.getClient(options)
    private var isProcessing = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || isProcessing) {
            imageProxy.close()
            return
        }

        isProcessing = true
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
        val imgWidth = if (isRotated) imageProxy.height else imageProxy.width
        val imgHeight = if (isRotated) imageProxy.width else imageProxy.height

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
                            isFrontCamera = true
                        )
                    )
                } else {
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

        // Extract detailed 2D lip contour points
        val lipPoints = mutableListOf<android.graphics.PointF>()
        face.getContour(FaceContour.UPPER_LIP_TOP)?.points?.let { lipPoints.addAll(it) }
        face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points?.let { lipPoints.addAll(it) }
        face.getContour(FaceContour.LOWER_LIP_TOP)?.points?.let { lipPoints.addAll(it) }
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

            val status = when {
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
                isFrontCamera = isFrontCamera
            )
        } else {
            // Face detected but landmarks partially occluded
            return LiveFaceMouthMetrics(
                jawOpen = 0.40f,
                lipRoundness = 0.12f,
                mouthWidthNormalized = 0.45f,
                isFaceDetected = true,
                statusText = "已检测到面部 (微调光线与距离)",
                lipContourPoints = lipPoints,
                sourceImageWidth = imageWidth,
                sourceImageHeight = imageHeight,
                isFrontCamera = isFrontCamera
            )
        }
    }
}

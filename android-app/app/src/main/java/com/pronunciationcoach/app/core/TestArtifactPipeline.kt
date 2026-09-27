package com.pronunciationcoach.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.pronunciationcoach.app.vision.LiveFaceMouthMetrics
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max

object TestArtifactPipeline {

    /**
     * Reads a raw 16kHz WAV resource, skips 44-byte RIFF header, and returns raw 16-bit PCM bytes.
     */
    fun readPcmFromRawResource(context: Context, resId: Int): ByteArray {
        val inputStream: InputStream = context.resources.openRawResource(resId)
        val allBytes = inputStream.readBytes()
        inputStream.close()
        // Standard PCM WAV header is 44 bytes
        return if (allBytes.size > 44) {
            allBytes.copyOfRange(44, allBytes.size)
        } else {
            allBytes
        }
    }

    /**
     * Loads a test face image from assets and passes it through Google ML Kit Face Landmarker
     * to extract real geometric jaw and lip metrics.
     */
    suspend fun analyzeTestImageFromAssets(
        context: Context,
        assetPath: String
    ): LiveFaceMouthMetrics = suspendCancellableCoroutine { cont ->
        try {
            val inputStream = context.assets.open(assetPath)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                cont.resume(
                    LiveFaceMouthMetrics(0.40f, 0.12f, 0.45f, false, "Image decode failed")
                )
                return@suspendCancellableCoroutine
            }

            val image = InputImage.fromBitmap(bitmap, 0)
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build()

            val detector = FaceDetection.getClient(options)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isEmpty()) {
                        cont.resume(
                            LiveFaceMouthMetrics(0.40f, 0.12f, 0.45f, false, "未在测试图片中检测到面部")
                        )
                    } else {
                        val metrics = calculateMetricsFromFace(faces[0])
                        cont.resume(metrics)
                    }
                }
                .addOnFailureListener { e ->
                    cont.resume(
                        LiveFaceMouthMetrics(0.40f, 0.12f, 0.45f, false, "Error: ${e.message}")
                    )
                }
        } catch (e: Exception) {
            cont.resume(
                LiveFaceMouthMetrics(0.40f, 0.12f, 0.45f, false, "Exception: ${e.message}")
            )
        }
    }

    private fun calculateMetricsFromFace(face: Face): LiveFaceMouthMetrics {
        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)?.position

        val boundingBox = face.boundingBox
        val faceHeight = max(100f, boundingBox.height().toFloat())
        val faceWidth = max(100f, boundingBox.width().toFloat())

        if (mouthBottom != null && noseBase != null && mouthLeft != null && mouthRight != null) {
            val verticalJawDist = abs(mouthBottom.y - noseBase.y)
            val normalizedJaw = (verticalJawDist / faceHeight * 2.8f).coerceIn(0.10f, 0.95f)

            val mouthWidth = abs(mouthRight.x - mouthLeft.x)
            val normalizedWidth = (mouthWidth / faceWidth * 1.5f).coerceIn(0.20f, 0.85f)
            val roundness = (normalizedJaw / (normalizedWidth + 0.001f) * 0.25f).coerceIn(0.05f, 0.60f)

            val status = when {
                normalizedJaw > 0.60f -> "⚠️ 物理大开口 (判定偏向 /ɑ/)"
                normalizedJaw < 0.25f -> "口型微闭 / 齿音"
                else -> "🟢 居中标准开口 (符合 /ʌ/)"
            }

            return LiveFaceMouthMetrics(
                jawOpen = normalizedJaw,
                lipRoundness = roundness,
                mouthWidthNormalized = normalizedWidth,
                isFaceDetected = true,
                statusText = status
            )
        } else {
            return LiveFaceMouthMetrics(
                jawOpen = 0.40f,
                lipRoundness = 0.12f,
                mouthWidthNormalized = 0.45f,
                isFaceDetected = true,
                statusText = "关键点部分被遮挡"
            )
        }
    }
}

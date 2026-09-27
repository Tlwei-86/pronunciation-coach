package com.pronunciationcoach.app.ui.components

import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.pronunciationcoach.app.vision.LiveFaceMouthMetrics
import kotlin.math.max
import kotlin.math.min

/**
 * High-tech HUD Overlay for real-time lip tracking and articulatory aperture visualization.
 *
 * Dynamically renders:
 * 1. Facial contour mesh (or synthesized wireframe if landmarks are occluding).
 * 2. Articulatory color grading according to jaw opening:
 *    - 0.18f .. 0.28f -> Neon Green (#00E676) with caliper lines (Optimal /ʌ/ range)
 *    - 0.28f .. 0.38f -> Amber Gold (#FFD54F) (Transitional aperture)
 *    - > 0.38f        -> Crimson Red (#FF5252) (Over-opened /ɑ/ confusion)
 * 3. Targeting reticle with corner brackets and crosshairs, dynamically anchoring to the tracked mouth.
 * 4. Vertical caliper gauge displaying normalized jaw opening percentage.
 */
@Composable
fun MouthTrackingOverlay(
    metrics: LiveFaceMouthMetrics,
    modifier: Modifier = Modifier
) {
    // Dynamic color selection based on jaw opening
    val targetColor = when {
        metrics.jawOpen in 0.18f..0.28f -> Color(0xFF00E676) // Neon Green
        metrics.jawOpen in 0.28f..0.38f -> Color(0xFFFFD54F) // Amber Gold
        metrics.jawOpen > 0.38f -> Color(0xFFFF5252)         // Crimson Red
        else -> Color(0xFF00E676)                            // Closed / Neutral
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 180),
        label = "hudColorAnim"
    )

    val nativeTextPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val canvasCenterX = w / 2f
        val canvasCenterY = h / 2f

        // 1. Draw Lip Mesh (Real ML Kit contour points mapped to face or synthesized articulatory mesh)
        val lipCenter = drawLipMesh(
            contourPoints = metrics.lipContourPoints,
            sourceWidth = metrics.sourceImageWidth,
            sourceHeight = metrics.sourceImageHeight,
            isFrontCamera = metrics.isFrontCamera,
            jawOpen = metrics.jawOpen,
            roundness = metrics.lipRoundness,
            w = w,
            h = h,
            canvasCenterX = canvasCenterX,
            canvasCenterY = canvasCenterY,
            hudColor = animatedColor
        )

        // 2. Draw Target Reticle around tracked mouth center (or canvas center if not tracked)
        val reticleX = lipCenter?.x ?: canvasCenterX
        val reticleY = lipCenter?.y ?: canvasCenterY
        drawTargetReticle(reticleX, reticleY, animatedColor)

        // 3. Draw Vertical Caliper Gauge on the right
        drawCaliperGauge(
            w = w,
            h = h,
            jawOpen = metrics.jawOpen,
            hudColor = animatedColor,
            paint = nativeTextPaint
        )
    }
}

/**
 * Draws HUD reticle around the central oral cavity area.
 */
private fun DrawScope.drawTargetReticle(
    centerX: Float,
    centerY: Float,
    hudColor: Color
) {
    val boxWidth = 220.dp.toPx()
    val boxHeight = 160.dp.toPx()
    val left = centerX - boxWidth / 2f
    val top = centerY - boxHeight / 2f
    val right = centerX + boxWidth / 2f
    val bottom = centerY + boxHeight / 2f

    val bracketLen = 24.dp.toPx()
    val strokeWidth = 2.dp.toPx()

    val bracketColor = hudColor.copy(alpha = 0.85f)

    // Corner brackets
    // Top-Left
    drawLine(color = bracketColor, start = Offset(left, top), end = Offset(left + bracketLen, top), strokeWidth = strokeWidth)
    drawLine(color = bracketColor, start = Offset(left, top), end = Offset(left, top + bracketLen), strokeWidth = strokeWidth)

    // Top-Right
    drawLine(color = bracketColor, start = Offset(right, top), end = Offset(right - bracketLen, top), strokeWidth = strokeWidth)
    drawLine(color = bracketColor, start = Offset(right, top), end = Offset(right, top + bracketLen), strokeWidth = strokeWidth)

    // Bottom-Left
    drawLine(color = bracketColor, start = Offset(left, bottom), end = Offset(left + bracketLen, bottom), strokeWidth = strokeWidth)
    drawLine(color = bracketColor, start = Offset(left, bottom), end = Offset(left, bottom - bracketLen), strokeWidth = strokeWidth)

    // Bottom-Right
    drawLine(color = bracketColor, start = Offset(right, bottom), end = Offset(right - bracketLen, bottom), strokeWidth = strokeWidth)
    drawLine(color = bracketColor, start = Offset(right, bottom), end = Offset(right, bottom - bracketLen), strokeWidth = strokeWidth)

    // Center Crosshairs
    val crossHairSize = 10.dp.toPx()
    drawLine(
        color = hudColor.copy(alpha = 0.45f),
        start = Offset(centerX - crossHairSize, centerY),
        end = Offset(centerX + crossHairSize, centerY),
        strokeWidth = 1.5.dp.toPx()
    )
    drawLine(
        color = hudColor.copy(alpha = 0.45f),
        start = Offset(centerX, centerY - crossHairSize),
        end = Offset(centerX, centerY + crossHairSize),
        strokeWidth = 1.5.dp.toPx()
    )
}

/**
 * Draws dynamic lip contours either from real ML Kit points or synthesized articulatory mesh.
 * Returns the tracked center of the mouth, or null if relying on synthesized mesh.
 */
private fun DrawScope.drawLipMesh(
    contourPoints: List<PointF>,
    sourceWidth: Int,
    sourceHeight: Int,
    isFrontCamera: Boolean,
    jawOpen: Float,
    roundness: Float,
    w: Float,
    h: Float,
    canvasCenterX: Float,
    canvasCenterY: Float,
    hudColor: Color
): Offset? {
    if (contourPoints.size >= 8) {
        // Project points onto Canvas
        val projectedPoints: List<Offset> = if (sourceWidth > 0 && sourceHeight > 0) {
            // CameraX PreviewView FILL_CENTER coordinate transform
            val imgW = sourceWidth.toFloat()
            val imgH = sourceHeight.toFloat()
            val scale = max(w / imgW, h / imgH)
            val offsetX = (w - imgW * scale) / 2f
            val offsetY = (h - imgH * scale) / 2f

            contourPoints.map { pt ->
                val px = if (isFrontCamera) (imgW - pt.x) else pt.x
                val py = pt.y
                Offset(
                    x = offsetX + px * scale,
                    y = offsetY + py * scale
                )
            }
        } else {
            // Graceful fallback to centered normalized bounding box
            var minX = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var minY = Float.MAX_VALUE
            var maxY = Float.MIN_VALUE

            for (pt in contourPoints) {
                if (pt.x < minX) minX = pt.x
                if (pt.x > maxX) maxX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.y > maxY) maxY = pt.y
            }

            val origWidth = max(1f, maxX - minX)
            val origHeight = max(1f, maxY - minY)
            val targetWidth = 160.dp.toPx()
            val targetHeight = targetWidth * (origHeight / origWidth)

            contourPoints.map { pt ->
                val normX = (pt.x - minX) / origWidth - 0.5f
                val normY = (pt.y - minY) / origHeight - 0.5f
                Offset(
                    x = canvasCenterX + normX * targetWidth,
                    y = canvasCenterY + normY * targetHeight
                )
            }
        }

        // Calculate lip bounds and center
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        for (p in projectedPoints) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        val lipWidth = max(20f, maxX - minX)
        val lipCenterX = (minX + maxX) / 2f
        val lipCenterY = (minY + maxY) / 2f

        if (projectedPoints.size == 38) {
            // Detailed ML Kit Lip Contours:
            // 0..10: UPPER_LIP_TOP (11 points)
            // 11..19: UPPER_LIP_BOTTOM (9 points)
            // 20..28: LOWER_LIP_TOP (9 points)
            // 29..37: LOWER_LIP_BOTTOM (9 points)
            val upperLipTop = projectedPoints.subList(0, 11)
            val upperLipBottom = projectedPoints.subList(11, 20)
            val lowerLipTop = projectedPoints.subList(20, 29)
            val lowerLipBottom = projectedPoints.subList(29, 38)

            // Outer Lip Closed Path (upper top left->right, lower bottom right->left)
            val outerLipPath = Path().apply {
                moveTo(upperLipTop[0].x, upperLipTop[0].y)
                for (i in 1 until upperLipTop.size) {
                    lineTo(upperLipTop[i].x, upperLipTop[i].y)
                }
                for (i in lowerLipBottom.indices.reversed()) {
                    lineTo(lowerLipBottom[i].x, lowerLipBottom[i].y)
                }
                close()
            }

            // Inner Lip Closed Path (upper bottom left->right, lower top right->left)
            val innerLipPath = Path().apply {
                moveTo(upperLipBottom[0].x, upperLipBottom[0].y)
                for (i in 1 until upperLipBottom.size) {
                    lineTo(upperLipBottom[i].x, upperLipBottom[i].y)
                }
                for (i in lowerLipTop.indices.reversed()) {
                    lineTo(lowerLipTop[i].x, lowerLipTop[i].y)
                }
                close()
            }

            // 1. Inner oral aperture translucent tint
            drawPath(
                path = innerLipPath,
                color = hudColor.copy(alpha = 0.20f)
            )

            // 2. Outer lip glow pass
            drawPath(
                path = outerLipPath,
                color = hudColor.copy(alpha = 0.30f),
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            )
            // Outer lip sharp stroke
            drawPath(
                path = outerLipPath,
                color = hudColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // 3. Inner lip aperture contour
            drawPath(
                path = innerLipPath,
                color = hudColor.copy(alpha = 0.85f),
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            )

            // 4. Anatomical wireframe struts
            // Upper lip struts (connect upperLipTop to upperLipBottom)
            for (j in 0 until min(upperLipBottom.size, upperLipTop.size - 2)) {
                drawLine(
                    color = hudColor.copy(alpha = 0.35f),
                    start = upperLipTop[j + 1],
                    end = upperLipBottom[j],
                    strokeWidth = 1.dp.toPx()
                )
            }
            // Lower lip struts (connect lowerLipTop to lowerLipBottom)
            for (j in 0 until min(lowerLipTop.size, lowerLipBottom.size)) {
                drawLine(
                    color = hudColor.copy(alpha = 0.35f),
                    start = lowerLipTop[j],
                    end = lowerLipBottom[j],
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 5. Landmark dots
            for (sp in projectedPoints) {
                drawCircle(
                    color = hudColor.copy(alpha = 0.90f),
                    radius = 2.dp.toPx(),
                    center = sp
                )
            }

        } else {
            // General polygon fallback for generic point clouds
            val path = Path().apply {
                moveTo(projectedPoints[0].x, projectedPoints[0].y)
                for (i in 1 until projectedPoints.size) {
                    lineTo(projectedPoints[i].x, projectedPoints[i].y)
                }
                close()
            }
            drawPath(
                path = path,
                color = hudColor.copy(alpha = 0.30f),
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                path = path,
                color = hudColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            for (sp in projectedPoints) {
                drawCircle(
                    color = hudColor.copy(alpha = 0.90f),
                    radius = 2.dp.toPx(),
                    center = sp
                )
            }
        }

        // Caliper horizontal guide lines across top and bottom limits
        drawCaliperGuideLines(lipCenterX, minY, maxY, lipWidth, hudColor)

        return Offset(lipCenterX, lipCenterY)
    } else {
        // Fallback to dynamic synthesized wireframe
        drawSynthesizedLipMesh(
            jawOpen = jawOpen,
            roundness = roundness,
            centerX = canvasCenterX,
            centerY = canvasCenterY,
            hudColor = hudColor
        )
        return null
    }
}

/**
 * Draws dynamic synthesized wireframe mesh based on jawOpen and roundness when landmarks are occluded.
 */
private fun DrawScope.drawSynthesizedLipMesh(
    jawOpen: Float,
    roundness: Float,
    centerX: Float,
    centerY: Float,
    hudColor: Color
) {
    val lipWidth = (130.dp.toPx() * (1.1f - roundness * 0.4f)).coerceIn(80.dp.toPx(), 160.dp.toPx())
    val mouthOpeningY = (jawOpen * 70.dp.toPx()).coerceIn(6.dp.toPx(), 65.dp.toPx())
    val halfW = lipWidth / 2f

    val upperLipTopY = centerY - mouthOpeningY * 0.65f - 8.dp.toPx()
    val upperLipBottomY = centerY - mouthOpeningY * 0.40f
    val lowerLipTopY = centerY + mouthOpeningY * 0.40f
    val lowerLipBottomY = centerY + mouthOpeningY * 0.70f + 8.dp.toPx()

    // Upper lip path
    val upperPath = Path().apply {
        moveTo(centerX - halfW, centerY)
        cubicTo(
            centerX - halfW * 0.5f, upperLipTopY - 4.dp.toPx(),
            centerX - halfW * 0.2f, upperLipTopY,
            centerX, upperLipTopY + 2.dp.toPx() // Cupid's bow dip
        )
        cubicTo(
            centerX + halfW * 0.2f, upperLipTopY,
            centerX + halfW * 0.5f, upperLipTopY - 4.dp.toPx(),
            centerX + halfW, centerY
        )
        cubicTo(
            centerX + halfW * 0.5f, upperLipBottomY,
            centerX - halfW * 0.5f, upperLipBottomY,
            centerX - halfW, centerY
        )
        close()
    }

    // Lower lip path
    val lowerPath = Path().apply {
        moveTo(centerX - halfW, centerY)
        cubicTo(
            centerX - halfW * 0.5f, lowerLipTopY,
            centerX + halfW * 0.5f, lowerLipTopY,
            centerX + halfW, centerY
        )
        cubicTo(
            centerX + halfW * 0.5f, lowerLipBottomY,
            centerX - halfW * 0.5f, lowerLipBottomY,
            centerX - halfW, centerY
        )
        close()
    }

    // Glow pass
    drawPath(
        path = upperPath,
        color = hudColor.copy(alpha = 0.25f),
        style = Stroke(width = 6.dp.toPx())
    )
    drawPath(
        path = lowerPath,
        color = hudColor.copy(alpha = 0.25f),
        style = Stroke(width = 6.dp.toPx())
    )

    // Main wireframe lines
    drawPath(
        path = upperPath,
        color = hudColor,
        style = Stroke(width = 2.dp.toPx())
    )
    drawPath(
        path = lowerPath,
        color = hudColor,
        style = Stroke(width = 2.dp.toPx())
    )

    // Inner oral cavity fill & grid mesh
    drawOval(
        color = Color(0x66000000),
        topLeft = Offset(centerX - halfW * 0.7f, upperLipBottomY),
        size = Size(halfW * 1.4f, max(2f, lowerLipTopY - upperLipBottomY))
    )

    // Articulatory mesh cross-lines
    val meshStep = halfW * 0.35f
    for (i in -2..2) {
        val meshX = centerX + i * meshStep
        drawLine(
            color = hudColor.copy(alpha = 0.40f),
            start = Offset(meshX, upperLipTopY),
            end = Offset(meshX, lowerLipBottomY),
            strokeWidth = 1.dp.toPx()
        )
    }

    // Caliper guide lines
    drawCaliperGuideLines(centerX, upperLipTopY, lowerLipBottomY, lipWidth, hudColor)
}

/**
 * Draws horizontal caliper lines extending from the lip limits to the right caliper gauge.
 */
private fun DrawScope.drawCaliperGuideLines(
    centerX: Float,
    topY: Float,
    bottomY: Float,
    lipWidth: Float,
    hudColor: Color
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
    val guideColor = hudColor.copy(alpha = 0.35f)

    // Top horizontal limit line
    drawLine(
        color = guideColor,
        start = Offset(centerX - lipWidth / 2f - 10.dp.toPx(), topY),
        end = Offset(centerX + lipWidth / 2f + 30.dp.toPx(), topY),
        strokeWidth = 1.dp.toPx(),
        pathEffect = dash
    )

    // Bottom horizontal limit line
    drawLine(
        color = guideColor,
        start = Offset(centerX - lipWidth / 2f - 10.dp.toPx(), bottomY),
        end = Offset(centerX + lipWidth / 2f + 30.dp.toPx(), bottomY),
        strokeWidth = 1.dp.toPx(),
        pathEffect = dash
    )
}

/**
 * Draws vertical Caliper Gauge showing normalized aperture and optimal target brackets.
 */
private fun DrawScope.drawCaliperGauge(
    w: Float,
    h: Float,
    jawOpen: Float,
    hudColor: Color,
    paint: Paint
) {
    val gaugeRight = w - 16.dp.toPx()
    val gaugeTop = h * 0.22f
    val gaugeHeight = h * 0.56f
    val gaugeBottom = gaugeTop + gaugeHeight

    // Background track line
    drawLine(
        color = Color(0x55FFFFFF),
        start = Offset(gaugeRight, gaugeTop),
        end = Offset(gaugeRight, gaugeBottom),
        strokeWidth = 2.dp.toPx()
    )

    // Target zone green bracket (0.18f to 0.28f of gauge)
    val targetTop = gaugeBottom - (gaugeHeight * 0.28f)
    val targetBottom = gaugeBottom - (gaugeHeight * 0.18f)
    drawRect(
        color = Color(0x3300E676),
        topLeft = Offset(gaugeRight - 14.dp.toPx(), targetTop),
        size = Size(14.dp.toPx(), targetBottom - targetTop)
    )
    drawLine(
        color = Color(0xFF00E676),
        start = Offset(gaugeRight - 14.dp.toPx(), targetTop),
        end = Offset(gaugeRight, targetTop),
        strokeWidth = 2.dp.toPx()
    )
    drawLine(
        color = Color(0xFF00E676),
        start = Offset(gaugeRight - 14.dp.toPx(), targetBottom),
        end = Offset(gaugeRight, targetBottom),
        strokeWidth = 2.dp.toPx()
    )

    // Current jaw indicator cursor
    val clampedJaw = jawOpen.coerceIn(0f, 1f)
    val cursorY = gaugeBottom - (gaugeHeight * clampedJaw)

    // Draw pointer triangle
    val pointerPath = Path().apply {
        moveTo(gaugeRight - 2.dp.toPx(), cursorY)
        lineTo(gaugeRight - 10.dp.toPx(), cursorY - 5.dp.toPx())
        lineTo(gaugeRight - 10.dp.toPx(), cursorY + 5.dp.toPx())
        close()
    }
    drawPath(path = pointerPath, color = hudColor)

    // Text metrics
    val pctText = "${(clampedJaw * 100).toInt()}%"
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 28f
    drawContext.canvas.nativeCanvas.drawText(
        pctText,
        gaugeRight - 42.dp.toPx(),
        cursorY + 9f,
        paint
    )
}

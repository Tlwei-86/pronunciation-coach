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
 * 3. Targeting reticle with corner brackets and crosshairs.
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
        val centerX = w / 2f
        val centerY = h / 2f

        // 1. Draw Target Reticle
        drawTargetReticle(centerX, centerY, animatedColor)

        // 2. Draw Lip Mesh (Real ML Kit contour points or synthesized articulatory mesh)
        drawMouthMesh(
            centerX = centerX,
            centerY = centerY,
            metrics = metrics,
            hudColor = animatedColor
        )

        // 3. Draw Vertical Caliper Gauge & Percentage
        drawVerticalCaliperGauge(
            w = w,
            h = h,
            centerY = centerY,
            metrics = metrics,
            hudColor = animatedColor,
            textPaint = nativeTextPaint
        )
    }
}

/**
 * Draws HUD reticle with corner brackets and centering crosshairs.
 */
private fun DrawScope.drawTargetReticle(
    centerX: Float,
    centerY: Float,
    hudColor: Color
) {
    val boxWidth = 180.dp.toPx()
    val boxHeight = 110.dp.toPx()
    val left = centerX - boxWidth / 2f
    val right = centerX + boxWidth / 2f
    val top = centerY - boxHeight / 2f
    val bottom = centerY + boxHeight / 2f
    val bracketLen = 22.dp.toPx()
    val strokeWidth = 2.dp.toPx()

    val bracketColor = hudColor.copy(alpha = 0.85f)

    // Corner brackets
    // Top-Left
    drawLine(bracketColor, Offset(left, top), Offset(left + bracketLen, top), strokeWidth)
    drawLine(bracketColor, Offset(left, top), Offset(left, top + bracketLen), strokeWidth)

    // Top-Right
    drawLine(bracketColor, Offset(right, top), Offset(right - bracketLen, top), strokeWidth)
    drawLine(bracketColor, Offset(right, top), Offset(right, top + bracketLen), strokeWidth)

    // Bottom-Left
    drawLine(bracketColor, Offset(left, bottom), Offset(left + bracketLen, bottom), strokeWidth)
    drawLine(bracketColor, Offset(left, bottom), Offset(left, bottom - bracketLen), strokeWidth)

    // Bottom-Right
    drawLine(bracketColor, Offset(right, bottom), Offset(right - bracketLen, bottom), strokeWidth)
    drawLine(bracketColor, Offset(right, bottom), Offset(right, bottom - bracketLen), strokeWidth)

    // Center Crosshairs
    val crossHairSize = 10.dp.toPx()
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
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

    // Dashed center ellipse (Ideal /ʌ/ target guide zone)
    drawOval(
        color = Color(0xFF00E676).copy(alpha = 0.25f),
        topLeft = Offset(centerX - 42.dp.toPx(), centerY - 14.dp.toPx()),
        size = Size(84.dp.toPx(), 28.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = dashEffect)
    )
}

/**
 * Draws dynamic lip tracking contour mesh and caliper lines.
 */
private fun DrawScope.drawMouthMesh(
    centerX: Float,
    centerY: Float,
    metrics: LiveFaceMouthMetrics,
    hudColor: Color
) {
    val jawOpen = metrics.jawOpen.coerceIn(0.08f, 0.95f)
    val roundness = metrics.lipRoundness.coerceIn(0.05f, 0.60f)
    val points = metrics.lipContourPoints

    if (points.isNotEmpty() && metrics.isFaceDetected) {
        // ML Kit contour coordinates are present: normalize and scale them to HUD center
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        for (pt in points) {
            if (pt.x < minX) minX = pt.x
            if (pt.x > maxX) maxX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.y > maxY) maxY = pt.y
        }

        val rangeX = max(1f, maxX - minX)
        val rangeY = max(1f, maxY - minY)

        val targetWidth = 140.dp.toPx()
        val targetHeight = (targetWidth * (rangeY / rangeX)).coerceIn(24.dp.toPx(), 90.dp.toPx())

        val scaledPoints = points.map { pt ->
            val normX = (pt.x - minX) / rangeX - 0.5f
            val normY = (pt.y - minY) / rangeY - 0.5f
            Offset(centerX + normX * targetWidth, centerY + normY * targetHeight)
        }

        // Draw outer glow path
        val path = Path().apply {
            if (scaledPoints.isNotEmpty()) {
                moveTo(scaledPoints[0].x, scaledPoints[0].y)
                for (i in 1 until scaledPoints.size) {
                    lineTo(scaledPoints[i].x, scaledPoints[i].y)
                }
            }
        }

        // Glow stroke
        drawPath(
            path = path,
            color = hudColor.copy(alpha = 0.30f),
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
        )
        // Sharp contour stroke
        drawPath(
            path = path,
            color = hudColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw landmark dots
        for (sp in scaledPoints) {
            drawCircle(
                color = hudColor.copy(alpha = 0.90f),
                radius = 2.5.dp.toPx(),
                center = sp
            )
        }

        // Caliper horizontal guide lines across top and bottom limits
        val topLimitY = centerY - targetHeight / 2f
        val bottomLimitY = centerY + targetHeight / 2f
        drawCaliperGuideLines(centerX, topLimitY, bottomLimitY, targetWidth, hudColor)

    } else {
        // Dynamic synthesized wireframe mesh based on jawOpen and roundness
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
            // Inner upper edge
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
        drawPath(upperPath, hudColor.copy(alpha = 0.25f), Stroke(width = 6.dp.toPx()))
        drawPath(lowerPath, hudColor.copy(alpha = 0.25f), Stroke(width = 6.dp.toPx()))

        // Main wireframe lines
        drawPath(upperPath, hudColor, Stroke(width = 2.dp.toPx()))
        drawPath(lowerPath, hudColor, Stroke(width = 2.dp.toPx()))

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
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
    val rightExtent = size.width - 64.dp.toPx()

    // Top caliper line
    drawLine(
        color = hudColor.copy(alpha = 0.65f),
        start = Offset(centerX + lipWidth * 0.45f, topY),
        end = Offset(rightExtent, topY),
        strokeWidth = 1.2.dp.toPx(),
        pathEffect = dashEffect
    )

    // Bottom caliper line
    drawLine(
        color = hudColor.copy(alpha = 0.65f),
        start = Offset(centerX + lipWidth * 0.45f, bottomY),
        end = Offset(rightExtent, bottomY),
        strokeWidth = 1.2.dp.toPx(),
        pathEffect = dashEffect
    )

    // Caliper height vertical span bar
    val caliperBarX = rightExtent + 6.dp.toPx()
    drawLine(
        color = hudColor,
        start = Offset(caliperBarX, topY),
        end = Offset(caliperBarX, bottomY),
        strokeWidth = 2.dp.toPx()
    )
    // Little end ticks
    drawLine(hudColor, Offset(caliperBarX - 4.dp.toPx(), topY), Offset(caliperBarX + 4.dp.toPx(), topY), 2.dp.toPx())
    drawLine(hudColor, Offset(caliperBarX - 4.dp.toPx(), bottomY), Offset(caliperBarX + 4.dp.toPx(), bottomY), 2.dp.toPx())
}

/**
 * Draws vertical caliper gauge on the right edge showing normalized jaw percentage.
 */
private fun DrawScope.drawVerticalCaliperGauge(
    w: Float,
    h: Float,
    centerY: Float,
    metrics: LiveFaceMouthMetrics,
    hudColor: Color,
    textPaint: Paint
) {
    val gaugeWidth = 10.dp.toPx()
    val gaugeHeight = 130.dp.toPx()
    val gaugeX = w - 34.dp.toPx()
    val gaugeTop = centerY - gaugeHeight / 2f
    val gaugeBottom = centerY + gaugeHeight / 2f

    // Background track
    drawRoundRect(
        color = Color(0x77000000),
        topLeft = Offset(gaugeX, gaugeTop),
        size = Size(gaugeWidth, gaugeHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
    )

    // Ideal /ʌ/ zone marker (0.18f .. 0.28f normalized)
    val idealTop = gaugeBottom - gaugeHeight * 0.28f
    val idealHeight = gaugeHeight * (0.28f - 0.18f)
    drawRect(
        color = Color(0x5500E676),
        topLeft = Offset(gaugeX, idealTop),
        size = Size(gaugeWidth, idealHeight)
    )

    // Active fill level (inverted: 0 at bottom, 1.0 at top)
    val fillPercent = metrics.jawOpen.coerceIn(0f, 1f)
    val fillHeight = gaugeHeight * fillPercent
    val fillTop = gaugeBottom - fillHeight

    drawRoundRect(
        color = hudColor,
        topLeft = Offset(gaugeX, fillTop),
        size = Size(gaugeWidth, fillHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
    )

    // Needle indicator tick
    drawLine(
        color = Color.White,
        start = Offset(gaugeX - 4.dp.toPx(), fillTop),
        end = Offset(gaugeX + gaugeWidth + 4.dp.toPx(), fillTop),
        strokeWidth = 2.5.dp.toPx()
    )

    // Gauge border
    drawRoundRect(
        color = Color(0x66FFFFFF),
        topLeft = Offset(gaugeX, gaugeTop),
        size = Size(gaugeWidth, gaugeHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
        style = Stroke(width = 1.dp.toPx())
    )

    // Draw percentage text using native canvas
    drawContext.canvas.nativeCanvas.apply {
        textPaint.color = android.graphics.Color.argb(
            (hudColor.alpha * 255).toInt(),
            (hudColor.red * 255).toInt(),
            (hudColor.green * 255).toInt(),
            (hudColor.blue * 255).toInt()
        )
        textPaint.textSize = 28f
        val pctText = "${(metrics.jawOpen * 100).toInt()}%"
        drawText(pctText, gaugeX - textPaint.measureText(pctText) - 8f, fillTop + 10f, textPaint)

        // Gauge Title
        textPaint.color = android.graphics.Color.argb(180, 200, 200, 200)
        textPaint.textSize = 20f
        drawText("JAW", gaugeX - 6f, gaugeTop - 8f, textPaint)
    }
}

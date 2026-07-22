/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.videonotes

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import kotlin.math.cos
import kotlin.math.sin

fun DrawScope.drawVideoNoteRingProgress(
    progress: Float,
    ringPaddingPx: Float,
    ringStrokePx: Float,
    backgroundColor: Color,
    progressColor: Color,
    showScrubberDot: Boolean = true,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val stroke = Stroke(width = ringStrokePx, cap = StrokeCap.Round)
    val inset = ringPaddingPx + ringStrokePx / 2f
    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
    drawArc(
        color = backgroundColor,
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = arcSize,
        style = stroke,
    )
    if (clampedProgress > 0f) {
        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = 360f * clampedProgress,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
    }
    if (showScrubberDot && clampedProgress > 0f) {
        val angleRadians = Math.toRadians((-90.0 + 360.0 * clampedProgress))
        val radius = arcSize.width / 2f
        val centerX = size.width / 2f + radius * cos(angleRadians).toFloat()
        val centerY = size.height / 2f + radius * sin(angleRadians).toFloat()
        drawCircle(
            color = progressColor,
            radius = ringStrokePx * 0.85f,
            center = Offset(centerX, centerY),
        )
    }
}

@Composable
internal fun VideoNoteRingProgressCanvas(
    progress: Float,
    ringPaddingPx: Float,
    ringStrokePx: Float,
    backgroundColor: Color,
    progressColor: Color,
    showScrubberDot: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.semantics { hideFromAccessibility() },
    ) {
        drawVideoNoteRingProgress(
            progress = progress,
            ringPaddingPx = ringPaddingPx,
            ringStrokePx = ringStrokePx,
            backgroundColor = backgroundColor,
            progressColor = progressColor,
            showScrubberDot = showScrubberDot,
        )
    }
}

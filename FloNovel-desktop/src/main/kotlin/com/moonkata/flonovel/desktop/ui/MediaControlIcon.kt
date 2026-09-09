package com.moonkata.flonovel.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shape of the media control symbol inside the blue circle.
 */
enum class MediaControlShape {
    PLAY,
    STOP,
}

/**
 * Renders a circular media control button icon:
 * - Blue circle background
 * - White triangle (Play) or white square (Stop) precisely centered
 */
@Composable
fun MediaControlIcon(
    shape: MediaControlShape,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    circleColor: Color = Color(0xFF3B82F6),
    iconColor: Color = Color.White,
) {
    Canvas(modifier = modifier.size(size)) {
        val radius = size.toPx() / 2f
        val center = Offset(radius, radius)

        // 1. Draw blue circle background
        drawCircle(
            color = circleColor,
            radius = radius,
            center = center,
        )

        val innerScale = radius * 0.95f

        when (shape) {
            MediaControlShape.PLAY -> {
                // Equilateral-style triangle pointing right, optically balanced
                val triangleHalfHeight = innerScale * 0.52f
                val triangleWidth = innerScale * 0.90f
                // Shift slightly right (+0.08) because geometric center != visual center of triangle
                val left = center.x - (triangleWidth * 0.42f)
                val right = left + triangleWidth
                val top = center.y - triangleHalfHeight
                val bottom = center.y + triangleHalfHeight

                val path = Path().apply {
                    moveTo(left, top)
                    lineTo(right, center.y)
                    lineTo(left, bottom)
                    close()
                }
                drawPath(path = path, color = iconColor)
            }
            MediaControlShape.STOP -> {
                // Modern square with slightly rounded corners centered in circle
                val squareSize = innerScale * 0.88f
                val halfSquare = squareSize / 2f
                val topLeft = Offset(center.x - halfSquare, center.y - halfSquare)
                val corner = radius * 0.12f

                drawRoundRect(
                    color = iconColor,
                    topLeft = topLeft,
                    size = Size(squareSize, squareSize),
                    cornerRadius = CornerRadius(corner, corner),
                )
            }
        }
    }
}

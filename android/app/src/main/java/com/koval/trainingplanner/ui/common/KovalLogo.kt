package com.koval.trainingplanner.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

@Composable
fun KovalLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val scale = s / 240f
        val orange = Color(0xFFFF9D00)

        // Background rounded rect
        val bgPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 10f * scale, top = 10f * scale,
                    right = 230f * scale, bottom = 230f * scale,
                    cornerRadius = CornerRadius(64f * scale)
                )
            )
        }
        drawPath(bgPath, Color.Black)
        drawPath(bgPath, orange, style = Stroke(width = 12f * scale))

        // K logo: use saveLayer + DstOut to replicate the SVG mask
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, Size(s, s)), Paint())

            // SVG: transform="rotate(-10, 120, 125) translate(10, 12)"
            canvas.save()
            canvas.translate(120f * scale, 125f * scale)
            canvas.rotate(-10f)
            canvas.translate(-110f * scale, -113f * scale)

            val fillPaint = Paint().apply { color = orange }
            val cutPaint = Paint().apply {
                color = Color.Black
                blendMode = BlendMode.DstOut
            }

            // Triangle
            canvas.drawPath(
                Path().apply {
                    moveTo(100f * scale, 18f * scale)
                    lineTo(182f * scale, 172f * scale)
                    lineTo(18f * scale, 172f * scale)
                    close()
                },
                fillPaint
            )

            // Vertical strip cut
            canvas.drawPath(
                Path().apply {
                    addRect(Rect(69f * scale, 0f, 81f * scale, 190f * scale))
                },
                cutPaint
            )

            // Upper diagonal cut
            canvas.drawPath(
                Path().apply {
                    moveTo(81f * scale, 110f * scale)
                    lineTo(81f * scale, 86f * scale)
                    lineTo(180f * scale, 0f)
                    lineTo(152f * scale, 0f)
                    close()
                },
                cutPaint
            )

            // Lower diagonal cut
            canvas.drawPath(
                Path().apply {
                    moveTo(81f * scale, 96f * scale)
                    lineTo(81f * scale, 120f * scale)
                    lineTo(190f * scale, 190f * scale)
                    lineTo(162f * scale, 190f * scale)
                    close()
                },
                cutPaint
            )

            canvas.restore()
            canvas.restore()
        }
    }
}

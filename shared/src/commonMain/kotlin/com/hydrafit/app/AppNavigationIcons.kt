package com.hydrafit.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.hydrafit.app.core.navigation.NavTab
import kotlin.math.cos
import kotlin.math.sin

/** Original geometric glyphs; the navigation item's visible label supplies its accessible name. */
@Composable
internal fun AppNavigationIcon(tab: NavTab) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round)
            when (tab) {
                NavTab.TRAIN -> {
                    drawLine(color, Offset(7f, 12f), Offset(17f, 12f), 1.8f)
                    for (x in listOf(4f, 7f, 17f, 20f)) {
                        val half = if (x == 4f || x == 20f) 3f else 6f
                        drawLine(
                            color,
                            Offset(x, 12f - half),
                            Offset(x, 12f + half),
                            1.8f,
                            StrokeCap.Round
                        )
                    }
                }
                NavTab.PROGRESS -> {
                    val trend = Path().apply {
                        moveTo(4f, 17f)
                        lineTo(9f, 12f)
                        lineTo(13f, 14f)
                        lineTo(20f, 6f)
                    }
                    drawPath(trend, color, style = stroke)
                    drawLine(color, Offset(4f, 4f), Offset(4f, 20f), 1.8f)
                    drawLine(color, Offset(4f, 20f), Offset(21f, 20f), 1.8f)
                }
                NavTab.LIBRARY -> {
                    drawRect(color, Offset(4f, 4f), Size(16f, 16f), style = stroke)
                    drawLine(color, Offset(9f, 4f), Offset(9f, 20f), 1.8f)
                    drawLine(color, Offset(13f, 8f), Offset(17f, 8f), 1.8f)
                    drawLine(color, Offset(13f, 12f), Offset(17f, 12f), 1.8f)
                }
                NavTab.SETTINGS -> {
                    drawCircle(color, 6f, Offset(12f, 12f), style = stroke)
                    drawCircle(color, 2f, Offset(12f, 12f), style = stroke)
                    repeat(8) { index ->
                        val angle = index * kotlin.math.PI / 4.0
                        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                        drawLine(
                            color,
                            Offset(12f, 12f) + direction * 6f,
                            Offset(12f, 12f) + direction * 9f,
                            1.8f,
                            StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

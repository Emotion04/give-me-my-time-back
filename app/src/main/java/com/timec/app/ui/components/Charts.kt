package com.timec.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

private val palette = listOf(
    Color(0xFF4D6A7A),
    Color(0xFF7FD1AE),
    Color(0xFFE6B76A),
    Color(0xFFDB6868),
    Color(0xFF8FA8C8),
    Color(0xFFA98FC8),
    Color(0xFFC89F9F)
)

@Composable
fun PieChart(data: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    val total = data.sumOf { it.second }.coerceAtLeast(1L)
    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(1f)) {
        var startAngle = -90f
        data.forEachIndexed { index, item ->
            val sweep = item.second.toFloat() / total.toFloat() * 360f
            drawArc(
                color = palette[index % palette.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = Offset.Zero,
                size = Size(size.width, size.height)
            )
            startAngle += sweep
        }
        if (data.isEmpty()) {
            drawArc(
                color = Color(0xFFE1E8EF),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = true,
                topLeft = Offset.Zero,
                size = Size(size.width, size.height)
            )
        }
    }
}

@Composable
fun BarChart(data: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    val max = data.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(2.2f)) {
        if (data.isEmpty()) return@Canvas
        val gap = size.width * 0.04f
        val slot = (size.width - gap * (data.size - 1)) / data.size
        val barWidth = slot * 0.72f
        data.forEachIndexed { index, item ->
            val height = (item.second.toFloat() / max.toFloat()) * size.height
            val left = index * (slot + gap) + (slot - barWidth) / 2f
            val top = size.height - height
            drawRoundRect(
                color = palette[index % palette.size],
                topLeft = Offset(left, top),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(8f, 8f)
            )
        }
    }
}

package com.timec.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

private val palette = listOf(
    Color(0xFF4D6A7A),
    Color(0xFF7FD1AE),
    Color(0xFFE6B76A),
    Color(0xFFDB6868),
    Color(0xFF8FA8C8),
    Color(0xFFA98FC8),
    Color(0xFFC89F9F)
)

fun chartColor(index: Int): Color = palette[index % palette.size]

@Composable
fun TimelineBar(buckets: List<Long>, color: Color, modifier: Modifier = Modifier) {
    val max = (buckets.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(modifier = modifier.fillMaxWidth().height(22.dp)) {
        val slotW = size.width / buckets.size.coerceAtLeast(1)
        buckets.forEachIndexed { i, millis ->
            if (millis > 0L) {
                val w = (millis.toFloat() / max.toFloat()) * slotW
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * slotW + (slotW - w) / 2f, size.height * 0.28f),
                    size = Size(w.coerceAtLeast(1.5f), size.height * 0.44f),
                    cornerRadius = CornerRadius(2.5f, 2.5f)
                )
            }
        }
    }
}

@Composable
fun PieChart(data: List<Pair<String, Long>>, modifier: Modifier = Modifier, onClick: ((Int) -> Unit)? = null) {
    val total = data.sumOf { it.second }.coerceAtLeast(1L)
    val drawModifier = if (onClick != null) {
        Modifier.fillMaxWidth().aspectRatio(1f).pointerInput(data, onClick) {
            detectTapGestures { offset ->
                val cx = size.width / 2f
                val cy = size.height / 2f
                val dx = offset.x - cx
                val dy = offset.y - cy
                val dist = Math.sqrt((dx * dx + dy * dy).toDouble())
                if (dist > size.width / 2f) return@detectTapGestures
                var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (angle < 0f) angle += 360f
                var acc = 0f
                var idx = -1
                for (i in data.indices) {
                    val sweep = data[i].second.toFloat() / total.toFloat() * 360f
                    val start = (270f + acc) % 360f
                    val end = (270f + acc + sweep) % 360f
                    val inSlice = if (start <= end) angle >= start && angle < end else angle >= start || angle < end
                    if (inSlice) { idx = i; break }
                    acc += sweep
                }
                if (idx >= 0) onClick(idx)
            }
        }
    } else {
        Modifier.fillMaxWidth().aspectRatio(1f)
    }
    Canvas(modifier = modifier.then(drawModifier)) {
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
fun BarChart(data: List<Pair<String, Long>>, modifier: Modifier = Modifier, onClick: ((Int) -> Unit)? = null) {
    val max = data.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    val drawModifier = if (onClick != null) {
        Modifier.fillMaxWidth().aspectRatio(2.2f).pointerInput(data, onClick) {
            detectTapGestures { offset ->
                if (data.isEmpty()) return@detectTapGestures
                val gap = size.width * 0.04f
                val slot = (size.width - gap * (data.size - 1)) / data.size
                val idx = (offset.x / (slot + gap)).toInt().coerceIn(0, data.size - 1)
                onClick(idx)
            }
        }
    } else {
        Modifier.fillMaxWidth().aspectRatio(2.2f)
    }
    Canvas(modifier = modifier.then(drawModifier)) {
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

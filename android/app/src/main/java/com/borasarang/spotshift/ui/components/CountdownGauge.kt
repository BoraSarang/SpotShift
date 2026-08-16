package com.borasarang.spotshift.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.borasarang.spotshift.ui.theme.AccentSky
import com.borasarang.spotshift.ui.theme.SurfaceMuted
import java.util.Locale

/**
 * 다음 IP 변경까지 남은 시간을 표시하는 원형 카운트다운 게이지.
 * v0.3 — "다음 변경까지 · HH:MM 예정" 형식으로 예상 시각을 병기한다.
 * 남은 시간 텍스트를 병기해 접근성을 확보한다.
 */
@Composable
fun CountdownGauge(
    remainingSeconds: Long,
    totalSeconds: Long,
    modifier: Modifier = Modifier,
    gaugeColor: Color = AccentSky,
    nextChangeAtLabel: String? = null
) {
    val progress by animateFloatAsState(
        targetValue = if (totalSeconds > 0) {
            (1f - remainingSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
        } else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "countdown_progress"
    )
    val remainingText = formatRemaining(remainingSeconds)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val strokeWidth = 14.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = SurfaceMuted,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = gaugeColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = remainingText,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            // v0.3 — 예상 시각 병기: "다음 변경까지 · 14:02 예정"
            Text(
                text = if (nextChangeAtLabel != null) {
                    "다음 변경까지 · $nextChangeAtLabel"
                } else {
                    "다음 변경까지"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatRemaining(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val s = safe % 60
    return if (h > 0) {
        String.format(Locale.KOREA, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.KOREA, "%02d:%02d", m, s)
    }
}

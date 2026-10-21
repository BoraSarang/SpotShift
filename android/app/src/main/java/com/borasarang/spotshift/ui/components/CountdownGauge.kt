package com.borasarang.spotshift.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.borasarang.spotshift.ui.theme.full
import com.borasarang.spotshift.ui.util.rememberAnimationsEnabled
import java.util.Locale

/**
 * T-48 — 다음 IP 변경까지 남은 시간 오브(192dp) + 예상 시각 pill.
 *
 * 목업 `?screen=home` 기준:
 * - 트랙/진행 링 + 안쪽 디스크(surface-container-low)
 * - 중앙: 남은 시간(display, tabular) / "다음 변경까지" / "HH:mm 예정" pill
 * - 남은 시간은 오브 안쪽 폭에 맞춰 폰트 크기를 상한 처리한다 (글자 1.3x에서도 잘림 0)
 * - 접근성: 오브 전체에 "다음 변경까지 1시간 47분, 14:02 예정" 설명 제공
 */
@Composable
fun CountdownGauge(
    remainingSeconds: Long,
    totalSeconds: Long,
    modifier: Modifier = Modifier,
    gaugeColor: Color? = null,
    nextChangeAtLabel: String? = null
) {
    val progress by animateFloatAsState(
        targetValue = if (totalSeconds > 0) {
            (1f - remainingSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
        } else {
            0f
        },
        // T-51 — 시스템 "애니메이션 제거"면 즉시 값만 갱신
        animationSpec = if (rememberAnimationsEnabled()) {
            tween(durationMillis = 1000, easing = LinearEasing)
        } else {
            tween(durationMillis = 0)
        },
        label = "countdown_progress"
    )
    val remainingText = formatRemaining(remainingSeconds)
    val progressColor = gaugeColor ?: MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val innerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val description = buildString {
        append("다음 변경까지 ")
        append(describeRemaining(remainingSeconds))
        if (nextChangeAtLabel != null) append(", $nextChangeAtLabel")
    }
    // 오브 안쪽 폭(192dp - 여백 32dp)에 "23:59:59"(약 3.9em)가 들어가는 크기.
    // 사용자 글자 크기(fontScale)가 커지면 그만큼 상한을 낮춰 잘림을 막는다.
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(0.1f)
    val fittedSp = (InnerWidthDp / 3.9f / fontScale).coerceIn(18f, 36f)

    Box(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(OrbSize)) {
            val strokeWidth = 12.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            drawCircle(
                color = trackColor,
                radius = size.minDimension / 2 - inset,
                style = Stroke(width = strokeWidth)
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Surface(
            modifier = Modifier.size(InnerDiscSize),
            shape = CircleShape,
            color = innerColor
        ) {}
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = remainingText,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = fittedSp.sp,
                    lineHeight = (fittedSp * 1.15f).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "다음 변경까지",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (nextChangeAtLabel != null) {
                Surface(
                    modifier = Modifier.padding(top = 8.dp),
                    shape = MaterialTheme.shapes.full,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = nextChangeAtLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private val OrbSize = 192.dp
private val InnerDiscSize = 160.dp
private val InnerWidthDp = 160f

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

/** 접근성용 읽기 문장 ("1시간 47분"). */
private fun describeRemaining(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val s = safe % 60
    return buildString {
        if (h > 0) append("${h}시간 ")
        append("${m}분")
        if (h == 0L) append(" ${s}초")
    }
}

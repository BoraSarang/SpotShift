package com.borasarang.spotshift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.borasarang.spotshift.ui.theme.LocalSpotColors
import com.borasarang.spotshift.ui.theme.full

/**
 * T-48 — 홈 상태 카드(히어로).
 *
 * 목업 `?screen=home` 기준 구성:
 * - 1행: 핫스팟 상태 배지 + 자동 변경 ON/OFF 배지
 * - 2행: 공인 IP(대형, 탭하면 복사) + 보조 정보(통신사·RAT·RSRP)
 * - 구분선
 * - 3지표: 최근 측정 / 속도 기준 / 온도
 *
 * 이전 버전의 `speedThresholdText`·`lastSpeedText`·`signalText` 문단 나열을 지표 그리드로 대체했다.
 */
@Composable
fun StatusCard(
    hotspotEnabled: Boolean,
    publicIp: String?,
    autoEnabled: Boolean,
    signalSummary: String?,
    lastMeasuredText: String?,
    thresholdText: String,
    temperatureText: String?,
    modifier: Modifier = Modifier,
    onCopyIp: (String) -> Unit = {}
) {
    val statusColor = if (hotspotEnabled) {
        LocalSpotColors.current.success
    } else {
        MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (hotspotEnabled) "핫스팟 켜짐" else "핫스팟 꺼짐",
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
                Spacer(Modifier.weight(1f))
                AutoBadge(enabled = autoEnabled)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = publicIp ?: "IP 확인 중",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (publicIp != null) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { onCopyIp(publicIp) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "공인 IP 복사",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Text(
                text = listOfNotNull("공인 IP", signalSummary).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(modifier = Modifier.fillMaxWidth()) {
                MiniStat(
                    label = "최근 측정",
                    value = lastMeasuredText ?: "—",
                    modifier = Modifier.weight(1f)
                )
                MiniStat(
                    label = "속도 기준",
                    value = thresholdText,
                    modifier = Modifier.weight(1f)
                )
                MiniStat(
                    label = "온도",
                    value = temperatureText ?: "—",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AutoBadge(enabled: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.full,
        color = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    ) {
        Text(
            text = if (enabled) "자동 변경 ON" else "자동 변경 OFF",
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

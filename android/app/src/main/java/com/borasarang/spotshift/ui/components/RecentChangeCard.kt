package com.borasarang.spotshift.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.borasarang.spotshift.data.RotationRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v0.4 — 최근 변경 요약 카드 (홈).
 * 마지막 기록의 x→y, 측정 속도/사유, 시각을 표시한다. 기록 없으면 호출처에서 숨김.
 */
@Composable
fun RecentChangeCard(
    record: RotationRecord,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.KOREA) }
    val statusColor = when {
        record.changed -> MaterialTheme.colorScheme.secondary
        record.errorCode != null -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val methodLabel = when (record.method) {
        RotationRecord.METHOD_DATA_RECONNECT -> "데이터 재연결"
        RotationRecord.METHOD_AIRPLANE -> "에어플레인"
        RotationRecord.METHOD_HOTSPOT_RESTART -> "핫스팟 재시작"
        RotationRecord.METHOD_SPEED_CHECK -> "속도 측정"
        else -> "변경 없음"
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "최근 변경 · ${timeFormat.format(Date(record.timestamp))}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // v0.4 — 속도 측정 단독 기록은 IP 행 없이 사유만 표시
            if (record.oldIp != null || record.newIp != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = record.oldIp ?: "-",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleMedium,
                        color = statusColor
                    )
                    Text(
                        text = record.newIp ?: "-",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Text(
                text = listOfNotNull(methodLabel, record.note, record.errorCode)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

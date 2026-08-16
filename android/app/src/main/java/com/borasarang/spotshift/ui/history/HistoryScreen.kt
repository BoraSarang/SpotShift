package com.borasarang.spotshift.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.data.RotationRecord
import com.borasarang.spotshift.ui.home.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(contentPadding: PaddingValues) {
    DebugLogger.feature("HistoryScreen", "표시됨")
    val viewModel: HomeViewModel = viewModel()
    val records by viewModel.records.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        // v0.2 — 요구사항 3: 기록 초기화
        if (records.isNotEmpty()) {
            TextButton(
                onClick = {
                    DebugLogger.feature("HistoryScreen", "기록 초기화 실행됨")
                    viewModel.clearRecords()
                },
                modifier = Modifier.align(Alignment.End).padding(end = 16.dp, top = 8.dp)
            ) {
                Text("기록 초기화", color = MaterialTheme.colorScheme.error)
            }
        }

        if (records.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "아직 변경 기록이 없습니다",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(records, key = { it.id }) { record ->
                RecordItem(record)
            }
        }
    }
}

@Composable
private fun RecordItem(record: RotationRecord) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.KOREA) }
    val statusColor = when {
        record.changed -> MaterialTheme.colorScheme.secondary
        record.errorCode != null -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.error
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateFormat.format(Date(record.timestamp)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = record.oldIp ?: "-",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Outlined.ArrowForward,
                        contentDescription = "→",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    Text(
                        text = record.newIp ?: "-",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = buildSummary(record),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
            }
        }
    }
}

private fun buildSummary(record: RotationRecord): String {
    val result = if (record.changed) "성공" else "실패"
    val method = when (record.method) {
        RotationRecord.METHOD_DATA_RECONNECT -> "데이터 재연결"
        RotationRecord.METHOD_AIRPLANE -> "에어플레인"
        RotationRecord.METHOD_HOTSPOT_RESTART -> "핫스팟 재시작"
        else -> "변경 없음"
    }
    val retry = if (record.retryCount > 0) " · 재시도 ${record.retryCount}회" else ""
    val error = record.errorCode?.let { " · $it" } ?: ""
    return "$result · $method${retry} · ${record.durationMs / 1000}s$error"
}

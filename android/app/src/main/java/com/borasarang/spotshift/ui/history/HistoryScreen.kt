package com.borasarang.spotshift.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.data.RotationRecord
import com.borasarang.spotshift.ui.home.HomeViewModel
import com.borasarang.spotshift.ui.theme.LocalSpotColors
import com.borasarang.spotshift.ui.theme.full
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** T-49 — 기록 필터 (전체/성공/실패/속도 측정). */
private enum class RecordFilter(val label: String) {
    ALL("전체"),
    SUCCESS("성공"),
    FAILED("실패"),
    SPEED("속도 측정");

    fun matches(record: RotationRecord): Boolean = when (this) {
        ALL -> true
        SUCCESS -> record.changed
        FAILED -> !record.changed && record.errorCode != null
        SPEED -> record.method == RotationRecord.METHOD_SPEED_CHECK
    }
}

/**
 * T-49 — 기록 화면. 목업 `?screen=history` 기준:
 * 필터 세그먼트 → 날짜 그룹 카드 → 행(리드 아이콘 + 제목/보조행 + 결과 배지).
 * 성공/실패는 색 + 아이콘 + 배지 텍스트로 이중 부호화한다.
 */
@Composable
fun HistoryScreen(contentPadding: PaddingValues) {
    // T-43 — 진입 로그는 1회만 (본문이면 재구성마다 찍혀 로그 버퍼를 잠식)
    LaunchedEffect(Unit) {
        DebugLogger.feature("HistoryScreen", "표시됨")
    }
    val viewModel: HomeViewModel = viewModel()
    val records by viewModel.records.collectAsState()
    var filter by remember { mutableStateOf(RecordFilter.ALL) }
    // v0.5 — 기록 초기화 확인 다이얼로그 (속도 탭과 동일 규칙)
    var showClearConfirm by remember { mutableStateOf(false) }

    val filtered = remember(records, filter) { records.filter(filter::matches) }
    val grouped = remember(filtered) {
        filtered.groupBy { record ->
            Instant.ofEpochMilli(record.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("기록", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "총 ${records.size}건",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (records.isNotEmpty()) {
                TextButton(onClick = { showClearConfirm = true }) {
                    Text("초기화", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val filters = RecordFilter.entries
            filters.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = filter == item,
                    onClick = { filter = item },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = filters.size)
                ) {
                    Text(
                        text = "${item.label} ${records.count(item::matches)}",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (records.isEmpty()) "아직 변경 기록이 없습니다" else "해당하는 기록이 없습니다",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grouped.forEach { (date, dayRecords) ->
                    item(key = "label-$date") {
                        Text(
                            text = dateLabel(date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp)
                        )
                    }
                    item(key = "card-$date") {
                        RecordGroupCard(dayRecords)
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("기록 초기화") },
            text = { Text("변경 기록을 모두 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    DebugLogger.feature("HistoryScreen", "기록 초기화 실행됨")
                    viewModel.clearRecords()
                    showClearConfirm = false
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun RecordGroupCard(records: List<RotationRecord>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            records.forEachIndexed { index, record ->
                RecordRow(record)
                if (index < records.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordRow(record: RotationRecord) {
    val tone = recordTone(record)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Lead(
            icon = tone.icon,
            container = tone.container,
            content = tone.content
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recordTitle(record),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = if (record.oldIp != null || record.newIp != null) {
                    FontFamily.Monospace
                } else {
                    null
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = recordSubtitle(record),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        ToneBadge(label = tone.label, container = tone.container, content = tone.content)
    }
}

@Composable
private fun Lead(icon: ImageVector, container: Color, content: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ToneBadge(label: String, container: Color, content: Color) {
    Surface(shape = MaterialTheme.shapes.full, color = container) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private data class RecordTone(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color
)

@Composable
private fun recordTone(record: RotationRecord): RecordTone {
    val spot = LocalSpotColors.current
    return when {
        record.method == RotationRecord.METHOD_SPEED_CHECK -> RecordTone(
            label = "측정",
            icon = Icons.Outlined.Speed,
            container = MaterialTheme.colorScheme.surfaceContainerHighest,
            content = MaterialTheme.colorScheme.onSurfaceVariant
        )
        record.changed -> RecordTone(
            label = "성공",
            icon = Icons.Outlined.CheckCircle,
            container = spot.successContainer,
            content = spot.onSuccessContainer
        )
        record.errorCode != null -> RecordTone(
            label = "실패",
            icon = Icons.Outlined.ErrorOutline,
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer
        )
        else -> RecordTone(
            label = "건너뜀",
            icon = Icons.Outlined.SkipNext,
            container = MaterialTheme.colorScheme.surfaceContainerHighest,
            content = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun recordTitle(record: RotationRecord): String {
    if (record.oldIp != null || record.newIp != null) {
        return "${record.oldIp ?: "-"} → ${record.newIp ?: "-"}"
    }
    if (record.method == RotationRecord.METHOD_SPEED_CHECK) {
        val mbps = Regex("""측정\s*([\d.]+)\s*Mbps""")
            .find(record.note ?: "")
            ?.groupValues
            ?.getOrNull(1)
        return if (mbps != null) "속도 측정 · $mbps Mbps" else "속도 측정"
    }
    return record.note ?: methodLabel(record.method) ?: "변경 없음"
}

private fun recordSubtitle(record: RotationRecord): String {
    val time = java.text.SimpleDateFormat("HH:mm", Locale.KOREA).format(java.util.Date(record.timestamp))
    val parts = mutableListOf<String>()
    methodLabel(record.method)?.let { parts += it }
    // 제목이 note를 쓰는 경우(속도/스킵)에는 중복을 피한다
    if (record.oldIp != null || record.newIp != null) {
        record.note?.takeIf { it.isNotBlank() }?.let { parts += it }
    }
    record.errorCode?.let { parts += it }
    parts += time
    return parts.joinToString(" · ")
}

private fun methodLabel(method: String?): String? = when (method) {
    RotationRecord.METHOD_DATA_RECONNECT -> "데이터 재연결"
    RotationRecord.METHOD_AIRPLANE -> "에어플레인"
    RotationRecord.METHOD_HOTSPOT_RESTART -> "핫스팟 재시작"
    RotationRecord.METHOD_SPEED_CHECK -> "속도 측정"
    else -> null
}

private fun dateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    val prefix = when (date) {
        today -> "오늘"
        today.minusDays(1) -> "어제"
        else -> null
    }
    val formatted = date.format(DateTimeFormatter.ofPattern("M월 d일", Locale.KOREA))
    return if (prefix != null) "$prefix · $formatted" else formatted
}

package com.borasarang.spotshift.ui.speed

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.borasarang.spotshift.core.NetInfo
import com.borasarang.spotshift.data.RotationPhase
import com.borasarang.spotshift.data.SpeedRecord
import com.borasarang.spotshift.ui.components.formatRate
import com.borasarang.spotshift.ui.theme.LocalSpotColors
import com.borasarang.spotshift.ui.theme.full

/**
 * T-49 — 속도 화면. 목업 `?screen=speed` 기준:
 * 숫자 히어로(다운로드) + 업로드·지연 2열 → 측정 액션 → 전/후 비교 카드 → 측정 기록 리스트.
 * 측정/변경 동작(ViewModel)은 그대로 유지하고 표시 계층만 재구성했다.
 */
@Composable
fun SpeedScreen(contentPadding: PaddingValues) {
    val vm: SpeedViewModel = viewModel()
    val config by vm.config.collectAsState()
    val records by vm.speedRecords.collectAsState()
    val shizukuReady by vm.shizukuReady.collectAsState()
    var net by remember { mutableStateOf(vm.currentNet()) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(vm.phase) {
        if (vm.phase == SpeedViewModel.Phase.IDLE) net = vm.currentNet()
    }

    val testing = vm.phase == SpeedViewModel.Phase.TESTING ||
        vm.phase == SpeedViewModel.Phase.CHANGING
    val measured = vm.downMbps
    val threshold = config.speedThresholdMbps
    val below = measured != null && measured < threshold

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("속도", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "현재 접속 · ${net.current}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                testing -> SpeedBadge(
                    label = "측정 중",
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurfaceVariant
                )
                measured != null && below -> SpeedBadge(
                    label = "기준 미달",
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer
                )
                measured != null -> SpeedBadge(
                    label = "기준 달성",
                    container = LocalSpotColors.current.successContainer,
                    content = LocalSpotColors.current.onSuccessContainer
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SpeedHeroCard(
            testing = testing,
            liveMbps = vm.liveMbps,
            downMbps = measured,
            upMbps = vm.upMbps,
            latencyMs = vm.latencyMs,
            liveLabel = vm.liveLabel,
            liveBytes = vm.liveBytes,
            wifi = net.wifi
        )

        Spacer(Modifier.height(12.dp))
        if (testing) {
            if (vm.phase == SpeedViewModel.Phase.CHANGING && vm.rotationPhase != RotationPhase.IDLE) {
                Text(
                    text = rotationText(vm.rotationPhase),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = { vm.cancelTest() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.full
            ) {
                Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("측정 취소", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Button(
                onClick = { vm.startTest() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.full
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("측정 시작", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "1회 측정에 최대 약 64MB 사용 (다운 최대 60MB + 업 최대 4MB)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (vm.measureFailed) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "측정 실패 — 다시 시도해주세요",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        // 미달 시 IP 변경 (DONE에서만, Shizuku 필요)
        if (vm.phase == SpeedViewModel.Phase.DONE && below) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = { vm.requestChange() },
                enabled = shizukuReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.full
            ) {
                Text("IP 변경하기 (기준 미달)")
            }
            if (!shizukuReady) {
                Text(
                    text = "Shizuku 연결 필요",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (vm.phase == SpeedViewModel.Phase.COMPARE) {
            Spacer(Modifier.height(16.dp))
            CompareCard(
                before = vm.beforeMbps,
                after = vm.afterMbps,
                threshold = threshold,
                maxRechecks = config.speedMaxRechecks,
                changeCount = vm.changeCount,
                shizukuReady = shizukuReady,
                onKeep = { vm.keepResult() },
                onChangeAgain = { vm.requestChange() }
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "최근 측정",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (records.isNotEmpty()) {
                TextButton(onClick = { showClearConfirm = true }) { Text("기록 초기화") }
            }
        }
        if (records.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Text(
                    text = "아직 측정 기록이 없습니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            SpeedRecordCard(records = records, onDelete = { vm.deleteRecord(it) })
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("기록 초기화") },
            text = { Text("측정 기록을 모두 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearRecords()
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
private fun SpeedHeroCard(
    testing: Boolean,
    liveMbps: Double,
    downMbps: Double?,
    upMbps: Double?,
    latencyMs: Long?,
    liveLabel: String,
    liveBytes: Long,
    wifi: String
) {
    val display = if (testing) liveMbps else downMbps
    val sub = when {
        testing -> liveLabel.ifEmpty { "측정 중" } +
            " · 사용량 ${"%.1f".format(liveBytes / 1024.0 / 1024.0)}MB"
        downMbps != null -> "≈ ${formatRate(downMbps)} · 최근 측정"
        else -> "측정 시작을 누르면 다운로드·업로드를 측정합니다"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "다운로드",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = MaterialTheme.shapes.full,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "Wi-Fi ${wifi}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
            Row(
                // 화면 낭독기가 "2.03 Mbps"를 한 문장으로 읽도록 병합
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.Bottom
            ) {
                if (display == null) {
                    Text(
                        text = "측정 전",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                } else {
                    Text(
                        text = "%.2f".format(display),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = " Mbps",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
            Text(
                text = sub,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                MiniMetric(
                    label = "업로드",
                    value = upMbps?.let { "%.2f Mbps".format(it) } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = "지연",
                    value = latencyMs?.let { "$it ms" } ?: "—",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CompareCard(
    before: Double?,
    after: Double?,
    threshold: Float,
    maxRechecks: Int,
    changeCount: Int,
    shizukuReady: Boolean,
    onKeep: () -> Unit,
    onChangeAgain: () -> Unit
) {
    val achieved = after != null && after >= threshold
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "IP 변경 전/후",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                SpeedBadge(
                    label = if (achieved) "기준 ${threshold.toInt()}Mbps 달성" else "기준 미달",
                    container = if (achieved) {
                        LocalSpotColors.current.successContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    content = if (achieved) {
                        LocalSpotColors.current.onSuccessContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompareBox(
                    label = "변경 전",
                    value = before?.let { "%.2f Mbps".format(it) } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                CompareBox(
                    label = "변경 후",
                    value = after?.let { "%.2f Mbps".format(it) } ?: "측정 실패",
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "자동 재측정 1회 · 기준 미달 시 최대 ${maxRechecks}회 재변경",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onKeep,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.full
                ) { Text("유지") }
                Button(
                    onClick = onChangeAgain,
                    enabled = shizukuReady && changeCount < 3,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.full
                ) { Text("다시 변경 ($changeCount/3)") }
            }
        }
    }
}

@Composable
private fun CompareBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SpeedRecordCard(records: List<SpeedRecord>, onDelete: (Long) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            records.forEachIndexed { index, record ->
                SpeedRecordRow(record = record, onDelete = { onDelete(record.id) })
                if (index < records.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 62.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedRecordRow(record: SpeedRecord, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "↓ ${record.downloadMbps?.let { "%.2f".format(it) } ?: "-"} · " +
                    "↑ ${record.uploadMbps?.let { "%.2f".format(it) } ?: "-"} Mbps",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = buildString {
                    append(formatTime(record.timestamp))
                    record.latencyMs?.let { append(" · 지연 ${it}ms") }
                    record.networkType.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "측정 기록 삭제",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SpeedBadge(label: String, container: Color, content: Color) {
    Surface(shape = MaterialTheme.shapes.full, color = container) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun rotationText(phase: RotationPhase): String = when (phase) {
    RotationPhase.IDLE -> "대기 중"
    RotationPhase.CHECKING_IP -> "현재 IP 확인 중"
    RotationPhase.ROTATING_DATA -> "모바일 데이터 재연결 중"
    RotationPhase.VERIFYING -> "IP 변경 확인 중"
    RotationPhase.RETRYING -> "재시도 중"
    RotationPhase.FALLBACK_AIRPLANE -> "에어플레인 폴백 시도"
    RotationPhase.SPEED_CHECKING -> "속도 측정 중"
    RotationPhase.SUCCESS -> "IP 변경 완료"
    RotationPhase.FAILED -> "IP 변경 실패"
    // T-40 — 온도 가드 연기
    RotationPhase.DEFERRED -> "온도 임계 초과로 연기"
}

private fun formatTime(ts: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.KOREA)
        .format(java.util.Date(ts))

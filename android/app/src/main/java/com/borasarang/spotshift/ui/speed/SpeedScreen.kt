package com.borasarang.spotshift.ui.speed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.borasarang.spotshift.data.RotationPhase
import com.borasarang.spotshift.ui.components.formatMbps
import com.borasarang.spotshift.ui.components.formatRate

/**
 * v0.5 (T-27) — 속도 탭. fast.com식 실시간 측정 + 미달 시 즉시 IP 변경 → 재측정 비교.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("속도", style = MaterialTheme.typography.headlineMedium)
        }

        Spacer(Modifier.height(16.dp))
        // 접속 상태
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text("현재 접속: ${net.current}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Wi-Fi: ${net.wifi}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "셀룰러: ${net.cell}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        when (vm.phase) {
            SpeedViewModel.Phase.TESTING, SpeedViewModel.Phase.CHANGING -> {
                Text(
                    vm.liveLabel.ifEmpty { "측정 중" },
                    style = MaterialTheme.typography.titleMedium
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "%.1f".format(vm.liveMbps),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        " Mbps",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Text("(≈ ${formatRate(vm.liveMbps)}) · 사용량 ${"%.1f".format(vm.liveBytes / 1024.0 / 1024.0)}MB")
                if (vm.phase == SpeedViewModel.Phase.CHANGING && vm.rotationPhase != RotationPhase.IDLE) {
                    Text(
                        rotationText(vm.rotationPhase),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { vm.cancelTest() }, modifier = Modifier.fillMaxWidth()) {
                    Text("취소")
                }
            }
            else -> {
                if (vm.measureFailed) {
                    Text(
                        "측정 실패 — 다시 시도해주세요",
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (vm.downMbps != null) {
                    val down = vm.downMbps!!
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "%.1f".format(down),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            " Mbps",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Text(
                        "(${formatRate(down)})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("다운로드")
                    vm.upMbps?.let { Text("업로드 ${formatMbps(it)}") }
                    vm.latencyMs?.let { Text("지연 ${it}ms") }
                    val threshold = config.speedThresholdMbps
                    val below = (vm.downMbps ?: Float.MAX_VALUE.toDouble()) < threshold
                    Text(
                        if (below) "기준(${threshold}Mbps) 미달" else "기준 달성",
                        color = if (below) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.secondary
                    )
                } else {
                    Text(
                        "측정 시작을 누르면 다운로드·업로드를 측정합니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.startTest() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("측정 시작")
                }
                Text(
                    "1회 측정에 최대 약 64MB 사용 (다운 최대 60MB + 업 최대 4MB)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // 미달 시 IP 변경 (DONE에서만, Shizuku 필요)
        if (vm.phase == SpeedViewModel.Phase.DONE && vm.downMbps != null &&
            vm.downMbps!! < config.speedThresholdMbps
        ) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.requestChange() },
                enabled = shizukuReady,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("IP 변경하기")
            }
            if (!shizukuReady) {
                Text(
                    "Shizuku 연결 필요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // 전/후 비교
        if (vm.phase == SpeedViewModel.Phase.COMPARE) {
            Spacer(Modifier.height(16.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text(
                        "변경 전 ${vm.beforeMbps?.let { formatMbps(it) } ?: "-"} → " +
                            "변경 후 ${vm.afterMbps?.let { formatMbps(it) } ?: "측정 실패"}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { vm.keepResult() },
                            modifier = Modifier.weight(1f)
                        ) { Text("유지") }
                        Button(
                            onClick = { vm.requestChange() },
                            enabled = shizukuReady && vm.changeCount < 3,
                            modifier = Modifier.weight(1f)
                        ) { Text("다시 변경 (${vm.changeCount}/3)") }
                    }
                }
            }
        }

        // 측정 기록
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("측정 기록", style = MaterialTheme.typography.titleMedium)
            if (records.isNotEmpty()) {
                TextButton(onClick = { showClearConfirm = true }) { Text("기록 초기화") }
            }
        }
        if (records.isEmpty()) {
            Text(
                "아직 측정 기록이 없습니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            records.forEach { r ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                formatTime(r.timestamp) + " · " + r.networkType,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "↓ ${r.downloadMbps?.let { "%.1f".format(it) } ?: "-"} Mbps" +
                                    (r.downloadMbps?.let { " (${formatRate(it.toDouble())})" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "↑ ${r.uploadMbps?.let { "%.1f".format(it) } ?: "-"} Mbps" +
                                    (r.uploadMbps?.let { " (${formatRate(it.toDouble())})" } ?: "") +
                                    (r.latencyMs?.let { " · ${it}ms" } ?: "") +
                                    " · ${"%.1f".format(r.bytesUsed / 1024.0 / 1024.0)}MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            r.signal?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(onClick = { vm.deleteRecord(r.id) }) { Text("삭제") }
                    }
                }
            }
        }
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

private fun rotationText(phase: RotationPhase): String = when (phase) {
    RotationPhase.IDLE -> "대기 중"
    RotationPhase.CHECKING_IP -> "현재 IP 확인 중"
    RotationPhase.ROTATING_DATA -> "IP 변경 중 (데이터 재연결)"
    RotationPhase.VERIFYING -> "IP 변경 확인 중"
    RotationPhase.RETRYING -> "재시도 중"
    RotationPhase.FALLBACK_AIRPLANE -> "에어플레인 폴백 시도"
    RotationPhase.SUCCESS -> "IP 변경 완료"
    RotationPhase.FAILED -> "IP 변경 실패"
}

private fun formatTime(ts: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.KOREA)
        .format(java.util.Date(ts))

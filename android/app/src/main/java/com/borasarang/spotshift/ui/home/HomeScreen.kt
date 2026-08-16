package com.borasarang.spotshift.ui.home

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.core.HotspotController
import com.borasarang.spotshift.core.ShizukuManager
import com.borasarang.spotshift.data.RotationPhase
import com.borasarang.spotshift.ui.components.ConditionChip
import com.borasarang.spotshift.ui.components.CountdownGauge
import com.borasarang.spotshift.ui.components.MainActionButton
import com.borasarang.spotshift.ui.components.RotationProgress
import com.borasarang.spotshift.ui.components.StatusCard
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(contentPadding: PaddingValues) {
    DebugLogger.feature("HomeScreen", "표시됨")
    val viewModel: HomeViewModel = viewModel()
    val config by viewModel.config.collectAsState()
    val context = LocalContext.current

    val hotspotController = remember { HotspotController(context) }
    var hotspotEnabled by remember { mutableStateOf(false) }
    var publicIp by remember { mutableStateOf<String?>(null) }
    // v0.3 — 마지막 변경 시각은 Prefs(config)에서 구독 — 앱 재시작/자동 변경에도 유지
    val lastRotationAt = config.lastRotationAt

    LaunchedEffect(Unit) {
        while (true) {
            hotspotEnabled = hotspotController.isHotspotEnabled()
            if (publicIp == null) {
                publicIp = com.borasarang.spotshift.core.IpVerifier().fetchPublicIp()
            }
            delay(5_000)
        }
    }

    // v0.3.1 — 카운트다운: 1초마다 now 갱신 → elapsed/remaining 재계산 (기존 tick은 읽히지 않아 재구성 안 되던 버그 수정)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val intervalSec = (config.intervalMinutes * 60L).coerceAtLeast(60L)
    val elapsed = if (lastRotationAt > 0) (now - lastRotationAt) / 1000 else 0L
    val remaining = (intervalSec - elapsed).coerceAtLeast(0L)

    // v0.3 — 다음 변경 예상 시각 ("HH:MM 예정")
    val nextChangeAtLabel = remember(lastRotationAt, intervalSec) {
        if (lastRotationAt > 0) {
            val nextAt = lastRotationAt + intervalSec * 1000
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA)
                .format(java.util.Date(nextAt)) + " 예정"
        } else null
    }

    val rotationState = viewModel.rotationState
    val running = rotationState.phase !in listOf(RotationPhase.IDLE, RotationPhase.SUCCESS, RotationPhase.FAILED)
    val shizukuReady = ShizukuManager.isReady

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("SpotShift", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "핫스팟 IP 변환기",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!shizukuReady) {
                Text(
                    "Shizuku 연결 필요",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        StatusCard(
            hotspotEnabled = hotspotEnabled,
            publicIp = publicIp ?: viewModel.rotationState.currentIp,
            clientCount = null
        )

        Spacer(Modifier.height(28.dp))
        // v0.3.1 — 변경 이력이 없으면 카운트다운 대신 안내 표시 (2:00:00 전체 표시 오해 방지)
        if (lastRotationAt > 0) {
            CountdownGauge(
                remainingSeconds = remaining,
                totalSeconds = intervalSec,
                gaugeColor = if (running) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                nextChangeAtLabel = nextChangeAtLabel
            )
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(width = 200.dp, height = 200.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "아직 변경 이력이 없습니다",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "IP 변경 시작을 누르면\n다음 변경 예상 시각을 표시합니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        // v0.3 — 진행 중에는 단계 인디케이터 표시, 완료/실패는 메시지 표시
        if (running) {
            RotationProgress(
                phase = rotationState.phase,
                attempt = rotationState.attempt,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        } else {
            rotationState.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (rotationState.phase) {
                        RotationPhase.SUCCESS -> MaterialTheme.colorScheme.secondary
                        RotationPhase.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
        MainActionButton(
            enabled = shizukuReady,
            running = running,
            onClick = {
                if (running) {
                    viewModel.setEnabled(false)
                } else {
                    viewModel.setEnabled(true)
                    viewModel.manualRotate { record ->
                        publicIp = record.newIp ?: publicIp
                    }
                }
            }
        )

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConditionChip(
                label = "배터리 ${config.minBatteryPercent}%",
                satisfied = true,
                icon = Icons.Outlined.BatteryStd
            )
            ConditionChip(
                label = config.scheduleWindowStart?.let { "시간대 ${it}시~${config.scheduleWindowEnd}시" } ?: "항상",
                satisfied = true,
                icon = Icons.Outlined.Schedule
            )
            ConditionChip(
                label = "신호 ${config.minSignalDbm}dBm",
                satisfied = true,
                icon = Icons.Outlined.SignalCellularAlt
            )
        }
    }
}

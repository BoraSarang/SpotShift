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
import com.borasarang.spotshift.ui.components.RecentChangeCard
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
    // v0.4 (T-19) — 접속 상태 5초 폴링
    var signalText by remember { mutableStateOf<String?>(null) }
    // 환경 체온계 (관문 아님): 실시간 배터리 + LTE 신호 표시용
    var batteryPercent by remember { mutableStateOf<Int?>(null) }
    // v0.3 — 마지막 변경 시각은 Prefs(config)에서 구독 — 앱 재시작/자동 변경에도 유지
    val lastRotationAt = config.lastRotationAt

    LaunchedEffect(Unit) {
        while (true) {
            // T-31: 조회 실패(null)는 이전 표시 유지 — 꺼짐 오판 금지
            hotspotEnabled = hotspotController.isHotspotEnabled() ?: hotspotEnabled
            if (publicIp == null) {
                publicIp = com.borasarang.spotshift.core.IpVerifier().fetchPublicIp()
            }
            signalText = com.borasarang.spotshift.core.SignalMonitor(context).snapshot().display()
            batteryPercent = runCatching {
                val bm = context.getSystemService(android.os.BatteryManager::class.java)
                    ?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    ?.takeIf { it >= 0 }
                bm
            }.getOrNull()
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
    // v0.4 — Shizuku 상태 구독 (스냅샷 고착 수정)
    val shizukuReady by viewModel.shizukuReady.collectAsState()
    val lastSpeed by viewModel.lastSpeed.collectAsState()
    val records by viewModel.records.collectAsState()

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
            clientCount = null,
            // 변경 후 속도 검증 기준 표시
            speedThresholdText = "속도 기준: ${"%.0f".format(config.speedThresholdMbps)}Mbps 미만 시 재변경",
            // v0.4 — 최근 변경 요약 (x→y + 측정 속도/사유)
            // v0.5 — B/s 병기
            lastSpeedText = lastSpeed?.let {
                "최근 측정: ${com.borasarang.spotshift.ui.components.formatMbps(it.toDouble())}"
            },
            // v0.4 (T-19) — 접속 상태 표시
            signalText = signalText
        )

        val latestRecord = records.firstOrNull()
        if (latestRecord != null) {
            Spacer(Modifier.height(16.dp))
            RecentChangeCard(record = latestRecord)
        }

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
                totalAttempts = config.retryCount + 1,
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
        // 변경 규칙 안내 (주기·기준·최대 반복 — 설정과 연동)
        val intervalLabel = if (config.intervalMinutes % 60 == 0) {
            "${config.intervalMinutes / 60}시간마다"
        } else {
            "${config.intervalMinutes}분마다"
        }
        Text(
            text = "$intervalLabel 변경 · ${"%.0f".format(config.speedThresholdMbps)}Mbps 미만이면 재변경(최대 ${config.speedMaxRechecks}회)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        MainActionButton(
            enabled = shizukuReady,
            running = running,
            onClick = {
                // v0.4 — Shizuku 미승인 상태에서 버튼을 누르면 권한 요청 동작
                if (!shizukuReady) {
                    DebugLogger.feature("HomeScreen", "메인 버튼 권한 요청")
                    viewModel.requestShizukuPermission()
                } else if (running) {
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
                label = batteryPercent?.let { "배터리 ${it}%" } ?: "배터리 -",
                satisfied = true,
                icon = Icons.Outlined.BatteryStd
            )
            ConditionChip(
                label = signalText ?: "신호 -",
                satisfied = true,
                icon = Icons.Outlined.SignalCellularAlt
            )
        }
    }
}

package com.borasarang.spotshift.ui.home

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Shield
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.core.HotspotController
import com.borasarang.spotshift.core.IpVerifier
import com.borasarang.spotshift.core.RotationGate
import com.borasarang.spotshift.core.SignalInfo
import com.borasarang.spotshift.core.SignalMonitor
import com.borasarang.spotshift.core.ThermalGuard
import com.borasarang.spotshift.data.RecordLabels
import com.borasarang.spotshift.data.RotationPhase
import com.borasarang.spotshift.ui.components.ConditionChip
import com.borasarang.spotshift.ui.components.CountdownGauge
import com.borasarang.spotshift.ui.components.MainActionButton
import com.borasarang.spotshift.ui.components.RecentChangeCard
import com.borasarang.spotshift.ui.components.RotationProgress
import com.borasarang.spotshift.ui.components.StatusCard
import com.borasarang.spotshift.ui.theme.LocalSpotColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** T-60 — 헤더 공인 IP 실조회 주기 (5초 틱 × 12 = 60초). */
private const val IP_REFRESH_TICKS = 12

/**
 * T-48 — 홈(상태) 화면. 목업 `docs/design/mockup_v2.html?screen=home` 순서를 따른다.
 *
 * 1) 히어로 카드: 핫스팟/자동 배지 + 공인 IP + 3지표(최근 측정·기준·온도)
 * 2) 카운트다운 오브 192dp + 예상 시각 pill + 규칙 한 줄
 * 3) 주 액션 1개(56dp filled) + 자동 변경 상태
 * 4) 조건 칩 1줄(배터리·RSRP·가드)
 * 5) 최근 변경 카드
 */
@Composable
fun HomeScreen(contentPadding: PaddingValues) {
    // T-43 — 진입 로그는 1회만 (본문이면 재구성마다 찍혀 로그 버퍼를 잠식)
    LaunchedEffect(Unit) {
        DebugLogger.feature("HomeScreen", "표시됨")
    }
    val viewModel: HomeViewModel = viewModel()
    val config by viewModel.config.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()

    val hotspotController = remember { HotspotController(context) }
    val thermalGuard = remember { ThermalGuard(context) }
    val ipVerifier = remember { IpVerifier() }
    var hotspotEnabled by remember { mutableStateOf(false) }
    var publicIp by remember { mutableStateOf<String?>(null) }
    var signal by remember { mutableStateOf<SignalInfo?>(null) }
    var batteryPercent by remember { mutableStateOf<Int?>(null) }
    var temperatureC by remember { mutableStateOf<Float?>(null) }
    // T-60 — 지금 IP 변경이 진행 중이면 그 출처 (수동/자동/외부 요청 …)
    var liveSource by remember { mutableStateOf<RotationGate.Source?>(null) }
    // v0.3 — 마지막 변경 시각은 Prefs(config)에서 구독 — 앱 재시작/자동 변경에도 유지
    val lastRotationAt = config.lastRotationAt

    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            // T-31: 조회 실패(null)는 이전 표시 유지 — 꺼짐 오판 금지
            hotspotEnabled = hotspotController.isHotspotEnabled() ?: hotspotEnabled
            // T-60 — 상단 IP 갱신:
            //  변경 경로(수동/자동/감시/원격)는 Prefs.lastKnownIp를 갱신하고, 아래 LaunchedEffect가
            //  즉시 헤더에 반영한다. 여기서는 60초마다 실제 조회로 보정한다(통신사가 IP를 바꾼 경우).
            if (tick % IP_REFRESH_TICKS == 0) {
                publicIp = ipVerifier.fetchPublicIp() ?: publicIp
            }
            tick++
            liveSource = RotationGate.currentSource()
            signal = SignalMonitor(context).snapshot()
            temperatureC = thermalGuard.batteryTempC()
            batteryPercent = runCatching {
                context.getSystemService(android.os.BatteryManager::class.java)
                    ?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    ?.takeIf { it >= 0 }
            }.getOrNull()
            delay(5_000)
        }
    }

    // T-60 — 자동/원격/감시 변경은 이 화면 밖(워커)에서 실행된다. 모든 경로가 남기는
    // Prefs.lastKnownIp를 구독해 변경 즉시 헤더 IP를 새 값으로 바꾼다 (기존: 최초 1회만 조회).
    LaunchedEffect(config.lastKnownIp) {
        config.lastKnownIp?.let { publicIp = it }
    }

    // v0.3.1 — 카운트다운: 1초마다 now 갱신 → elapsed/remaining 재계산
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

    // v0.3 — 다음 변경 예상 시각 ("HH:mm 예정")
    val nextChangeAtLabel = remember(lastRotationAt, intervalSec) {
        if (lastRotationAt > 0) {
            val nextAt = lastRotationAt + intervalSec * 1000
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA)
                .format(java.util.Date(nextAt)) + " 예정"
        } else {
            null
        }
    }

    val rotationState = viewModel.rotationState
    // T-40 — DEFERRED는 종료 상태 (스피너 대상 아님)
    val running = rotationState.phase !in listOf(
        RotationPhase.IDLE, RotationPhase.SUCCESS, RotationPhase.FAILED, RotationPhase.DEFERRED
    )
    // v0.4 — Shizuku 상태 구독 (스냅샷 고착 수정)
    val shizukuReady by viewModel.shizukuReady.collectAsState()
    val lastSpeed by viewModel.lastSpeed.collectAsState()
    val records by viewModel.records.collectAsState()
    val guardOn by viewModel.thermalGuard.collectAsState()

    val thresholdLabel = "${"%.0f".format(config.speedThresholdMbps)} Mbps"
    val signalSummary = signal?.let { s ->
        listOfNotNull(
            s.carrier,
            s.rat,
            s.rsrpDbm?.let { "RSRP ${it}dBm" }
        ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
    val intervalLabel = if (config.intervalMinutes % 60 == 0) {
        "${config.intervalMinutes / 60}시간마다"
    } else {
        "${config.intervalMinutes}분마다"
    }

    // T-60 — IP 변경 방법 표시: 진행 중 출처 > 최근 성공 변경(출처·수단·시각)
    val changeTimeFormat = remember {
        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.KOREA)
    }
    val lastChangeSummary = records.firstOrNull { it.changed }?.let { rec ->
        "최근 변경 · " + listOfNotNull(
            RecordLabels.changeLine(rec.source, rec.sourceDetail, rec.method),
            changeTimeFormat.format(java.util.Date(rec.timestamp))
        ).joinToString(" · ")
    }
    val runningSummary = liveSource?.let { "지금 ${it.ko} 진행 중" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
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
                    style = MaterialTheme.typography.labelMedium,
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

        Spacer(Modifier.height(16.dp))
        StatusCard(
            hotspotEnabled = hotspotEnabled,
            publicIp = publicIp ?: config.lastKnownIp,
            autoEnabled = config.enabled,
            signalSummary = signalSummary,
            lastMeasuredText = lastSpeed?.let { "%.2f Mbps".format(it) },
            thresholdText = thresholdLabel,
            temperatureText = temperatureC?.let { "%.1f °C".format(it) },
            runningSummary = runningSummary,
            changeSummary = lastChangeSummary,
            onCopyIp = { ip ->
                clipboardScope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(android.content.ClipData.newPlainText("IP", ip))
                    )
                }
                Toast.makeText(context, "IP를 복사했습니다", Toast.LENGTH_SHORT).show()
            }
        )

        Spacer(Modifier.height(20.dp))
        // v0.3.1 — 변경 이력이 없으면 카운트다운 대신 안내 (2:00:00 전체 표시 오해 방지)
        if (lastRotationAt > 0) {
            CountdownGauge(
                remainingSeconds = remaining,
                totalSeconds = intervalSec,
                gaugeColor = if (running) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                nextChangeAtLabel = nextChangeAtLabel
            )
        } else {
            Surface(
                modifier = Modifier.size(192.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "아직 변경 이력이 없습니다",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "IP 변경을 시작하면\n다음 예정 시각을 표시합니다",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // 변경 규칙 안내 (주기·기준·최대 반복 — 설정과 연동)
        Text(
            text = "$intervalLabel · ${"%.0f".format(config.speedThresholdMbps)}Mbps 미만이면 " +
                "최대 ${config.speedMaxRechecks}회 재변경",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))
        // 진행 중에는 단계 인디케이터, 완료/실패는 메시지 (목업에는 없는 동작 피드백)
        if (running) {
            RotationProgress(
                phase = rotationState.phase,
                attempt = rotationState.attempt,
                totalAttempts = config.retryCount + 1,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        } else {
            rotationState.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = when (rotationState.phase) {
                        RotationPhase.SUCCESS -> LocalSpotColors.current.success
                        RotationPhase.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        // T-45 — 일회성 수동 변경 전용. "자동 IP 변경" 토글은 설정 탭·퀵세팅 타일만 조작한다.
        MainActionButton(
            enabled = shizukuReady,
            running = running,
            onClick = {
                if (!shizukuReady) {
                    DebugLogger.feature("HomeScreen", "메인 버튼 권한 요청")
                    viewModel.requestShizukuPermission()
                } else {
                    viewModel.manualRotate { record ->
                        publicIp = record.newIp ?: publicIp
                    }
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (config.enabled) {
                "자동 IP 변경 켜짐 · 설정에서 끄기"
            } else {
                "자동 IP 변경 꺼짐 · 설정에서 켤 수 있음"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (config.enabled) {
                LocalSpotColors.current.success
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConditionChip(
                label = batteryPercent?.let { "배터리 $it%" } ?: "배터리 -",
                satisfied = true,
                icon = Icons.Outlined.BatteryStd
            )
            ConditionChip(
                label = signal?.rsrpDbm?.let { "RSRP $it" } ?: "신호 -",
                satisfied = true,
                icon = Icons.Outlined.SignalCellularAlt
            )
            ConditionChip(
                label = if (guardOn) "가드 ON" else "가드 OFF",
                satisfied = guardOn,
                icon = Icons.Outlined.Shield
            )
        }

        // "최근 변경" 카드는 IP 변경 기록을 우선한다 (속도 측정 단독 기록은 변경이 아님).
        (records.firstOrNull { it.oldIp != null || it.newIp != null } ?: records.firstOrNull())
            ?.let { latestRecord ->
                Spacer(Modifier.height(12.dp))
                RecentChangeCard(record = latestRecord)
            }
        Spacer(Modifier.height(16.dp))
    }
}

package com.borasarang.spotshift.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.borasarang.spotshift.BuildConfig
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.core.ShizukuManager
import com.borasarang.spotshift.ui.home.HomeViewModel

@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    DebugLogger.feature("SettingsScreen", "표시됨")
    val viewModel: HomeViewModel = viewModel()
    val config by viewModel.config.collectAsState()
    // v0.4 — Shizuku 상태 구독 (스냅샷 고착 수정)
    val shizukuReady by viewModel.shizukuReady.collectAsState()
    val batteryUnrestricted by viewModel.batteryUnrestricted.collectAsState()
    val context = LocalContext.current

    // v0.4 — 시스템 설정에서 돌아오면 배터리 상태 갱신
    DisposableEffect(Unit) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        if (lifecycle == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshBatteryState()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    var intervalMinutes by remember { mutableStateOf(config.intervalMinutes) }
    var enabled by remember { mutableStateOf(config.enabled) }
    var minBattery by remember { mutableStateOf(config.minBatteryPercent) }
    var minSignal by remember { mutableStateOf(config.minSignalDbm) }
    var retryCount by remember { mutableStateOf(config.retryCount) }
    var fallbackEnabled by remember { mutableStateOf(config.fallbackEnabled) }
    var hotspotAutoEnable by remember { mutableStateOf(config.hotspotAutoEnable) }
    // v0.4 — 변경 후 속도 검증용 (건너뛰기 없음)
    var speedThreshold by remember { mutableStateOf(config.speedThresholdMbps) }
    var speedMaxRechecks by remember { mutableStateOf(config.speedMaxRechecks) }
    var bootAutoStart by remember { mutableStateOf(config.bootAutoStart) }
    var eventAlertEnabled by remember { mutableStateOf(config.eventAlertEnabled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle("Shizuku")
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
                Text(
                    text = if (shizukuReady) "연결됨" else "연결 필요",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (shizukuReady) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = {
                    DebugLogger.feature("SettingsScreen", "Shizuku 권한 요청")
                    viewModel.requestShizukuPermission()
                }) {
                    Text("권한 요청")
                }
            }
        }

        // v0.4 — 배터리 최적화 제외 (삼성 백그라운드 종료 방지)
        SectionTitle("배터리")
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (batteryUnrestricted) "제한 없음" else "최적화 중",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (batteryUnrestricted) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            // v0.4 — 최적화 중은 기본 상태라 실패색 대신 중립색
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (!batteryUnrestricted) {
                        OutlinedButton(onClick = {
                            viewModel.openBatteryOptimizationSettings()
                        }) {
                            Text("설정 열기")
                        }
                    }
                }
                Text(
                    "백그라운드에서 강제 종료되지 않도록 배터리 최적화에서 제외합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionTitle("주기")
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "변경 주기: ${intervalMinutes}분",
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(
                    value = intervalMinutes.toFloat(),
                    onValueChange = { intervalMinutes = it.toInt() },
                    onValueChangeFinished = {
                        viewModel.updateIntervalMinutes(intervalMinutes)
                    },
                    // v0.2 — 요구사항 1: 30분~720분(12시간), 30분 단위
                    valueRange = MIN_INTERVAL_MINUTES.toFloat()..MAX_INTERVAL_MINUTES.toFloat(),
                    steps = ((MAX_INTERVAL_MINUTES - MIN_INTERVAL_MINUTES) / INTERVAL_STEP).toInt() - 1
                )
                Text(
                    "IP가 변경된 지 주기가 지나지 않았으면 자동으로 변경하지 않습니다.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionTitle("스마트 조건")
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "최소 배터리: ${minBattery}%",
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(
                    value = minBattery.toFloat(),
                    onValueChange = { minBattery = it.toInt() },
                    onValueChangeFinished = {
                        viewModel.updateMinBattery(minBattery)
                    },
                    valueRange = 0f..100f,
                    steps = 19
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    "최소 신호: ${minSignal}dBm",
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(
                    value = minSignal.toFloat(),
                    onValueChange = { minSignal = it.toInt() },
                    onValueChangeFinished = {
                        viewModel.updateMinSignal(minSignal)
                    },
                    valueRange = -120f..-60f,
                    steps = 11
                )
            }
        }

        // v0.4 (T-17) — 속도 기반 조건부 실행
        SectionTitle("속도 검증")
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("변경 후 속도 검증", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "IP 변경 후 속도를 측정해 기준 미만이면 다시 변경합니다. (측정 1회 약 1MB)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    "속도 기준: ${"%.0f".format(speedThreshold)}Mbps",
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(
                    value = speedThreshold,
                    onValueChange = { speedThreshold = it },
                    onValueChangeFinished = {
                        viewModel.updateSpeedThreshold(speedThreshold)
                    },
                    valueRange = 1f..50f,
                    steps = 48
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    "최대 반복: ${speedMaxRechecks}회",
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(
                    value = speedMaxRechecks.toFloat(),
                    onValueChange = { speedMaxRechecks = it.toInt() },
                    onValueChangeFinished = {
                        viewModel.updateSpeedRechecks(speedMaxRechecks)
                    },
                    valueRange = 1f..5f,
                    steps = 3
                )
            }
        }

        SectionTitle("IP 변경")
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // v0.2 — 요구사항 5: IP 변경 여부 (자동 스케줄)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("자동 IP 변경", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { value ->
                            enabled = value
                            viewModel.setEnabled(value)
                        }
                    )
                }
                Text(
                    "설정한 주기에 따라 셀룰러 모드에서만 IP를 자동으로 변경합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                // v0.2 — 요구사항 5: 핫스팟 자동 켜기
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("핫스팟 자동 켜기", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = hotspotAutoEnable,
                        onCheckedChange = { value ->
                            hotspotAutoEnable = value
                            viewModel.updateHotspotAuto(value)
                        }
                    )
                }
                Text(
                    "IP 변경 후 핫스팟이 꺼져 있으면 설정 화면을 열어 안내합니다. (Android 16에서 자동 ON 제한)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("에어플레인 폴백", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = fallbackEnabled,
                        onCheckedChange = { value ->
                            fallbackEnabled = value
                            viewModel.updateFallback(value)
                        }
                    )
                }
                Text(
                    "모바일 데이터 재연결로 IP 변경 실패 시 에어플레인을 사용합니다. 핫스팟이 꺼질 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    "재시도 횟수: $retryCount",
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(
                    value = retryCount.toFloat(),
                    onValueChange = { retryCount = it.toInt() },
                    onValueChangeFinished = {
                        viewModel.updateRetryCount(retryCount)
                    },
                    valueRange = 0f..5f,
                    steps = 4
                )
                // v0.4 (T-18) — 부팅 시 자동 시작
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("부팅 시 자동 시작", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = bootAutoStart,
                        onCheckedChange = { value ->
                            bootAutoStart = value
                            viewModel.updateBootAuto(value)
                        }
                    )
                }
                Text(
                    "재부팅 후 스케줄을 자동으로 복원합니다. Shizuku는 직접 다시 시작해야 합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // v0.4 — 완료 시 소리 알림 (상시 1개 유지, 10초 후 자동 소멸)
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("완료 시 소리 알림", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = eventAlertEnabled,
                        onCheckedChange = { value ->
                            eventAlertEnabled = value
                            viewModel.updateEventAlert(value)
                        }
                    )
                }
                Text(
                    "IP 변경 완료 때 소리가 1회 울립니다 (알림은 10초 후 자동 소멸, 상태 알림 1개 유지).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "통신사 정책에 따라 IP가 변경되지 않을 수 있습니다. 실패 시 재시도 후 안내합니다.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // v0.3 — 요구사항 2: 제작자/문의 메일/GitHub 링크
        Spacer(Modifier.height(16.dp))
        SectionTitle("문의")
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                AboutRow(label = "제작자", value = "BoRaSaRang")
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                AboutRow(
                    label = "문의 메일",
                    value = "leeborasarang@gmail.com",
                    onClick = {
                        DebugLogger.feature("SettingsScreen", "문의 메일 열기")
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:leeborasarang@gmail.com"))
                            )
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                AboutRow(
                    label = "GitHub",
                    value = "github.com/BoraSarang/SpotShift",
                    onClick = {
                        DebugLogger.feature("SettingsScreen", "GitHub 열기")
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/BoraSarang/SpotShift"))
                            )
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    "SpotShift v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AboutRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

private const val MIN_INTERVAL_MINUTES = 30
private const val MAX_INTERVAL_MINUTES = 720
private const val INTERVAL_STEP = 30

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

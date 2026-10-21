package com.borasarang.spotshift.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.borasarang.spotshift.BuildConfig
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.ui.home.HomeViewModel
import com.borasarang.spotshift.ui.theme.LocalSpotColors
import com.borasarang.spotshift.ui.theme.ThemeMode
import com.borasarang.spotshift.ui.theme.full
import kotlin.math.roundToInt

/**
 * T-50 — 설정 화면. 목업 `?screen=settings` 기준으로 그룹 카드 구조로 재구성했다.
 *
 * 그룹: Shizuku → 자동 변경(주기·재시도·속도 검증) → 화면(테마·동적 색상) →
 * 가드(온도·전원·속도 감시·에어플레인 폴백) → 알림(부팅·소리·핫스팟 안내) →
 * 백그라운드(배터리 최적화) → 연동·정보.
 * Prefs 키와 ViewModel 호출은 그대로 유지한다(동작 불변).
 */
@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    // T-43 — 진입 로그는 1회만 (본문이면 재구성마다 찍혀 로그 버퍼를 잠식)
    LaunchedEffect(Unit) {
        DebugLogger.feature("SettingsScreen", "표시됨")
    }
    val viewModel: HomeViewModel = viewModel()
    val config by viewModel.config.collectAsState()
    // v0.4 — Shizuku 상태 구독 (스냅샷 고착 수정)
    val shizukuReady by viewModel.shizukuReady.collectAsState()
    val batteryUnrestricted by viewModel.batteryUnrestricted.collectAsState()
    // T-40 — 온도 가드 토글
    val thermalGuard by viewModel.thermalGuard.collectAsState()
    // T-41 — 전원 감시 토글
    val powerGuard by viewModel.powerGuard.collectAsState()
    // T-42 — 속도 감시 토글
    val speedWatch by viewModel.speedWatch.collectAsState()
    // T-47 — 테마 모드 / 동적 색상
    val themeModeKey by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val canUseDynamicColor = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val context = LocalContext.current

    // v0.4 — 시스템 설정에서 돌아오면 배터리 상태 갱신
    // T-37 — Shizuku 설치 상태도 함께 갱신
    DisposableEffect(Unit) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        if (lifecycle == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBatteryState()
                viewModel.refreshShizukuInstalled()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    var intervalMinutes by remember { mutableStateOf(config.intervalMinutes) }
    var enabled by remember { mutableStateOf(config.enabled) }
    var retryCount by remember { mutableStateOf(config.retryCount) }
    var fallbackEnabled by remember { mutableStateOf(config.fallbackEnabled) }
    var hotspotAutoEnable by remember { mutableStateOf(config.hotspotAutoEnable) }
    // v0.4 — 변경 후 속도 검증용 (건너뛰기 없음)
    var speedThreshold by remember { mutableStateOf(config.speedThresholdMbps) }
    var speedMaxRechecks by remember { mutableStateOf(config.speedMaxRechecks) }
    var bootAutoStart by remember { mutableStateOf(config.bootAutoStart) }
    var eventAlertEnabled by remember { mutableStateOf(config.eventAlertEnabled) }
    // T-33 — 연동 계약 v1: 외부 원격 요청 허용
    var pluginAllowed by remember { mutableStateOf(config.pluginAllowed) }

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
                Text("설정", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "SpotShift v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        ShizukuHeroCard(
            ready = shizukuReady,
            installed = viewModel.shizukuInstalled,
            onOpen = {
                DebugLogger.feature("SettingsScreen", "Shizuku 실행/설치 안내")
                viewModel.openShizuku()
            },
            onGithub = { viewModel.openShizukuGithub() }
        )

        GroupLabel("자동 변경")
        SettingCard {
            SwitchRow(
                title = "자동 IP 변경",
                sub = "주기마다 자동 실행",
                checked = enabled,
                onCheckedChange = { value ->
                    enabled = value
                    viewModel.setEnabled(value)
                }
            )
            SettingDivider()
            ValueSlider(
                title = "주기",
                valueText = if (intervalMinutes % 60 == 0) {
                    "${intervalMinutes / 60}시간"
                } else {
                    "${intervalMinutes}분"
                },
                value = intervalMinutes.toFloat(),
                onValueChange = {
                    intervalMinutes = ((it / INTERVAL_STEP).roundToInt() * INTERVAL_STEP)
                        .coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
                },
                onValueChangeFinished = { viewModel.updateIntervalMinutes(intervalMinutes) },
                valueRange = MIN_INTERVAL_MINUTES.toFloat()..MAX_INTERVAL_MINUTES.toFloat(),
                steps = 0,
                minLabel = "30분",
                maxLabel = "12시간"
            )
            SettingDivider()
            ValueSlider(
                title = "재시도 횟수",
                valueText = "${retryCount}회",
                value = retryCount.toFloat(),
                onValueChange = { retryCount = it.toInt() },
                onValueChangeFinished = { viewModel.updateRetryCount(retryCount) },
                valueRange = 0f..5f,
                steps = 0
            )
            SettingDivider()
            Text(
                "속도 검증",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "변경 후 기준 미달 시 다시 변경합니다 (측정 1회 약 1MB)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ValueSlider(
                title = "기준",
                valueText = "${"%.1f".format(speedThreshold)} Mbps",
                value = speedThreshold,
                onValueChange = { speedThreshold = it.roundToInt().toFloat() },
                onValueChangeFinished = { viewModel.updateSpeedThreshold(speedThreshold) },
                valueRange = 1f..50f,
                steps = 0
            )
            ValueSlider(
                title = "최대 반복",
                valueText = "${speedMaxRechecks}회",
                value = speedMaxRechecks.toFloat(),
                onValueChange = { speedMaxRechecks = it.toInt() },
                onValueChangeFinished = { viewModel.updateSpeedRechecks(speedMaxRechecks) },
                valueRange = 1f..5f,
                steps = 0
            )
        }

        GroupLabel("화면")
        SettingCard {
            Text(
                "테마",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "시스템 설정을 따르거나 직접 선택",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            // T-47 — 테마 모드 (시스템/라이트/다크)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = ThemeMode.fromKey(themeModeKey) == mode,
                        onClick = { viewModel.updateThemeMode(mode.name) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ThemeMode.entries.size
                        ),
                        label = { Text(mode.label) }
                    )
                }
            }
            SettingDivider()
            SwitchRow(
                title = "배경화면 색상 사용",
                sub = if (canUseDynamicColor) {
                    "배경화면 색에서 앱 색을 생성합니다"
                } else {
                    "Android 12 이상에서 사용할 수 있습니다"
                },
                checked = dynamicColor,
                enabled = canUseDynamicColor,
                onCheckedChange = { viewModel.updateDynamicColor(it) }
            )
        }

        GroupLabel("가드")
        SettingCard {
            SwitchRow(
                title = "온도 가드",
                sub = "배터리 45°C·thermal emergency 시 연기",
                checked = thermalGuard,
                onCheckedChange = { viewModel.updateThermalGuard(it) }
            )
            SettingDivider()
            SwitchRow(
                title = "전원 감시",
                sub = "미충전 20% 이하 연기 · 충전기 분리 알림",
                checked = powerGuard,
                onCheckedChange = { viewModel.updatePowerGuard(it) }
            )
            SettingDivider()
            SwitchRow(
                title = "속도 감시",
                sub = "주기 외 저속 감지 시 자동 변경 (평소 소비 0)",
                checked = speedWatch,
                onCheckedChange = { viewModel.updateSpeedWatch(it) }
            )
            SettingDivider()
            SwitchRow(
                title = "에어플레인 폴백",
                sub = "데이터 재연결 실패 시 사용 · 핫스팟이 꺼질 수 있음",
                checked = fallbackEnabled,
                onCheckedChange = { value ->
                    fallbackEnabled = value
                    viewModel.updateFallback(value)
                }
            )
        }

        GroupLabel("알림")
        SettingCard {
            SwitchRow(
                title = "부팅 시 자동 시작",
                sub = "재부팅 후 스케줄 복원 (Shizuku는 직접 시작)",
                checked = bootAutoStart,
                onCheckedChange = { value ->
                    bootAutoStart = value
                    viewModel.updateBootAuto(value)
                }
            )
            SettingDivider()
            SwitchRow(
                title = "완료 시 소리 알림",
                sub = "완료 1회 · 알림은 10초 후 자동 소멸",
                checked = eventAlertEnabled,
                onCheckedChange = { value ->
                    eventAlertEnabled = value
                    viewModel.updateEventAlert(value)
                }
            )
            SettingDivider()
            SwitchRow(
                title = "핫스팟 꺼짐 안내",
                sub = "IP 변경 후 꺼져 있으면 설정 화면 안내",
                checked = hotspotAutoEnable,
                onCheckedChange = { value ->
                    hotspotAutoEnable = value
                    viewModel.updateHotspotAuto(value)
                }
            )
        }

        GroupLabel("백그라운드")
        SettingCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "배터리 최적화",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (batteryUnrestricted) {
                            "제외됨 · 백그라운드 종료 제한 없음"
                        } else {
                            "최적화 중 · 백그라운드에서 종료될 수 있습니다"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (batteryUnrestricted) {
                            LocalSpotColors.current.success
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (batteryUnrestricted) {
                            viewModel.openBatteryOptimizationSettings()
                        } else {
                            viewModel.requestBatteryExemption()
                        }
                    },
                    shape = MaterialTheme.shapes.full
                ) {
                    Text(if (batteryUnrestricted) "설정" else "예외 설정")
                }
            }
        }

        GroupLabel("연동 · 정보")
        SettingCard {
            SwitchRow(
                title = "연동 허용",
                sub = "외부 앱의 원격 IP 변경 요청 수신",
                checked = pluginAllowed,
                onCheckedChange = { value ->
                    pluginAllowed = value
                    viewModel.updatePluginAllowed(value)
                }
            )
            SettingDivider()
            AboutRow(label = "버전", value = "v${BuildConfig.VERSION_NAME}")
            SettingDivider()
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
            SettingDivider()
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
            SettingDivider()
            AboutRow(label = "제작자", value = "BoRaSaRang")
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "통신사 정책에 따라 IP가 변경되지 않을 수 있습니다. 실패 시 재시도 후 안내합니다.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ShizukuHeroCard(
    ready: Boolean,
    installed: Boolean,
    onOpen: () -> Unit,
    onGithub: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (ready) {
                                LocalSpotColors.current.successContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = if (ready) {
                            LocalSpotColors.current.onSuccessContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (ready) "Shizuku 연결됨" else "Shizuku 연결 필요",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (ready) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    Text(
                        text = if (ready) "셸 권한 정상" else "APN·IP 변경에 셸 권한이 필요합니다",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!ready) {
                    // T-37 — 상태별 동작: 미설치→설치 안내, 설치됨→앱 실행
                    TextButton(onClick = onOpen) {
                        Text(if (installed) "실행" else "설치")
                    }
                }
            }
            SettingDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onGithub)
                    .padding(vertical = 6.dp)
            ) {
                Column {
                    Text(
                        text = "Shizuku GitHub",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "github.com/RikkaApps/Shizuku",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            content = content
        )
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun SwitchRow(
    title: String,
    sub: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (!sub.isNullOrBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 값 배지가 붙은 슬라이더 (목업의 "주기 2시간" / "기준 2.0 Mbps" 형태).
 * 슬라이더는 드래그 중 로컬 상태로 갱신하고, 손을 떼면 Prefs에 저장한다.
 */
@Composable
private fun ValueSlider(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    minLabel: String? = null,
    maxLabel: String? = null
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            ValueBadge(text = valueText)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps
        )
        if (minLabel != null && maxLabel != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = minLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = maxLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ValueBadge(text: String) {
    Surface(
        shape = MaterialTheme.shapes.full,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
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
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = if (onClick != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 2
        )
    }
}

private const val MIN_INTERVAL_MINUTES = 30
private const val MAX_INTERVAL_MINUTES = 720
private const val INTERVAL_STEP = 30

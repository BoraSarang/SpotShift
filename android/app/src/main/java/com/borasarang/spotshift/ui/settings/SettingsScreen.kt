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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val context = LocalContext.current

    var intervalMinutes by remember { mutableStateOf(config.intervalMinutes) }
    var enabled by remember { mutableStateOf(config.enabled) }
    var minBattery by remember { mutableStateOf(config.minBatteryPercent) }
    var minSignal by remember { mutableStateOf(config.minSignalDbm) }
    var retryCount by remember { mutableStateOf(config.retryCount) }
    var fallbackEnabled by remember { mutableStateOf(config.fallbackEnabled) }
    var hotspotAutoEnable by remember { mutableStateOf(config.hotspotAutoEnable) }

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
                    text = if (ShizukuManager.isReady) "연결됨" else "연결 필요",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (ShizukuManager.isReady) {
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
                        viewModel.saveConfig { it.copy(intervalMinutes = intervalMinutes) }
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
                        viewModel.saveConfig { it.copy(minBatteryPercent = minBattery) }
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
                        viewModel.saveConfig { it.copy(minSignalDbm = minSignal) }
                    },
                    valueRange = -120f..-60f,
                    steps = 11
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
                            viewModel.saveConfig { it.copy(hotspotAutoEnable = value) }
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
                            viewModel.saveConfig { it.copy(fallbackEnabled = value) }
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
                        viewModel.saveConfig { it.copy(retryCount = retryCount) }
                    },
                    valueRange = 0f..5f,
                    steps = 4
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

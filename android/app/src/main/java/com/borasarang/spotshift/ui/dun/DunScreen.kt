package com.borasarang.spotshift.ui.dun

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.ui.home.HomeViewModel
import com.borasarang.spotshift.ui.theme.LocalSpotColors

/**
 * T-37 — DUN 탭. APN 선택 → DUN 추가/원복 (Shizuku 필요).
 */
@Composable
fun DunScreen(contentPadding: PaddingValues) {
    val vm: DunViewModel = viewModel()
    // T-37 — Shizuku 실행/설치 유도는 공유 HomeViewModel 경유
    val homeVm: HomeViewModel = viewModel()
    val apns by vm.apns.collectAsState()
    val shizukuReady by vm.shizukuReady.collectAsState()
    val dunMaintain by vm.dunMaintain.collectAsState()

    LaunchedEffect(Unit) {
        DebugLogger.feature("DunScreen", "표시됨")
        vm.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("DUN", style = MaterialTheme.typography.headlineMedium)
        Text(
            "선택한 APN의 타입에 dun을 추가해 테더링 차단을 우회합니다. 적용 후 모바일 데이터를 한 번 껐다 켜주세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!shizukuReady) {
            Text(
                "Shizuku 연결이 필요합니다. (누르면 실행/설치 안내)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable { homeVm.openShizuku() }
            )
        }

        // T-39 — DUN 유지 (Shizuku 연결 시 자동 재적용)
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("DUN 유지", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "재부팅·통신사 리셋으로 dun이 사라지면 Shizuku 연결 시 자동으로 다시 적용합니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = dunMaintain,
                    onCheckedChange = { vm.setDunMaintain(it) }
                )
            }
        }

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("APN 목록", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(
                        onClick = { vm.refresh() },
                        enabled = shizukuReady && !vm.busy
                    ) {
                        Text("새로고침")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (apns.isEmpty()) {
                    Text(
                        if (vm.busy) "조회 중…" else "APN이 없습니다. 새로고침을 눌러주세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                apns.forEach { apn ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !vm.busy) { vm.select(apn.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = vm.selectedId == apn.id,
                            onClick = { vm.select(apn.id) },
                            enabled = !vm.busy
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${apn.name} (${apn.apn.ifBlank { "-" }})" +
                                    if (apn.hasDun) " · DUN" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (apn.hasDun) {
                                    LocalSpotColors.current.success
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                "type=${apn.type.ifBlank { "-" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { vm.applyDun() },
                enabled = shizukuReady && !vm.busy && vm.selectedId != null,
                modifier = Modifier.weight(1f)
            ) {
                Text("DUN 추가")
            }
            OutlinedButton(
                onClick = { vm.restoreDun() },
                enabled = shizukuReady && !vm.busy && vm.selectedId != null,
                modifier = Modifier.weight(1f)
            ) {
                Text("원복")
            }
        }

        vm.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(
                    enabled = it.startsWith("Shizuku")
                ) { homeVm.openShizuku() }
            )
        }
    }
}

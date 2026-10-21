package com.borasarang.spotshift.ui.dun

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.core.ApnEntry
import com.borasarang.spotshift.ui.home.HomeViewModel
import com.borasarang.spotshift.ui.theme.LocalSpotColors
import com.borasarang.spotshift.ui.theme.full

/**
 * T-50 — DUN 탭. 목업 `?screen=dun` 기준:
 * 권한 배너 → DUN 유지 토글 → APN 목록(선택·DUN 배지) → 액션 → 원복 안내.
 * APN 선택/추가/원복 로직(`DunViewModel`)은 불변, 표시 계층만 재구성했다.
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
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("DUN", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "테더링 APN 관리",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalIconButton(
                onClick = { vm.refresh() },
                enabled = shizukuReady && !vm.busy
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "APN 새로고침",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (shizukuReady) {
            DunBanner(
                icon = Icons.Outlined.CheckCircle,
                text = "Shizuku 연결됨",
                container = LocalSpotColors.current.successContainer,
                content = LocalSpotColors.current.onSuccessContainer
            )
        } else {
            DunBanner(
                icon = Icons.Outlined.ErrorOutline,
                text = "Shizuku 연결이 필요합니다 · 눌러서 설치/실행",
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                onClick = { homeVm.openShizuku() }
            )
        }

        // T-39 — DUN 유지 (Shizuku 연결 시 자동 재적용)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("DUN 유지", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Shizuku 연결 시 dun 타입을 자동으로 다시 적용합니다.",
                        style = MaterialTheme.typography.labelMedium,
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
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "APN 목록",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (apns.isEmpty()) "—" else "${apns.size}개",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (vm.busy && apns.isEmpty()) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else if (apns.isEmpty()) {
                    Text(
                        text = "APN이 없습니다. 새로고침을 눌러주세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                apns.forEach { apn ->
                    ApnRow(
                        apn = apn,
                        selected = vm.selectedId == apn.id,
                        enabled = !vm.busy,
                        onSelect = { vm.select(apn.id) }
                    )
                }
                if (vm.busy && apns.isNotEmpty()) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { vm.applyDun() },
                enabled = shizukuReady && !vm.busy && vm.selectedId != null,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = MaterialTheme.shapes.full
            ) {
                Text("DUN 추가")
            }
            OutlinedButton(
                onClick = { vm.restoreDun() },
                enabled = shizukuReady && !vm.busy && vm.selectedId != null,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = MaterialTheme.shapes.full
            ) {
                Text("원복")
            }
        }

        vm.message?.let { message ->
            val isError = message.contains("실패") || message.contains("필요") || message.contains("못")
            DunBanner(
                icon = if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.Info,
                text = message,
                container = if (isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    LocalSpotColors.current.successContainer
                },
                content = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    LocalSpotColors.current.onSuccessContainer
                },
                onClick = if (message.startsWith("Shizuku")) {
                    { homeVm.openShizuku() }
                } else {
                    null
                }
            )
        }

        DunBanner(
            icon = Icons.Outlined.Info,
            text = "원본 type은 저장되며 언제든 원복할 수 있습니다. 변경 후 재조회로 검증합니다.",
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ApnRow(
    apn: ApnEntry,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) Icons.Outlined.Check else Icons.Outlined.Router,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${apn.name} (${apn.apn.ifBlank { "-" }})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "type=${apn.type.ifBlank { "-" }}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (apn.hasDun) {
            Surface(
                shape = MaterialTheme.shapes.full,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "DUN 포함",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun DunBanner(
    icon: ImageVector,
    text: String,
    container: Color,
    content: Color,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = MaterialTheme.shapes.large,
        color = container
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

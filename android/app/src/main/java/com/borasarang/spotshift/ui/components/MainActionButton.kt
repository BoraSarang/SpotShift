package com.borasarang.spotshift.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.borasarang.spotshift.ui.theme.full

/**
 * 대형 시작 버튼 (일회성 수동 IP 변경 전용).
 *
 * T-45 — 더 이상 "정지"(자동 변경 OFF)를 겸하지 않는다. 회전 중에는 비활성만 한다:
 * DataController.toggleCycle이 disable→enable 사이에서 취소되면 모바일 데이터가
 * 꺼진 채 남을 수 있어 중간 취소를 제공하지 않는다. 자동 변경 On/Off는
 * 설정 탭 토글과 퀵세팅 타일이 담당한다.
 *
 * T-48 — 목업 기준: 56dp filled + full(pill) + 번개 아이콘.
 */
@Composable
fun MainActionButton(
    enabled: Boolean,
    running: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canPress = !running
    val label = when {
        !enabled -> "Shizuku 권한 필요"
        running -> "IP 변경 중…"
        else -> "IP 변경 시작"
    }
    Button(
        // v0.4 — 미승인 상태에서도 클릭 가능 (권한 요청 동작). T-45 — 회전 중에만 비활성.
        onClick = onClick,
        enabled = canPress,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = MaterialTheme.shapes.full,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (canPress) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(text = label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

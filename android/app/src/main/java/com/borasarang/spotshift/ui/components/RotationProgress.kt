package com.borasarang.spotshift.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.borasarang.spotshift.data.RotationPhase

/**
 * v0.3 — IP 변경 진행 단계 표시.
 * 실행 중(Running)일 때 인디터미네이트 프로그레스 바 + 단계 텍스트를 보여준다.
 */
@Composable
fun RotationProgress(
    phase: RotationPhase,
    attempt: Int = 0,
    modifier: Modifier = Modifier
) {
    val label = when (phase) {
        RotationPhase.CHECKING_IP -> "현재 IP 확인 중"
        RotationPhase.ROTATING_DATA -> "모바일 데이터 재연결 중"
        RotationPhase.VERIFYING -> "IP 변경 확인 중"
        RotationPhase.RETRYING -> "재시도 중 ($attempt/3)"
        RotationPhase.FALLBACK_AIRPLANE -> "에어플레인 모드 전환"
        else -> null
    } ?: return

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
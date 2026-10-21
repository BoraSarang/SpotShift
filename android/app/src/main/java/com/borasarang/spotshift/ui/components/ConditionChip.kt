package com.borasarang.spotshift.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.borasarang.spotshift.ui.theme.LocalSpotColors
import com.borasarang.spotshift.ui.theme.full

/**
 * 스케줄 조건 상태 칩.
 *
 * T-47 — 충족 상태는 중립(tonal)으로 두고, 미충족만 경고색으로 강조한다.
 * (전부 초록이면 "조건 3개 = 성공 3개"처럼 읽혀 상태 의미가 흐려짐 — 목업 v2와 일치)
 */
@Composable
fun ConditionChip(
    label: String,
    satisfied: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val container = if (satisfied) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        LocalSpotColors.current.warningContainer
    }
    val content = if (satisfied) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        LocalSpotColors.current.onWarningContainer
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.full,
        color = container,
        border = if (satisfied) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = content
            )
        }
    }
}

@Composable
fun ConditionRow(
    chips: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { chip ->
            chip()
        }
    }
}

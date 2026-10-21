package com.borasarang.spotshift.ui.util

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * T-51 — 시스템 "애니메이션 제거"(접근성/개발자 옵션) 대응.
 *
 * `Settings.Global.ANIMATOR_DURATION_SCALE`이 0이면 전환·게이지 애니메이션을 끄고
 * 값만 갱신한다 (docs/design/REDESIGN_v2.md §8).
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f) > 0f
    }
}

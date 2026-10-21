package com.borasarang.spotshift.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.borasarang.spotshift.R
import com.borasarang.spotshift.ui.dun.DunScreen
import com.borasarang.spotshift.ui.history.HistoryScreen
import com.borasarang.spotshift.ui.settings.SettingsScreen
import com.borasarang.spotshift.ui.speed.SpeedScreen
import com.borasarang.spotshift.ui.util.rememberAnimationsEnabled

/**
 * T-51 — 앱 루트 내비게이션.
 *
 * - 폭 600dp 미만: 하단 `NavigationBar` (A90)
 * - 폭 600dp 이상: 좌측 `NavigationRail` + 콘텐츠 (태블릿/가로/DeX)
 * - 탭 전환은 짧은 fade+slide, 시스템 "애니메이션 제거"면 즉시 전환
 * - `contentWindowInsets = safeDrawing`으로 컷아웃·시스템 바 회피
 */
@Composable
fun SpotShiftRoot() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_status) to Icons.Outlined.WifiTethering,
        stringResource(R.string.tab_history) to Icons.Outlined.History,
        stringResource(R.string.tab_speed) to Icons.Outlined.Speed,
        // T-37 — DUN 탭 (APN 선택 + dun 추가/원복)
        stringResource(R.string.tab_dun) to Icons.Outlined.Router,
        stringResource(R.string.tab_settings) to Icons.Outlined.Settings
    )
    val animationsEnabled = rememberAnimationsEnabled()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        tabs.forEachIndexed { index, (label, icon) ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                // 라벨이 있으므로 아이콘 설명은 생략 (중복 낭독 방지)
                                icon = { Icon(icon, contentDescription = null) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (useRail) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        RailSpacer()
                        tabs.forEachIndexed { index, (label, icon) ->
                            NavigationRailItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = { Icon(icon, contentDescription = null) },
                                label = { Text(label) }
                            )
                        }
                        RailSpacer()
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        TabContent(
                            selectedTab = selectedTab,
                            contentPadding = PaddingValues(0.dp),
                            animationsEnabled = animationsEnabled
                        )
                    }
                }
            } else {
                TabContent(
                    selectedTab = selectedTab,
                    contentPadding = innerPadding,
                    animationsEnabled = animationsEnabled
                )
            }
        }
    }
}

/** 레일 항목을 세로 중앙에 배치하기 위한 균형 스페이서. */
@Composable
private fun ColumnScope.RailSpacer() {
    Spacer(Modifier.weight(1f))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TabContent(
    selectedTab: Int,
    contentPadding: PaddingValues,
    animationsEnabled: Boolean
) {
    // transitionSpec 람다는 컴포지션이 아니므로 여기서 읽어 캡처한다
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = selectedTab,
        transitionSpec = {
            if (!animationsEnabled) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val direction = if (targetState > initialState) 1 else -1
                // T-54 — MotionScheme 토큰 사용 (expressive/standard는 테마에서 주입)
                (fadeIn(animationSpec = motion.fastEffectsSpec<Float>()) +
                    slideInHorizontally(
                        animationSpec = motion.fastSpatialSpec<IntOffset>(),
                        initialOffsetX = { it / 12 * direction }
                    )) togetherWith fadeOut(animationSpec = motion.fastEffectsSpec<Float>())
            }
        },
        label = "tab"
    ) { tab ->
        when (tab) {
            0 -> HomeScreen(contentPadding = contentPadding)
            1 -> HistoryScreen(contentPadding = contentPadding)
            2 -> SpeedScreen(contentPadding = contentPadding)
            3 -> DunScreen(contentPadding = contentPadding)
            else -> SettingsScreen(contentPadding = contentPadding)
        }
    }
}

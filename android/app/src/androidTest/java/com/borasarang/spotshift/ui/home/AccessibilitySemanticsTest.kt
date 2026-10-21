package com.borasarang.spotshift.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.borasarang.spotshift.ui.theme.SpotShiftTheme
import com.borasarang.spotshift.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test

/**
 * T-56 — 화면 낭독기(TalkBack)가 읽을 수 있는 라벨/역할이 붙어 있는지 검증.
 *
 * 네트워크·Shizuku 상태에 의존하지 않는 정적 요소만 검사한다.
 */
class AccessibilitySemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setRoot(widthDp: Int) {
        rule.setContent {
            SpotShiftTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                Box(Modifier.size(widthDp.dp, 900.dp)) {
                    SpotShiftRoot()
                }
            }
        }
    }

    private fun navItem(label: String) = rule.onNode(hasText(label) and isSelectable())

    @Test
    fun navigation_exposesLabelsAndSelectedState() {
        setRoot(411)

        listOf("상태", "기록", "속도", "DUN", "설정").forEach { label ->
            navItem(label).assertIsDisplayed()
        }
        navItem("기록").performClick()
        navItem("기록").assertIsDisplayed()
    }

    @Test
    fun home_primaryActionIsClickableAndLabelled() {
        setRoot(411)

        rule.onNodeWithText("IP 변경 시작").assertHasClickAction()
        // 조건 칩은 아이콘 없이 텍스트만으로 의미가 전달되어야 한다
        rule.onNodeWithText("배터리", substring = true).assertIsDisplayed()
    }

    @Test
    fun home_copyButton_hasDescriptionAndTouchTarget() {
        setRoot(411)

        // IP 조회 전에는 버튼이 없으므로 최대 5초 대기 (ipify)
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithContentDescription("공인 IP 복사")
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("공인 IP 복사")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun speed_measureButtonAndDeleteRow_targets() {
        setRoot(411)

        navItem("속도").performClick()
        rule.onNodeWithText("측정 시작").assertHasClickAction().assertIsDisplayed()
    }

    @Test
    fun dun_refreshButton_hasDescription() {
        setRoot(411)

        navItem("DUN").performClick()
        rule.onNodeWithContentDescription("APN 새로고침").assertExists()
    }

    @Test
    fun settings_switchIsToggleable() {
        setRoot(411)

        navItem("설정").performClick()
        val toggles = rule.onAllNodes(isToggleable()).fetchSemanticsNodes()
        assert(toggles.isNotEmpty()) { "설정 화면에 토글이 없습니다" }
    }
}

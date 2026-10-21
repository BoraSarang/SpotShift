package com.borasarang.spotshift.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.borasarang.spotshift.ui.theme.SpotShiftTheme
import com.borasarang.spotshift.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test

/**
 * T-55 — 내비게이션 분기/탭 전환 계측 검증.
 *
 * 폭을 강제로 바꿔 `SpotShiftRoot`의 600dp 분기를 그대로 태운다.
 * 실기기(A90)에서 `connectedDebugAndroidTest`로 실행한다.
 */
class RailNavigationTest {

    @get:Rule
    val rule = createComposeRule()

    /** 화면 제목과 같은 문자열이 있어도 내비 항목(selectable)만 집는다. */
    private fun navItem(label: String) =
        rule.onNode(hasText(label) and isSelectable())

    private fun setRoot(widthDp: Int) {
        rule.setContent {
            SpotShiftTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                Box(Modifier.size(widthDp.dp, 900.dp)) {
                    SpotShiftRoot()
                }
            }
        }
    }

    @Test
    fun largeScreen_rail_selectsEachTab() {
        setRoot(800)

        navItem("속도").performClick()
        navItem("속도").assertIsSelected()
        rule.onNodeWithText("측정 시작").assertIsDisplayed()

        navItem("DUN").performClick()
        navItem("DUN").assertIsSelected()
        rule.onNodeWithText("APN 목록").assertIsDisplayed()

        navItem("설정").performClick()
        navItem("설정").assertIsSelected()
        rule.onNodeWithText("자동 IP 변경").assertIsDisplayed()
    }

    @Test
    fun compact_bottomBar_selectsEachTab() {
        setRoot(411)

        navItem("기록").performClick()
        navItem("기록").assertIsSelected()

        navItem("속도").performClick()
        navItem("속도").assertIsSelected()
        rule.onNodeWithText("측정 시작").assertIsDisplayed()
    }
}

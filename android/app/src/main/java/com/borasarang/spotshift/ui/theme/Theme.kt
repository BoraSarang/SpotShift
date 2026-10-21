package com.borasarang.spotshift.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.borasarang.spotshift.DebugLogger

/**
 * T-47 — 테마 모드. 설정에서 시스템/라이트/다크를 고정할 수 있다.
 * 저장은 Prefs의 `theme_mode` 키에 enum 이름 문자열로 보관한다.
 */
enum class ThemeMode(val label: String) {
    SYSTEM("시스템"),
    LIGHT("라이트"),
    DARK("다크");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.name == key } ?: SYSTEM
    }
}

/**
 * @param themeMode 시스템/라이트/다크 (기본: 시스템)
 * @param dynamicColor Android 12+ 배경화면 동적 색상 사용 여부 (기본: 사용)
 */
@Composable
fun SpotShiftTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val canUseDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val usesDynamic = dynamicColor && canUseDynamic
    val colorScheme = when {
        usesDynamic ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    val spotColors = if (darkTheme) DarkSpotColors else LightSpotColors

    // T-47 — 테마 적용 결과를 1회만 기록 (검증용: TC-19~21)
    LaunchedEffect(darkTheme, usesDynamic) {
        DebugLogger.feature(
            "Theme",
            "테마 적용 dark=$darkTheme dynamic=$usesDynamic " +
                "scheme=${if (usesDynamic) "dynamic" else "static"} " +
                "primary=${hex(colorScheme.primary)} container=${hex(colorScheme.secondaryContainer)}"
        )
    }

    // 상태바/내비게이션바 아이콘 밝기 — 사용자가 테마를 고정해도 시스템 기본값과 어긋나지 않게 맞춘다.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalSpotColors provides spotColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

private fun hex(color: Color): String = String.format("#%06X", color.toArgb() and 0xFFFFFF)

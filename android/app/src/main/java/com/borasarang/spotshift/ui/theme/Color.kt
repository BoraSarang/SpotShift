package com.borasarang.spotshift.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * T-47 — 디자인 토큰 v2 (docs/design/REDESIGN_v2.md §3).
 *
 * 원칙:
 * - 화면/컴포넌트는 반드시 시맨틱 role만 참조한다 (`Color(0x...)` 직접 참조 금지).
 * - 성공/경고는 Material3 기본 role에 없으므로 [SpotColors]로 분리한다
 *   (성공 상태를 error/secondary로 표현하지 않는다).
 * - 다크는 순흑 대신 surface #0F1318을 쓴다 (OLED 번인·대비 균형).
 */

// 시드: #0E7BC4 (스카이/네이비 정체성 유지) — 정적 폴백 팔레트
private val LightPrimary = Color(0xFF00639B)
private val LightPrimaryContainer = Color(0xFFCDE5FF)
private val LightOnPrimaryContainer = Color(0xFF001D33)
private val LightSecondary = Color(0xFF4F616E)
private val LightSecondaryContainer = Color(0xFFD2E5F4)
private val LightOnSecondaryContainer = Color(0xFF0B1D29)
private val LightTertiary = Color(0xFF7A5900)
private val LightTertiaryContainer = Color(0xFFFFDF9C)
private val LightOnTertiaryContainer = Color(0xFF261A00)

private val DarkPrimary = Color(0xFF93CCFF)
private val DarkOnPrimary = Color(0xFF003354)
private val DarkPrimaryContainer = Color(0xFF00497A)
private val DarkOnPrimaryContainer = Color(0xFFCDE5FF)
private val DarkSecondary = Color(0xFFB6C9D8)
private val DarkOnSecondary = Color(0xFF21323E)
private val DarkSecondaryContainer = Color(0xFF374956)
private val DarkOnSecondaryContainer = Color(0xFFD2E5F4)
private val DarkTertiary = Color(0xFFF5BF48)
private val DarkOnTertiary = Color(0xFF402D00)
private val DarkTertiaryContainer = Color(0xFF5C4200)
private val DarkOnTertiaryContainer = Color(0xFFFFDF9C)

val LightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = Color(0xFF8FCDFF),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = Color.White,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = Color(0xFFF7F9FF),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFF7F9FF),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFDFE2E8),
    onSurfaceVariant = Color(0xFF41474E),
    surfaceTint = LightPrimary,
    inverseSurface = Color(0xFF2D3135),
    inverseOnSurface = Color(0xFFEEF1F6),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF71787F),
    outlineVariant = Color(0xFFC1C7CE),
    scrim = Color.Black,
    surfaceBright = Color(0xFFF7F9FF),
    surfaceDim = Color(0xFFD7DADF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4FA),
    surfaceContainer = Color(0xFFEBEEF4),
    surfaceContainerHigh = Color(0xFFE5E8EE),
    surfaceContainerHighest = Color(0xFFDFE2E8)
)

val DarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = LightPrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = Color(0xFF0F1318),
    onBackground = Color(0xFFDFE3E8),
    surface = Color(0xFF0F1318),
    onSurface = Color(0xFFDFE3E8),
    surfaceVariant = Color(0xFF4F545A),
    onSurfaceVariant = Color(0xFFBFC6CE),
    surfaceTint = DarkPrimary,
    inverseSurface = Color(0xFFDFE3E8),
    inverseOnSurface = Color(0xFF2B3036),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF899098),
    outlineVariant = Color(0xFF3F464D),
    scrim = Color.Black,
    surfaceBright = Color(0xFF35393F),
    surfaceDim = Color(0xFF0F1318),
    surfaceContainerLowest = Color(0xFF0A0E12),
    surfaceContainerLow = Color(0xFF171C21),
    surfaceContainer = Color(0xFF1B2025),
    surfaceContainerHigh = Color(0xFF252A30),
    surfaceContainerHighest = Color(0xFF30353B)
)

/**
 * Material3에 없는 상태 role. success = IP 변경/기준 달성, warning = 연기/주의.
 * 동적 색상(dynamic color)이 켜져도 성공/경고 의미는 고정한다.
 */
@Immutable
data class SpotColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color
)

val LightSpotColors = SpotColors(
    success = Color(0xFF146C2E),
    onSuccess = Color.White,
    successContainer = Color(0xFFA8F5BD),
    onSuccessContainer = Color(0xFF00210B),
    warning = Color(0xFF8A5100),
    onWarning = Color.White,
    warningContainer = Color(0xFFFFDDB3),
    onWarningContainer = Color(0xFF2C1600)
)

val DarkSpotColors = SpotColors(
    success = Color(0xFF8BD99F),
    onSuccess = Color(0xFF00391A),
    successContainer = Color(0xFF00531F),
    onSuccessContainer = Color(0xFFA8F5BD),
    warning = Color(0xFFFFB95C),
    onWarning = Color(0xFF402D00),
    warningContainer = Color(0xFF653D00),
    onWarningContainer = Color(0xFFFFDDB3)
)

val LocalSpotColors = staticCompositionLocalOf { LightSpotColors }

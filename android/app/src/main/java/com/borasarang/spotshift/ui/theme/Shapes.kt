package com.borasarang.spotshift.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * T-47 — shape 토큰 (MD3 / Expressive 여유값).
 * 카드 히어로 28dp, 일반 컨테이너 20dp, 버튼·칩은 full.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * T-48 — MD3 `full` 형태(버튼·칩·배지·pill). `Shapes` 기본 슬롯에 없어 확장으로 제공한다.
 * 컴포넌트에서 `RoundedCornerShape(50)` 같은 하드코딩을 제거하기 위한 단일 출처.
 */
val Shapes.full: Shape
    get() = RoundedCornerShape(percent = 50)

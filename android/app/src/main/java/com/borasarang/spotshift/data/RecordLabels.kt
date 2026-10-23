package com.borasarang.spotshift.data

/**
 * T-60 — 기록 한 줄에 쓰는 한국어 라벨.
 *
 * 홈(히어로·최근 변경 카드)과 기록 탭이 같은 문구를 쓰도록 한 곳에 모은다.
 * (기존에는 `methodLabel`이 최근 변경 카드와 기록 화면에 중복 정의돼 있었다.)
 */
object RecordLabels {

    /** 변경 수단 — 데이터 재연결 / 에어플레인 / 속도 측정 … */
    fun method(method: String?): String? = when (method) {
        RotationRecord.METHOD_DATA_RECONNECT -> "데이터 재연결"
        RotationRecord.METHOD_AIRPLANE -> "에어플레인"
        RotationRecord.METHOD_HOTSPOT_RESTART -> "핫스팟 재시작"
        RotationRecord.METHOD_SPEED_CHECK -> "속도 측정"
        else -> null
    }

    /**
     * 변경 출처 — 수동/자동/감시/외부 요청. 외부 요청은 요청자를 덧붙인다.
     * T-60 이전 기록(source == null)은 출처를 알 수 없으므로 null.
     */
    fun source(source: String?, detail: String? = null): String? = when (source) {
        RotationRecord.SOURCE_MANUAL -> "수동 변경"
        RotationRecord.SOURCE_SPEED_TAB -> "속도 탭 변경"
        RotationRecord.SOURCE_SCHEDULED -> "자동 변경"
        RotationRecord.SOURCE_WATCHDOG -> "속도 감시 자동"
        RotationRecord.SOURCE_REMOTE ->
            if (detail.isNullOrBlank()) "외부 요청" else "외부 요청 · $detail"
        else -> null
    }

    /** 출처 + 수단 한 줄 (예: `자동 변경 · 데이터 재연결`). 둘 다 없으면 null. */
    fun changeLine(source: String?, detail: String?, method: String?): String? =
        listOfNotNull(source(source, detail), method(method))
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
}

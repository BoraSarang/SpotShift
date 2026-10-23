package com.borasarang.spotshift.core

/**
 * T-52 — IP 변경 단일 실행 게이트.
 *
 * 배경: 원격 요청(`PluginActionReceiver`)은 브로드캐스트마다 새 코루틴 + 새 [RotationEngine]을
 * 만들었고 Mutex는 엔진 인스턴스 필드라 요청 간 직렬화가 없었다. 그래서 요청이 겹치면
 * `svc data disable/enable`이 인터리브되어 IP 검증 실패 오기록·중복 기록·데이터 OFF 잔존
 * 위험이 있었다.
 *
 * 정책 (2026-10-21 사용자 확정):
 * - 진행 중이면 **새 요청은 즉시 거부**하고, **진행 중인 작업은 그대로 계속**한다.
 * - 대기 큐·코얼레싱 없음 (거부가 예측 가능하고 지연이 없다).
 *
 * 적용 경로: 수동(홈) / 속도 탭 / 주기 워커 / 감시 워커 / 원격(플러그인).
 * 회전 코어(`RotationEngine`/`DataController`)는 불변 — 진입 게이트만 추가한다.
 */
object RotationGate {

    /** 요청 출처. 거부 응답에 그대로 노출된다. */
    enum class Source(val label: String, val ko: String) {
        MANUAL("manual", "수동 변경"),
        SPEED_TAB("speed", "속도 탭 변경"),
        SCHEDULED("scheduled", "자동 변경"),
        WATCHDOG("watchdog", "속도 감시 자동"),
        REMOTE("remote", "외부 요청")
    }

    private data class Lease(val source: Source, val startedAt: Long)

    private val lock = Any()
    private var lease: Lease? = null

    /**
     * 리스 만료 시간. 실행이 비정상 종료되어 [exit]가 누락돼도 게이트가 영구히 잠기지 않게 한다.
     * (정상 회전은 최대 2~3분 — 재시도 3회 기준)
     */
    private const val STALE_LEASE_MILLIS = 10 * 60 * 1000L

    /**
     * 실행권 획득. 이미 진행 중이면 false를 반환하고 아무것도 바꾸지 않는다.
     * 성공 시 반드시 [exit]를 finally에서 호출해야 한다.
     */
    fun tryEnter(source: Source): Boolean = synchronized(lock) {
        clearIfStale()
        if (lease != null) return false
        lease = Lease(source, System.currentTimeMillis())
        true
    }

    fun exit() = synchronized(lock) {
        lease = null
    }

    fun isBusy(): Boolean = synchronized(lock) {
        clearIfStale()
        lease != null
    }

    fun currentSource(): Source? = synchronized(lock) { lease?.source }

    fun elapsedSeconds(): Long = synchronized(lock) {
        val current = lease ?: return 0
        ((System.currentTimeMillis() - current.startedAt) / 1000L).coerceAtLeast(0)
    }

    /** 호출자가 lock을 이미 잡은 상태에서만 사용한다. */
    private fun clearIfStale() {
        val current = lease ?: return
        if (System.currentTimeMillis() - current.startedAt > STALE_LEASE_MILLIS) {
            lease = null
        }
    }

    /** 거부 사유 한 줄 — 로그/계약 응답에 사용 (예: `busy (source=remote elapsed=3s)`). */
    fun describeBusy(): String {
        val source = currentSource()?.label ?: "unknown"
        return "busy (source=$source elapsed=${elapsedSeconds()}s)"
    }
}

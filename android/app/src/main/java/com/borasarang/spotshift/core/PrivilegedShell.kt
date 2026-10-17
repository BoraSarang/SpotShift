package com.borasarang.spotshift.core

/**
 * T-44 — 특권 셸 실행 추상화.
 * 현재 구현은 Shizuku. 루팅 전환 시 구현체만 교체하면 호출부는 그대로 둔다.
 * 신규 코드는 [ShizukuManager] 직접 참조 대신 이 인터페이스를 경유한다.
 */
interface PrivilegedShell {
    val isReady: Boolean
    suspend fun runShell(vararg args: String): ShizukuManager.ShellResult
}

/**
 * Shizuku 기반 구현 (기본값).
 */
object ShizukuShell : PrivilegedShell {
    override val isReady: Boolean
        get() = ShizukuManager.isReady

    override suspend fun runShell(vararg args: String): ShizukuManager.ShellResult =
        ShizukuManager.runShell(*args)
}

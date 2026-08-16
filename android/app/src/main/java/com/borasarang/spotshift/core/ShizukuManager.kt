package com.borasarang.spotshift.core

import android.content.pm.PackageManager
import com.borasarang.spotshift.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

object ShizukuManager {

    private const val REQUEST_CODE = 10001
    private const val SHELL_TIMEOUT_MILLIS = 20_000L

    val isShizukuAvailable: Boolean
        get() = Shizuku.pingBinder()

    val isPermissionGranted: Boolean
        get() = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    val isReady: Boolean
        get() = isShizukuAvailable && isPermissionGranted

    fun requestPermission(): Boolean {
        if (!isShizukuAvailable) return false
        if (isPermissionGranted) return true
        try {
            Shizuku.requestPermission(REQUEST_CODE)
            return true
        } catch (e: Exception) {
            DebugLogger.e("Shizuku 권한 요청 실패", "E-AND-PERM-0001", e)
            return false
        }
    }

    /**
     * Shizuku 권한으로 셸 명령 실행 후 표준 출력을 반환한다.
     * Shizuku 미연결/권한 없음 시 에러코드를 담은 결과를 반환한다.
     */
    suspend fun runShell(vararg args: String): ShellResult = withContext(Dispatchers.IO) {
        if (!isReady) {
            val code = if (!isShizukuAvailable) "E-AND-PERM-0002" else "E-AND-PERM-0001"
            DebugLogger.e("Shizuku 준비 안 됨", code)
            return@withContext ShellResult(errorCode = code, exitCode = -1)
        }
        try {
            val start = System.currentTimeMillis()
            val process = createRemoteProcess(Array(args.size) { args[it] })
                ?: return@withContext ShellResult(errorCode = "E-AND-PERM-0001", exitCode = -1)
            val output = withTimeout(SHELL_TIMEOUT_MILLIS) {
                process.inputStream.bufferedReader().use { it.readText() }
            }
            val exitCode = process.waitFor()
            DebugLogger.perf("shizuku_run ${args.firstOrNull()}", System.currentTimeMillis() - start)
            DebugLogger.d("Shizuku cmd=${args.joinToString(" ")} exit=$exitCode out=${output.take(200)}")
            ShellResult(output = output, exitCode = exitCode)
        } catch (e: Exception) {
            DebugLogger.e("Shizuku 명령 실행 실패: ${args.joinToString(" ")}", "E-AND-PERM-0001", e)
            ShellResult(errorCode = "E-AND-PERM-0001", exitCode = -1)
        }
    }

    /**
     * Shizuku API 13.1.x에서 newProcess가 private로 변경되어 리플렉션으로 호출한다.
     * (UserService 전환 전 임시 — 커뮤니티 표준 방식)
     */
    private fun createRemoteProcess(args: Array<String>): ShizukuRemoteProcess? {
        return try {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val method = clazz.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            method.invoke(null, args, null, null) as ShizukuRemoteProcess
        } catch (e: Exception) {
            DebugLogger.e("Shizuku.newProcess 리플렉션 실패", "E-AND-PERM-0001", e)
            null
        }
    }

    data class ShellResult(
        val output: String? = null,
        val errorCode: String? = null,
        val exitCode: Int = -1
    ) {
        val isSuccess: Boolean get() = errorCode == null && exitCode == 0
    }
}

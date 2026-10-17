package com.borasarang.spotshift.core

import com.borasarang.spotshift.DebugLogger

/**
 * T-37 — 테더링 APN(DUN) 컨트롤러.
 * Shizuku 셸(`content` 명령)로 `content://telephony/carriers`를 조회·수정한다.
 * 통신사 APN을 직접 고치는 방식이라 DunUnlocker처럼 복사본을 만들지 않고,
 * 원본 type을 Prefs에 저장해 원복한다. (A90 실측: update+재조회 검증 성공)
 */
data class ApnEntry(
    val id: Long,
    val name: String,
    val apn: String,
    val type: String
) {
    val hasDun: Boolean
        get() = type.split(",").any { it.trim().equals("dun", ignoreCase = true) }
}

class ApnController(
    private val shell: PrivilegedShell = ShizukuShell
) {

    /**
     * 현재 사용 중인 APN(current=1) 목록을 반환한다.
     */
    suspend fun listCurrent(): Result<List<ApnEntry>> {
        val result = shell.runShell(
            "content", "query",
            "--uri", CARRIERS_URI,
            "--projection", "_id:name:apn:type",
            "--where", "current=1"
        )
        if (!result.isSuccess) {
            val code = result.errorCode ?: "E-AND-NET-0005"
            DebugLogger.e("APN 목록 조회 실패", code)
            return Result.failure(IllegalStateException(code))
        }
        val entries = result.output.orEmpty().lineSequence()
            .mapNotNull { parseRow(it) }
            .toList()
        DebugLogger.feature("ApnController", "APN ${entries.size}건 조회")
        return Result.success(entries)
    }

    /**
     * 단일 APN의 현재 type을 반환한다.
     */
    suspend fun queryType(id: Long): Result<String> {
        val result = shell.runShell(
            "content", "query",
            "--uri", CARRIERS_URI,
            "--projection", "type",
            "--where", "_id=$id"
        )
        if (!result.isSuccess) {
            val code = result.errorCode ?: "E-AND-NET-0005"
            DebugLogger.e("APN type 조회 실패 id=$id", code)
            return Result.failure(IllegalStateException(code))
        }
        val type = result.output.orEmpty().lineSequence()
            .mapNotNull { TYPE_ROW.find(it)?.groupValues?.get(1) }
            .firstOrNull()
            ?.trim()
        if (type.isNullOrEmpty()) {
            DebugLogger.e("APN type 파싱 실패 id=$id", "E-AND-NET-0005")
            return Result.failure(IllegalStateException("E-AND-NET-0005"))
        }
        return Result.success(type)
    }

    /**
     * 지정 APN type에 dun을 추가한다. 성공 시 변경 후 type을 반환한다.
     * 이미 dun이 있으면 변경 없이 현재 type을 반환한다.
     */
    suspend fun addDun(id: Long): Result<String> {
        val current = queryType(id).getOrElse { return Result.failure(it) }
        if (ApnEntry(id, "", "", current).hasDun) {
            DebugLogger.feature("ApnController", "id=$id 이미 dun 포함 — 변경 없음")
            return Result.success(current)
        }
        val next = if (current.isBlank()) "dun" else "$current,dun"
        return updateType(id, next)
    }

    /**
     * 지정 APN type을 [originalType]으로 되돌린다.
     */
    suspend fun restoreType(id: Long, originalType: String): Result<String> {
        return updateType(id, originalType)
    }

    /**
     * 지정 APN type에서 dun 토큰만 제거한다 (원본 미보관 시 폴백).
     */
    suspend fun stripDun(id: Long): Result<String> {
        val current = queryType(id).getOrElse { return Result.failure(it) }
        val next = current.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("dun", ignoreCase = true) }
            .joinToString(",")
        return updateType(id, next)
    }

    private suspend fun updateType(id: Long, type: String): Result<String> {
        val update = shell.runShell(
            "content", "update",
            "--uri", CARRIERS_URI,
            "--bind", "type:s:$type",
            "--where", "_id=$id"
        )
        if (!update.isSuccess) {
            val code = update.errorCode ?: "E-AND-NET-0005"
            DebugLogger.e("APN type 변경 실패 id=$id", code)
            return Result.failure(IllegalStateException(code))
        }
        // 재조회 검증 (기기·통신사가 되돌렸는지 확인)
        val verified = queryType(id).getOrElse { return Result.failure(it) }
        if (!typesEqual(verified, type)) {
            DebugLogger.e("APN type 검증 불일치 id=$id 요청=$type 실제=$verified", "E-AND-NET-0005")
            return Result.failure(IllegalStateException("E-AND-NET-0005"))
        }
        DebugLogger.feature("ApnController", "id=$id type=$verified 적용됨")
        return Result.success(verified)
    }

    private fun typesEqual(a: String, b: String): Boolean {
        fun norm(s: String) = s.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        return norm(a) == norm(b)
    }

    private fun parseRow(line: String): ApnEntry? = runCatching {
        // Row: 0 _id=5744, name=KT, apn=internet, type=default,supl,rcs,dun
        val id = ID_ROW.find(line)?.groupValues?.get(1)?.toLong() ?: return null
        val nameStart = line.indexOf("name=") + "name=".length
        val apnSep = line.indexOf(", apn=")
        val typeSep = line.indexOf(", type=")
        if (nameStart < "name=".length || apnSep < 0 || typeSep < 0) return null
        val name = line.substring(nameStart, apnSep)
        val apn = line.substring(apnSep + ", apn=".length, typeSep)
        val type = line.substring(typeSep + ", type=".length).trim()
        ApnEntry(id, name, apn, type)
    }.getOrNull()

    companion object {
        private const val CARRIERS_URI = "content://telephony/carriers"
        private val ID_ROW = Regex("_id=(\\d+)")
        private val TYPE_ROW = Regex("type=(.*)$")
    }
}

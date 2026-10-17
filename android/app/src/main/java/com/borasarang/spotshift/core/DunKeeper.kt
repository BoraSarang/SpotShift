package com.borasarang.spotshift.core

import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.data.Prefs

/**
 * T-39 — DUN 유지자.
 * DUN을 적용했던 APN(원본 보관분)에 dun이 사라졌으면 다시 추가한다.
 * 재부팅·통신사 리셋으로 APN이 되돌아간 경우, Shizuku 연결 시 자동 복구용.
 */
class DunKeeper(
    private val prefs: Prefs,
    private val apn: ApnController = ApnController()
) {

    /**
     * 유지 토글 ON + 보관된 원본이 있을 때만 동작.
     * Shizuku 미준비면 조용히 false (연결 시 SpotShiftApp이 다시 호출).
     */
    suspend fun ensure(): Boolean {
        if (!prefs.getDunMaintain()) return true
        val originals = prefs.getDunOriginalTypes()
        if (originals.isEmpty()) return true
        if (!ShizukuManager.isReady) {
            DebugLogger.i("[DUN] Shizuku 미준비 — 연결 시 자동 재적용 예약")
            return false
        }
        var ok = true
        originals.forEach { (idStr, _) ->
            val id = idStr.toLongOrNull() ?: return@forEach
            val current = apn.queryType(id).getOrElse {
                ok = false
                return@forEach
            }
            if (!ApnEntry(id, "", "", current).hasDun) {
                apn.addDun(id)
                    .onSuccess {
                        DebugLogger.feature("DunKeeper", "DUN 자동 재적용 id=$id")
                    }
                    .onFailure {
                        ok = false
                        DebugLogger.e("DUN 자동 재적용 실패 id=$id", "E-AND-SCH-0004")
                    }
            }
        }
        return ok
    }
}

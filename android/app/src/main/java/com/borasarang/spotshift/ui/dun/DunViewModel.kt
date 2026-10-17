package com.borasarang.spotshift.ui.dun

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.core.ApnController
import com.borasarang.spotshift.core.ApnEntry
import com.borasarang.spotshift.core.ShizukuManager
import com.borasarang.spotshift.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * T-37 — DUN 탭 ViewModel.
 * APN 목록 조회 → 선택 → DUN 추가/원복. 원본 type은 Prefs에 보관한다.
 */
class DunViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val apnController = ApnController()

    val shizukuReady: StateFlow<Boolean> = ShizukuManager.ready

    private val _apns = MutableStateFlow<List<ApnEntry>>(emptyList())
    val apns: StateFlow<List<ApnEntry>> = _apns

    // T-39 — DUN 유지 토글
    val dunMaintain: StateFlow<Boolean> = prefs.dunMaintainFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setDunMaintain(v: Boolean) {
        viewModelScope.launch {
            prefs.updateDunMaintain(v)
            DebugLogger.feature("DunViewModel", "DUN 유지=$v")
        }
    }

    var selectedId: Long? by mutableStateOf(null)
        private set

    var busy: Boolean by mutableStateOf(false)
        private set

    var message: String? by mutableStateOf(null)
        private set

    fun refresh() {
        if (!ShizukuManager.isReady) {
            DebugLogger.e("Shizuku 미준비 — APN 조회 불가", "E-AND-PERM-0002")
            message = "Shizuku 연결이 필요합니다."
            return
        }
        viewModelScope.launch {
            busy = true
            message = null
            apnController.listCurrent()
                .onSuccess { list ->
                    _apns.value = list
                    // 선택 유지 (사라졌으면 선호 첫 항목)
                    if (list.none { it.id == selectedId }) {
                        selectedId = list.firstOrNull { it.hasDun }?.id ?: list.firstOrNull()?.id
                    }
                    DebugLogger.feature("DunViewModel", "APN ${list.size}건 표시")
                }
                .onFailure {
                    message = "APN 목록을 가져오지 못했습니다. (${it.message})"
                }
            busy = false
        }
    }

    fun select(id: Long) {
        selectedId = id
        message = null
    }

    fun applyDun() {
        val id = selectedId ?: return
        if (!ShizukuManager.isReady) {
            DebugLogger.e("Shizuku 미준비 — DUN 추가 불가", "E-AND-PERM-0002")
            message = "Shizuku 연결이 필요합니다."
            return
        }
        viewModelScope.launch {
            busy = true
            message = null
            val before = apnController.queryType(id).getOrElse {
                busy = false
                message = "현재 type을 읽지 못했습니다. (${it.message})"
                return@launch
            }
            if (!ApnEntry(id, "", "", before).hasDun) {
                prefs.saveDunOriginalType(id, before)
            }
            apnController.addDun(id)
                .onSuccess { type ->
                    message = "DUN 추가됨 (type=$type). 데이터 연결을 한 번 껐다 켜주세요."
                    DebugLogger.feature("DunViewModel", "DUN 추가 성공 id=$id")
                    refresh()
                }
                .onFailure {
                    message = "DUN 추가 실패. (${it.message})"
                }
            busy = false
        }
    }

    fun restoreDun() {
        val id = selectedId ?: return
        if (!ShizukuManager.isReady) {
            DebugLogger.e("Shizuku 미준비 — DUN 원복 불가", "E-AND-PERM-0002")
            message = "Shizuku 연결이 필요합니다."
            return
        }
        viewModelScope.launch {
            busy = true
            message = null
            val original = prefs.getDunOriginalTypes()[id.toString()]
            val result = if (original != null) {
                apnController.restoreType(id, original)
            } else {
                apnController.stripDun(id)
            }
            result
                .onSuccess { type ->
                    prefs.clearDunOriginalType(id)
                    message = "원복됨 (type=$type)."
                    DebugLogger.feature("DunViewModel", "DUN 원복 성공 id=$id")
                    refresh()
                }
                .onFailure {
                    message = "원복 실패. (${it.message})"
                }
            busy = false
        }
    }
}

package com.borasarang.spotshift.ui.home

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.core.AirplaneController
import com.borasarang.spotshift.core.DataController
import com.borasarang.spotshift.core.HotspotController
import com.borasarang.spotshift.core.IpVerifier
import com.borasarang.spotshift.core.RotationEngine
import com.borasarang.spotshift.core.ShizukuManager
import com.borasarang.spotshift.data.Prefs
import com.borasarang.spotshift.data.RotationConfig
import com.borasarang.spotshift.data.RotationRecord
import com.borasarang.spotshift.data.RotationState
import com.borasarang.spotshift.service.IpRotationService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    val config: StateFlow<RotationConfig> = prefs.configFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, RotationConfig())
    val records: StateFlow<List<RotationRecord>> = prefs.recordsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val engine = RotationEngine(
        context = app,
        dataController = DataController(),
        airplaneController = AirplaneController(app),
        hotspotController = HotspotController(app),
        ipVerifier = IpVerifier()
    )

    // Compose 상태 — 진행 단계/메시지가 UI에 즉시 반영되도록 mutableStateOf 사용
    var rotationState: RotationState by mutableStateOf(RotationState())
        private set

    init {
        engine.onStateChanged = { state ->
            rotationState = state
        }
        // v0.2 — 요구사항 5: 이벤트 알림은 RotationEngine이 발행 (중복 방지)
        engine.onHotspotOffDetected = {
            runCatching {
                // API 36에서 ACTION_TETHERING_SETTINGS 제거됨 → ACTION_WIRELESS_SETTINGS
                val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                getApplication<Application>().startActivity(intent)
            }
        }
        DebugLogger.feature("HomeViewModel", "생성")
    }

    fun isShizukuReady(): Boolean = ShizukuManager.isReady

    fun requestShizukuPermission(): Boolean = ShizukuManager.requestPermission()

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = config.value
            prefs.saveConfig(current.copy(enabled = enabled))
            // v0.2 — 요구사항 5: 자동 IP 변경 토글이 서비스(스케줄러) 라이프사이클을 제어
            val intent = Intent(getApplication(), IpRotationService::class.java)
            if (enabled) {
                intent.action = IpRotationService.ACTION_START
                ContextCompat.startForegroundService(getApplication(), intent)
            } else {
                getApplication<Application>().stopService(intent)
            }
            DebugLogger.feature("HomeViewModel", "setEnabled=$enabled")
        }
    }

    fun saveConfig(transform: (RotationConfig) -> RotationConfig) {
        viewModelScope.launch {
            prefs.saveConfig(transform(config.value))
        }
    }

    fun manualRotate(onResult: (RotationRecord) -> Unit = {}) {
        viewModelScope.launch {
            if (!ShizukuManager.isReady) {
                DebugLogger.e("Shizuku 미준비 — 수동 실행 불가", "E-AND-PERM-0002")
                return@launch
            }
            val record = engine.rotate(config.value)
            prefs.addRecord(record)
            if (record.changed && record.newIp != null) {
                prefs.updateRotationMeta(System.currentTimeMillis(), record.newIp)
            }
            onResult(record)
        }
    }

    /**
     * v0.2 — 요구사항 3: 기록 전체 초기화.
     */
    fun clearRecords() {
        viewModelScope.launch {
            prefs.clearRecords()
        }
    }
}

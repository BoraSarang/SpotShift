package com.borasarang.spotshift.ui.home

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.core.AirplaneController
import com.borasarang.spotshift.core.DataController
import com.borasarang.spotshift.core.HotspotController
import com.borasarang.spotshift.core.IpVerifier
import com.borasarang.spotshift.core.RotationEngine
import com.borasarang.spotshift.core.ShizukuManager
import com.borasarang.spotshift.core.SpeedChecker
import com.borasarang.spotshift.data.Prefs
import com.borasarang.spotshift.data.RotationConfig
import com.borasarang.spotshift.data.RotationRecord
import com.borasarang.spotshift.data.RotationState
import com.borasarang.spotshift.scheduler.RotationSchedule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    // v0.4 — 수동 변경 후 속도 측정용
    private val speedChecker = SpeedChecker()

    val config: StateFlow<RotationConfig> = prefs.configFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, RotationConfig())
    val records: StateFlow<List<RotationRecord>> = prefs.recordsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    // v0.4 — Shizuku 준비 상태 반응형 (승인 후 스냅샷 고착 버그 수정)
    val shizukuReady: StateFlow<Boolean> = ShizukuManager.ready
    // v0.4 — 배터리 최적화 제외 상태 (삼성 백그라운드 종료 방지)
    private val _batteryUnrestricted =
        kotlinx.coroutines.flow.MutableStateFlow(isBatteryUnrestricted())
    val batteryUnrestricted: kotlinx.coroutines.flow.StateFlow<Boolean> = _batteryUnrestricted

    fun isBatteryUnrestricted(): Boolean {
        val pm = getApplication<Application>().getSystemService(android.os.PowerManager::class.java)
            ?: return false
        return pm.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
    }

    fun refreshBatteryState() {
        _batteryUnrestricted.value = isBatteryUnrestricted()
    }

    /**
     * v0.4 — 시스템 배터리 최적화 설정 화면으로 이동.
     * 목록에서 SpotShift를 찾아 "제한 없음"으로 직접 변경한다.
     */
    fun openBatteryOptimizationSettings() {
        DebugLogger.feature("HomeViewModel", "배터리 최적화 설정 열기")
        runCatching {
            val intent = Intent(
                android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(intent)
        }
    }

    /**
     * 배터리 예외 직접 요청 (미설정 상태의 기본 버튼).
     * 시스템 허용 다이얼로그 1탭으로 예외 등록 — 복귀 시 ON_RESUME에서 상태 갱신.
     * 요청 인텐트 미지원 기기에서는 목록 화면으로 폴백한다.
     */
    fun requestBatteryExemption() {
        DebugLogger.feature("HomeViewModel", "배터리 예외 직접 요청")
        val app = getApplication<Application>()
        runCatching {
            val intent = Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:${app.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        }.onFailure {
            DebugLogger.e("배터리 예외 직접 요청 실패 — 목록 화면으로 폴백", "E-AND-SRV-0001", it as? Exception)
            openBatteryOptimizationSettings()
        }
    }
    // v0.4 — 최근 측정 속도 표시용
    val lastSpeed: StateFlow<Float?> = prefs.lastSpeedFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }

    // v0.4 — Shizuku 서비스 사망 시 권한 요청이 무응답이던 문제 수정:
    // 앱 실행 유도로 폴백 (다이얼로그를 띄울 바인더 자체가 없음)
    fun requestShizukuPermission(): Boolean {
        if (!ShizukuManager.isShizukuAvailable) {
            DebugLogger.e("Shizuku 미실행 — Shizuku 앱 실행 유도", "E-AND-PERM-0002")
            runCatching {
                val launch = getApplication<Application>().packageManager
                    .getLaunchIntentForPackage(SHIZUKU_PACKAGE)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (launch != null) getApplication<Application>().startActivity(launch)
            }
            return false
        }
        return ShizukuManager.requestPermission()
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.updateEnabled(enabled)
            // v0.5 — 자동 IP 변경 토글이 WorkManager 주기 라이프사이클을 제어 (구 FGS 서비스 대체)
            if (enabled) {
                RotationSchedule.enqueuePeriodic(getApplication(), prefs.getConfig().intervalMinutes)
            } else {
                RotationSchedule.cancel(getApplication())
            }
            DebugLogger.feature("HomeViewModel", "setEnabled=$enabled")
        }
    }

    // 키별 저장 중계 (통째 덮어쓰기 금지 — Prefs 참조)
    fun updateIntervalMinutes(v: Int) = launchUpdate {
        prefs.updateIntervalMinutes(v)
        // v0.5 — 주기 변경 시 다음 실행부터 반영되도록 재등록 (토글 ON일 때만)
        if (prefs.getConfig().enabled) RotationSchedule.enqueuePeriodic(getApplication(), v)
    }
    fun updateRetryCount(v: Int) = launchUpdate { prefs.updateRetryCount(v) }
    fun updateFallback(v: Boolean) = launchUpdate { prefs.updateFallback(v) }
    fun updateHotspotAuto(v: Boolean) = launchUpdate { prefs.updateHotspotAuto(v) }
    fun updateSpeedThreshold(v: Float) = launchUpdate { prefs.updateSpeedThreshold(v) }
    fun updateSpeedRechecks(v: Int) = launchUpdate { prefs.updateSpeedRechecks(v) }
    fun updateBootAuto(v: Boolean) = launchUpdate { prefs.updateBootAuto(v) }
    fun updateEventAlert(v: Boolean) = launchUpdate { prefs.updateEventAlert(v) }
    // T-33 — 연동 계약 v1: 외부 원격 요청 허용 토글
    fun updatePluginAllowed(v: Boolean) = launchUpdate { prefs.updatePluginAllowed(v) }

    private fun launchUpdate(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun manualRotate(onResult: (RotationRecord) -> Unit = {}) {
        viewModelScope.launch {
            if (!ShizukuManager.isReady) {
                DebugLogger.e("Shizuku 미준비 — 수동 실행 불가", "E-AND-PERM-0002")
                return@launch
            }
            // config.value 금지: 콜드스타트 직후에는 DataStore 첫 방출 전이라
            // 초기값(알림=true)이 들어있어 꺼져 있는데도 울린다. 반드시 fresh read.
            val cfg = prefs.getConfig()
            // 변경 후 검증: 기준 미달이면 재변경, 달성/최대 반복 시 종료 (건너뛰기 없음)
            val threshold = cfg.speedThresholdMbps
            val maxAttempts = cfg.speedMaxRechecks.coerceAtLeast(1)
            var attempt = 0
            var lastRecord: RotationRecord? = null
            while (attempt < maxAttempts) {
                attempt++
                val base = engine.rotate(cfg)
                engine.notifySpeedChecking()
                val speed = speedChecker.measure()
                val speedNote = if (speed.success && speed.mbps != null) {
                    prefs.updateLastSpeed(speed.mbps.toFloat())
                    val label = "%.2f".format(speed.mbps)
                    prefs.addRecord(
                        RotationRecord(
                            changed = false,
                            method = RotationRecord.METHOD_SPEED_CHECK,
                            note = "측정 ${label}Mbps"
                        )
                    )
                    if (speed.mbps >= threshold) {
                        lastRecord = finishManual(base, "측정 ${label}Mbps ≥ 기준 ${threshold}Mbps — 달성")
                        break
                    }
                    if (attempt >= maxAttempts) {
                        lastRecord = finishManual(base, "측정 ${label}Mbps < 기준 ${threshold}Mbps — 기준 미달 (IP 변경은 완료)")
                        break
                    }
                    // 재변경 전 중간 기록 (최종 기록은 루프 종료 시 최신으로 표시)
                    prefs.addRecord(base.copy(note = "측정 ${label}Mbps < 기준 ${threshold}Mbps — 재변경 ${attempt}/${maxAttempts}"))
                    if (base.changed && base.newIp != null) {
                        prefs.updateRotationMeta(System.currentTimeMillis(), base.newIp)
                    }
                    continue
                } else null
                lastRecord = finishManual(base, speedNote)
                break
            }
            // T-34 — 최종 상태 복원 (SPEED_CHECKING에서 안 돌아오면 진행 표시가 영원히 돈다)
            lastRecord?.let { final ->
                engine.notifySpeedFinished(
                    final.changed,
                    final.note ?: if (final.changed) "IP 변경 완료" else "IP 변경 실패"
                )
                onResult(final)
            }
        }
    }

    private suspend fun finishManual(base: RotationRecord, note: String?): RotationRecord {
        val record = if (note != null) base.copy(note = note) else base
        prefs.addRecord(record)
        if (record.changed && record.newIp != null) {
            prefs.updateRotationMeta(System.currentTimeMillis(), record.newIp)
        }
        return record
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

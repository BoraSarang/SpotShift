package com.borasarang.spotshift.ui.speed

import android.app.Application
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
import com.borasarang.spotshift.core.NetInfo
import com.borasarang.spotshift.core.RotationEngine
import com.borasarang.spotshift.core.ShizukuManager
import com.borasarang.spotshift.core.SignalMonitor
import com.borasarang.spotshift.core.SpeedTester
import com.borasarang.spotshift.data.Prefs
import com.borasarang.spotshift.data.RotationConfig
import com.borasarang.spotshift.data.RotationPhase
import com.borasarang.spotshift.data.SpeedRecord
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * v0.5 (T-27) — 속도 탭 ViewModel.
 * 측정(fast.com식 실시간) → 미달 시 IP 변경 → 자동 재측정 → 전/후 비교 → 유지/다시 변경.
 */
class SpeedViewModel(app: Application) : AndroidViewModel(app) {

    enum class Phase { IDLE, TESTING, DONE, CHANGING, COMPARE }

    private val prefs = Prefs(app)
    private val tester = SpeedTester()

    val config: StateFlow<RotationConfig> = prefs.configFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, RotationConfig())
    val speedRecords: StateFlow<List<SpeedRecord>> = prefs.speedRecordsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val shizukuReady: StateFlow<Boolean> = ShizukuManager.ready

    var phase: Phase by mutableStateOf(Phase.IDLE)
        private set
    // 측정 중 실시간 값
    var liveMbps: Double by mutableStateOf(0.0)
        private set
    var liveLabel: String by mutableStateOf("")
        private set
    var liveBytes: Long by mutableStateOf(0L)
        private set
    // 완료 값
    var downMbps: Double? by mutableStateOf(null)
        private set
    var upMbps: Double? by mutableStateOf(null)
        private set
    var latencyMs: Long? by mutableStateOf(null)
        private set
    var measureFailed: Boolean by mutableStateOf(false)
        private set
    // 변경 전/후 비교
    var beforeMbps: Double? by mutableStateOf(null)
        private set
    var afterMbps: Double? by mutableStateOf(null)
        private set
    var changeCount: Int by mutableStateOf(0)
        private set
    var rotationPhase: RotationPhase by mutableStateOf(RotationPhase.IDLE)
        private set

    private val engine = RotationEngine(
        context = app,
        dataController = DataController(),
        airplaneController = AirplaneController(app),
        hotspotController = HotspotController(app),
        ipVerifier = IpVerifier()
    )

    private var testJob: Job? = null

    init {
        engine.onStateChanged = { state -> rotationPhase = state.phase }
    }

    fun currentNet(): NetInfo.Snapshot = NetInfo(getApplication()).snapshot()

    fun startTest() {
        if (phase == Phase.TESTING || phase == Phase.CHANGING) return
        resetResults()
        phase = Phase.TESTING
        testJob = viewModelScope.launch {
            DebugLogger.feature("SpeedViewModel", "측정 시작")
            val net = currentNet()
            val signal = runCatching { SignalMonitor(getApplication()).snapshot().display() }.getOrNull()
            latencyMs = tester.latency()
            liveLabel = "다운로드 측정 중"
            val down = tester.download { mbps, bytes ->
                liveMbps = mbps
                liveBytes = bytes
            }
            if (!down.success) return@launch fail("E-AND-NET-0004")
            downMbps = down.mbps
            liveLabel = "업로드 측정 중"
            val up = tester.upload { mbps, bytes ->
                liveMbps = mbps
                liveBytes = bytes
            }
            if (!up.success) return@launch fail("E-AND-NET-0004")
            upMbps = up.mbps
            phase = Phase.DONE
            viewModelScope.launch {
                prefs.addSpeedRecord(
                    SpeedRecord(
                        networkType = net.current,
                        downloadMbps = down.mbps?.toFloat(),
                        uploadMbps = up.mbps?.toFloat(),
                        latencyMs = latencyMs,
                        bytesUsed = (down.bytesUsed + up.bytesUsed),
                        signal = signal
                    )
                )
            }
            DebugLogger.feature("SpeedViewModel", "측정 완료 ↓${down.mbps} ↑${up.mbps}")
        }
    }

    fun cancelTest() {
        testJob?.cancel()
        tester.cancel()
        phase = Phase.IDLE
        DebugLogger.feature("SpeedViewModel", "측정 취소")
    }

    /**
     * 미달 시 IP 변경 → 자동 재측정(다운로드) → 전/후 비교.
     */
    fun requestChange() {
        if (phase != Phase.DONE && phase != Phase.COMPARE) return
        if (!ShizukuManager.isReady) {
            DebugLogger.e("Shizuku 미준비 — 속도탭 변경 불가", "E-AND-PERM-0002")
            return
        }
        if (changeCount >= MAX_CHANGES) return
        beforeMbps = downMbps
        afterMbps = null
        phase = Phase.CHANGING
        viewModelScope.launch {
            val cfg = prefs.getConfig()
            val record = engine.rotate(cfg)
            if (record.changed && record.newIp != null) {
                prefs.updateRotationMeta(System.currentTimeMillis(), record.newIp)
            }
            if (!record.changed) {
                DebugLogger.e("속도탭 IP 변경 실패", record.errorCode ?: "E-AND-NET-0002")
                phase = Phase.DONE
                return@launch
            }
            changeCount++
            // 변경 후 재측정 (다운로드만 — 비교용)
            liveLabel = "변경 후 재측정 중"
            phase = Phase.CHANGING
            val re = tester.download { mbps, bytes ->
                liveMbps = mbps
                liveBytes = bytes
            }
            if (re.success) {
                afterMbps = re.mbps
                val net = currentNet()
                prefs.addSpeedRecord(
                    SpeedRecord(
                        networkType = net.current + " (변경 후)",
                        downloadMbps = re.mbps?.toFloat(),
                        bytesUsed = re.bytesUsed,
                        latencyMs = null,
                        signal = runCatching {
                            SignalMonitor(getApplication()).snapshot().display()
                        }.getOrNull()
                    )
                )
            }
            phase = Phase.COMPARE
        }
    }

    fun keepResult() {
        phase = Phase.DONE
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch { prefs.deleteSpeedRecord(id) }
    }

    fun clearRecords() {
        viewModelScope.launch { prefs.clearSpeedRecords() }
    }

    private fun resetResults() {
        liveMbps = 0.0
        liveBytes = 0L
        liveLabel = ""
        downMbps = null
        upMbps = null
        latencyMs = null
        measureFailed = false
        beforeMbps = null
        afterMbps = null
    }

    private fun fail(code: String) {
        DebugLogger.e("속도 측정 실패", code)
        measureFailed = true
        phase = Phase.IDLE
    }

    companion object {
        private const val MAX_CHANGES = 3
    }
}

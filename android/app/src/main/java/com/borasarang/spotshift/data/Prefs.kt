package com.borasarang.spotshift.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.borasarang.spotshift.DebugLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "spotshift_prefs")

class Prefs(private val context: Context) {

    val configFlow: Flow<RotationConfig> = context.dataStore.data.map { prefs ->
        RotationConfig(
            enabled = prefs[KEY_ENABLED] ?: false,
            intervalMinutes = prefs[KEY_INTERVAL] ?: 120,
            scheduleWindowStart = prefs[KEY_WINDOW_START],
            scheduleWindowEnd = prefs[KEY_WINDOW_END],
            minBatteryPercent = prefs[KEY_MIN_BATTERY] ?: 30,
            minSignalDbm = prefs[KEY_MIN_SIGNAL] ?: -110,
            retryCount = prefs[KEY_RETRY] ?: 2,
            airplaneHoldSec = prefs[KEY_AIRPLANE_HOLD] ?: 5,
            fallbackEnabled = prefs[KEY_FALLBACK] ?: true,
            hotspotAutoEnable = prefs[KEY_HOTSPOT_AUTO] ?: true,
            lastRotationAt = prefs[KEY_LAST_ROTATION_AT] ?: 0L,
            lastKnownIp = prefs[KEY_LAST_KNOWN_IP],
            // v0.4 — 변경 후 속도 검증 (건너뛰기 없음, 무조건 변경)
            speedThresholdMbps = prefs[KEY_SPEED_THRESHOLD] ?: 2.0f,
            speedMaxRechecks = prefs[KEY_SPEED_RECHECKS] ?: 3,
            bootAutoStart = prefs[KEY_BOOT_AUTO] ?: true,
            eventAlertEnabled = prefs[KEY_EVENT_ALERT] ?: true
        )
    }

    suspend fun getConfig(): RotationConfig = configFlow.first()

    /**
     * 키별 저장: 통째 덮어쓰기 금지.
     * 전체 객체를 읽어-수정-쓰기하면 오래된 스냅샷이 다른 키를 몰래 되돌릴 수 있어
     * (알림 OFF 표시인데 저장값 ON으로 뒤집힌 사고의 원인). 각 키는 자기 키만 건드린다.
     */
    suspend fun updateEnabled(v: Boolean) = editKey { it[KEY_ENABLED] = v }
    suspend fun updateIntervalMinutes(v: Int) = editKey { it[KEY_INTERVAL] = v }
    suspend fun updateWindow(startHour: Int?, endHour: Int?) = editKey {
        if (startHour != null) it[KEY_WINDOW_START] = startHour else it.remove(KEY_WINDOW_START)
        if (endHour != null) it[KEY_WINDOW_END] = endHour else it.remove(KEY_WINDOW_END)
    }
    suspend fun updateMinBattery(v: Int) = editKey { it[KEY_MIN_BATTERY] = v }
    suspend fun updateMinSignal(v: Int) = editKey { it[KEY_MIN_SIGNAL] = v }
    suspend fun updateRetryCount(v: Int) = editKey { it[KEY_RETRY] = v }
    suspend fun updateAirplaneHoldSec(v: Int) = editKey { it[KEY_AIRPLANE_HOLD] = v }
    suspend fun updateFallback(v: Boolean) = editKey { it[KEY_FALLBACK] = v }
    suspend fun updateHotspotAuto(v: Boolean) = editKey { it[KEY_HOTSPOT_AUTO] = v }
    suspend fun updateSpeedThreshold(v: Float) = editKey { it[KEY_SPEED_THRESHOLD] = v }
    suspend fun updateSpeedRechecks(v: Int) = editKey { it[KEY_SPEED_RECHECKS] = v }
    suspend fun updateBootAuto(v: Boolean) = editKey { it[KEY_BOOT_AUTO] = v }
    suspend fun updateEventAlert(v: Boolean) = editKey { it[KEY_EVENT_ALERT] = v }

    private suspend fun editKey(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
        DebugLogger.feature("Prefs", "키 저장됨")
    }

    suspend fun updateRotationMeta(lastRotationAt: Long, lastKnownIp: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_ROTATION_AT] = lastRotationAt
            prefs[KEY_LAST_KNOWN_IP] = lastKnownIp
        }
    }

    /**
     * 마지막 IP 변경 시각 (자동 스케줄러의 주기 내 스킵 판단용).
     */
    suspend fun getLastRotationAt(): Long =
        context.dataStore.data.first()[KEY_LAST_ROTATION_AT] ?: 0L

    val recordsFlow: Flow<List<RotationRecord>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_RECORDS] ?: return@map emptyList()
        runCatching {
            com.google.gson.Gson().fromJson(raw, Array<RotationRecord>::class.java).toList()
        }.getOrDefault(emptyList())
    }

    // v0.4 — 최근 측정 속도 (홈 상태카드 표시용)
    val lastSpeedFlow: Flow<Float?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_SPEED]
    }

    suspend fun updateLastSpeed(mbps: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SPEED] = mbps
        }
    }

    suspend fun getRecords(): List<RotationRecord> = recordsFlow.first()

    suspend fun addRecord(record: RotationRecord) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_RECORDS]?.let { raw ->
                runCatching {
                    com.google.gson.Gson().fromJson(raw, Array<RotationRecord>::class.java).toList()
                }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = (listOf(record.copy(id = System.currentTimeMillis())) + current).take(MAX_RECORDS)
            prefs[KEY_RECORDS] = com.google.gson.Gson().toJson(updated)
        }
        DebugLogger.feature("Prefs", "addRecord ${record.changed} ip=${record.newIp}")
    }

    /**
     * 기록 전체 초기화 (v0.2 — 요구사항 3).
     */
    suspend fun clearRecords() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_RECORDS)
        }
        DebugLogger.feature("Prefs", "clearRecords 실행됨")
    }

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_INTERVAL = intPreferencesKey("interval_minutes")
        private val KEY_WINDOW_START = intPreferencesKey("window_start_hour")
        private val KEY_WINDOW_END = intPreferencesKey("window_end_hour")
        private val KEY_MIN_BATTERY = intPreferencesKey("min_battery_percent")
        private val KEY_MIN_SIGNAL = intPreferencesKey("min_signal_dbm")
        private val KEY_RETRY = intPreferencesKey("retry_count")
        private val KEY_AIRPLANE_HOLD = intPreferencesKey("airplane_hold_sec")
        private val KEY_FALLBACK = booleanPreferencesKey("fallback_enabled")
        private val KEY_HOTSPOT_AUTO = booleanPreferencesKey("hotspot_auto_enable")
        private val KEY_LAST_ROTATION_AT = longPreferencesKey("last_rotation_at")
        private val KEY_LAST_KNOWN_IP = stringPreferencesKey("last_known_ip")
        private val KEY_RECORDS = stringPreferencesKey("records_json")
        // v0.4 — 변경 후 속도 검증용 (최근 측정 표시)
        private val KEY_LAST_SPEED = floatPreferencesKey("last_speed_mbps")
        // v0.4 — 변경 후 검증 기준/최대 반복
        private val KEY_SPEED_THRESHOLD = floatPreferencesKey("speed_threshold_mbps")
        private val KEY_SPEED_RECHECKS = intPreferencesKey("speed_max_rechecks")
        private val KEY_BOOT_AUTO = booleanPreferencesKey("boot_auto_start")
        // v0.4 — 변경 시작/완료 알림
        private val KEY_EVENT_ALERT = booleanPreferencesKey("event_alert_enabled")

        private const val MAX_RECORDS = 200
    }
}

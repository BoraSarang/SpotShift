package com.borasarang.spotshift.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
            lastKnownIp = prefs[KEY_LAST_KNOWN_IP]
        )
    }

    suspend fun getConfig(): RotationConfig = configFlow.first()

    suspend fun saveConfig(config: RotationConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENABLED] = config.enabled
            prefs[KEY_INTERVAL] = config.intervalMinutes
            if (config.scheduleWindowStart != null) prefs[KEY_WINDOW_START] = config.scheduleWindowStart
            else prefs.remove(KEY_WINDOW_START)
            if (config.scheduleWindowEnd != null) prefs[KEY_WINDOW_END] = config.scheduleWindowEnd
            else prefs.remove(KEY_WINDOW_END)
            prefs[KEY_MIN_BATTERY] = config.minBatteryPercent
            prefs[KEY_MIN_SIGNAL] = config.minSignalDbm
            prefs[KEY_RETRY] = config.retryCount
            prefs[KEY_AIRPLANE_HOLD] = config.airplaneHoldSec
            prefs[KEY_FALLBACK] = config.fallbackEnabled
            prefs[KEY_HOTSPOT_AUTO] = config.hotspotAutoEnable
            prefs[KEY_LAST_ROTATION_AT] = config.lastRotationAt
            if (config.lastKnownIp != null) prefs[KEY_LAST_KNOWN_IP] = config.lastKnownIp
            else prefs.remove(KEY_LAST_KNOWN_IP)
        }
        DebugLogger.feature("Prefs", "saveConfig 저장됨")
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

        private const val MAX_RECORDS = 200
    }
}

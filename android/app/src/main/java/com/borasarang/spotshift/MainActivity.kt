package com.borasarang.spotshift

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.borasarang.spotshift.core.ShizukuManager
import com.borasarang.spotshift.ui.home.HomeViewModel
import com.borasarang.spotshift.ui.home.SpotShiftRoot
import com.borasarang.spotshift.ui.theme.SpotShiftTheme

class MainActivity : ComponentActivity() {

    // v0.4 — 실행 시 Shizuku 미승인이면 1회 자동 요청 (프로세스당 1회)
    private var shizukuAutoRequested = false

    // v0.2 — 요구사항 4: 알림 권한 (Android 13+ 런타임 요청)
    // v0.4 (T-19) — 위치/전화 권한 추가 (접속 상태 표시용)
    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            DebugLogger.feature(
                "MainActivity",
                "권한 요청 결과 $results"
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLogger.feature("MainActivity", "onCreate")
        requestMissingPermissions()
        setContent {
            SpotShiftTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SpotShiftRoot()
                }
            }
        }
        handleAutorotate(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutorotate(intent)
    }

    /**
     * v0.4 — Mac 원격 IP 변경 요청 (adb am start --ez spotshift.autorotate true).
     * 앱 미실행 시 실행 후 변경, 실행 중이면 즉시 변경한다.
     */
    private fun handleAutorotate(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_AUTOROTATE, false) != true) return
        DebugLogger.feature("MainActivity", "원격 자동 변경 요청 수신")
        runCatching {
            ViewModelProvider(this)[HomeViewModel::class.java].manualRotate { record ->
                DebugLogger.i(
                    "[REMOTE] 원격 변경 결과 changed=${record.changed} " +
                        "${record.oldIp ?: "-"} → ${record.newIp ?: "-"} " +
                        (record.errorCode ?: record.note ?: "")
                )
            }
        }.onFailure { e ->
            DebugLogger.e("원격 자동 변경 실행 실패", "E-AND-SRV-0001", e as? Exception)
        }
    }

    private fun requestMissingPermissions() {
        val missing = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isGranted(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            missing += Manifest.permission.POST_NOTIFICATIONS
        }
        // v0.4 (T-19) — 접속 상태 표시용
        if (!isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            missing += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (!isGranted(Manifest.permission.READ_PHONE_STATE)) {
            missing += Manifest.permission.READ_PHONE_STATE
        }
        if (missing.isNotEmpty()) {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val EXTRA_AUTOROTATE = "spotshift.autorotate"
    }

    override fun onResume() {
        super.onResume()
        // v0.4 — 복귀 시 Shizuku 상태 갱신 (설정/승인 화면에서 돌아온 경우 반영)
        ShizukuManager.refresh()
        if (!shizukuAutoRequested) {
            shizukuAutoRequested = true
            if (ShizukuManager.isShizukuAvailable && !ShizukuManager.isPermissionGranted) {
                DebugLogger.feature("MainActivity", "Shizuku 미승인 — 실행 시 자동 요청")
                ShizukuManager.requestPermission()
            }
        }
    }
}

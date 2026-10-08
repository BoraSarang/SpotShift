package com.borasarang.spotshift.plugin

import com.borasarang.spotshift.BuildConfig

/**
 * T-35 — 연동 계약 v2 상수 + 줄 빌더 (SDK: RelayConsole docs/PLUGIN_SDK.md가 원천).
 * SpotShift 제공 레벨: L2.
 */
object PluginContract {
    const val VERSION = 2
    const val PACKAGE = "com.borasarang.spotshift"
    const val ACTION_PROBE = "$PACKAGE.PLUGIN_PROBE"
    const val ACTION = "$PACKAGE.PLUGIN_ACTION"
    const val CMD_AUTOROTATE = "autorotate"
    const val LOG_TAG = "SpotShift"
    const val PROVIDER_URI = "content://$PACKAGE.plugin/info"

    const val LABEL = "SpotShift"
    const val DESCRIPTION = "핫스팟 IP 자동 변경"

    const val ACTIONS_JSON =
        "[{\"id\":\"autorotate\",\"title\":\"IP 변경 요청\",\"kind\":\"button\"," +
            "\"invoke\":{\"via\":\"broadcast\",\"action\":\"$ACTION\"," +
            "\"extra\":{\"cmd\":\"$CMD_AUTOROTATE\"}}}]"

    private val CMD_PATTERN = Regex("[a-z_]+")

    /** --es cmd 값 정제. 이상하면 unknown (파서 계약 §5.2: 값에 공백 금지). */
    fun sanitizeCmd(raw: String?): String {
        val v = raw?.trim() ?: return "unknown"
        return if (v.matches(CMD_PATTERN)) v else "unknown"
    }

    fun probeLine(allowed: Boolean): String =
        "[PLUGIN] version=$VERSION actions=$CMD_AUTOROTATE " +
            "logTag=$LOG_TAG allowed=$allowed appVersion=${BuildConfig.VERSION_NAME}"

    fun remoteOk(oldIp: String?, newIp: String?, speedMbps: Double?, note: String): String =
        "[REMOTE] action=$CMD_AUTOROTATE ok=true " +
            "oldIp=${oldIp ?: "-"} newIp=${newIp ?: "-"} " +
            "speedMbps=${speedMbps?.let { "%.2f".format(it) } ?: "-"} note=$note"

    fun remoteFail(errorCode: String, note: String): String =
        "[REMOTE] action=$CMD_AUTOROTATE ok=false errorCode=$errorCode note=$note"

    fun remoteInvalid(cmd: String): String =
        "[REMOTE] action=$cmd ok=false errorCode=E-AND-PLG-0002 note=unknown cmd"

    fun remoteDenied(): String = "[REMOTE] 거부됨 (연동 OFF)"

    fun eventIpChanged(id: Long, oldIp: String?, newIp: String?, method: String): String =
        "[EVENT] type=ip_changed id=$id level=info " +
            "oldIp=${oldIp ?: "-"} newIp=${newIp ?: "-"} method=$method"
}

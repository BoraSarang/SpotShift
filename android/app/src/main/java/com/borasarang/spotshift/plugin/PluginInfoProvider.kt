package com.borasarang.spotshift.plugin

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.util.Base64
import com.borasarang.spotshift.BuildConfig
import com.borasarang.spotshift.data.Prefs
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream

/**
 * T-35 — 연동 계약 v2 §3 메타데이터 Provider (L2).
 *
 *   adb shell content query --uri content://com.borasarang.spotshift.plugin/info
 *
 * 단일 행. 실패·부재 시 소비자는 L1 폴백한다.
 */
class PluginInfoProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        val ctx = context?.applicationContext
        // allowed는 fresh read. 바인더 스레드에서 짧은 블로킹 허용 (로컬 DataStore 1키).
        val allowed = if (ctx != null) {
            runCatching {
                runBlocking { Prefs(ctx).getConfig().pluginAllowed }
            }.getOrDefault(true)
        } else true
        cursor.addRow(
            listOf(
                PluginContract.LABEL,
                PluginContract.DESCRIPTION,
                PluginContract.VERSION.toString(),
                BuildConfig.VERSION_NAME,
                allowed.toString(),
                loadIconBase64(),
                PluginContract.ACTIONS_JSON
            )
        )
        return cursor
    }

    /**
     * T-36 — 런처 아이콘 96px PNG base64 (SDK §3 iconBase64).
     * 리소스 번들 없이 런타임 렌더라 아이콘 바뀌면 자동 반영. 실패하면 "" (소비자 폴백).
     */
    private fun loadIconBase64(): String {
        val ctx = context?.applicationContext ?: return ""
        return runCatching {
            val drawable = ctx.packageManager.getApplicationIcon(ctx.packageName)
            val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, ICON_PX, ICON_PX)
            drawable.draw(canvas)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        }.getOrDefault("")
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        private const val ICON_PX = 96
        val COLUMNS = arrayOf(
            "label",
            "description",
            "contractVersion",
            "appVersion",
            "allowed",
            "iconBase64",
            "actionsJson"
        )
    }
}

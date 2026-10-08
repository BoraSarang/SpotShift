package com.borasarang.spotshift.plugin

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.borasarang.spotshift.BuildConfig
import com.borasarang.spotshift.data.Prefs
import kotlinx.coroutines.runBlocking

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
                "", // iconBase64 (선택, 미제공 → 소비자 폴백)
                PluginContract.ACTIONS_JSON
            )
        )
        return cursor
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

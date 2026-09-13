package com.xiaoxiong.weather.data

import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences

private const val TAG = "DataStore"

/**
 * DataStore 文件损坏时重置为空并记录日志。
 *
 * 默认 `ReplaceFileCorruptionHandler { emptyPreferences() }` 会静默清空文件：
 * 用户设置/收藏「莫名重置」时没有任何可诊断线索（尤其 [AppSettingsStore]）。
 * 这里统一带上 store 名打警告日志，便于定位是哪个文件损坏。
 */
internal fun corruptionHandlerLogging(name: String): ReplaceFileCorruptionHandler<Preferences> =
    ReplaceFileCorruptionHandler { e ->
        Log.w(TAG, "corrupted store $name, resetting", e)
        emptyPreferences()
    }

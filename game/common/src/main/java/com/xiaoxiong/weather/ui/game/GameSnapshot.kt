package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 对局快照的轻量本地存储：把每个游戏自己的紧凑文本快照存进 `bear_game` prefs。
 *
 * 目的：旋转屏幕（若未锁方向）或进程被系统回收后，重新进入游戏能续上对局，
 * 而不是从标题重开。快照内容由各游戏自行编码（不使用 kotlinx.serialization，
 * 避免把序列化运行时依赖引入每个游戏模块）。
 *
 * 约定：对局进行中保存；对局结束/回到标题/主动重开时 [clear]，避免陈旧续玩。
 */
object GameSnapshot {
    fun save(
        prefs: SharedPreferences,
        key: String,
        payload: String,
    ) {
        runCatching { prefs.edit { putString(key, payload) } }
    }

    fun load(
        prefs: SharedPreferences,
        key: String,
    ): String? = runCatching { prefs.getString(key, null) }.getOrNull()

    fun clear(
        prefs: SharedPreferences,
        key: String,
    ) {
        runCatching { prefs.edit { remove(key) } }
    }
}

/** 快照编码小工具：用固定分隔符拼装/解析数值，避免各游戏重复实现。 */
object SnapshotCodec {
    private const val GROUP = ';'
    private const val FIELD = ','

    fun encode(vararg values: Number): String = values.joinToString(FIELD.toString()) { it.toString() }

    fun encodeGroups(groups: List<List<Number>>): String = groups.joinToString(GROUP.toString()) { group -> encode(*group.toTypedArray()) }

    fun decodeFloatArray(text: String?): FloatArray? {
        if (text.isNullOrBlank()) return null
        return runCatching { text.split(FIELD).map { it.toFloat() }.toFloatArray() }.getOrNull()
    }

    fun decodeGroups(text: String?): List<FloatArray>? {
        if (text == null) return null
        // 空字符串对应 encodeGroups(emptyList())：解码回空列表而不是 null，保证往返对称
        if (text.isBlank()) return emptyList()
        return runCatching {
            text.split(GROUP).mapNotNull { part ->
                if (part.isBlank()) null else part.split(FIELD).map { it.toFloat() }.toFloatArray()
            }
        }.getOrNull()
    }
}

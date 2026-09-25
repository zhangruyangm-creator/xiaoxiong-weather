package com.xiaoxiong.weather.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.time.LocalDate

/**
 * 当前日期（epochDay），跨零点自动刷新。
 *
 * 直接 `remember { LocalDate.now() }` 会在页面停留期间永久停在进入那天的日期：
 * 每日挑战、赛季提示、可选日期上限全部错位一天——尤其"跨过午夜后仍显示昨天"
 * 对用户是不可解释的。迷宫此前单独实现了 ON_RESUME 刷新，这里抽成公共件复用。
 */
@Composable
fun rememberTodayEpochDay(): State<Long> {
    // 用 mutableStateOf 而非 mutableLongStateOf：renameSaveable 对 MutableState 有
    // 明确支持，而本仓其余 rememberSaveable 也都用前者，不在这里率先依赖 Long 变体。
    val today = rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, today) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    today.value = LocalDate.now().toEpochDay()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return today
}

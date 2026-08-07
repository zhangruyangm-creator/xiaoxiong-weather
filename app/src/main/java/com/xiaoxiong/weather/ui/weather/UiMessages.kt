package com.xiaoxiong.weather.ui.weather

import android.app.Application
import androidx.annotation.StringRes
import com.xiaoxiong.weather.data.toAppError
import com.xiaoxiong.weather.data.toUserMessage

internal fun Application.str(@StringRes id: Int, vararg args: Any): String =
    if (args.isEmpty()) getString(id) else getString(id, *args)

internal fun Throwable.userFacingMessage(app: Application, @StringRes fallback: Int): String =
    toAppError().toUserMessage(app.resources, fallback)

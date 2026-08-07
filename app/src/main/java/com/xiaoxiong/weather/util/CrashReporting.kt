package com.xiaoxiong.weather.util

import android.content.Context
import android.util.Log

/**
 * 崩溃上报门面：放入 google-services.json 并启用 Firebase 插件后自动走 Crashlytics；
 * 未配置时仅打日志，不阻断编译与运行。
 */
object CrashReporting {
    private const val TAG = "CrashReporting"

    fun init(context: Context) {
        runCatching {
            val clazz = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
            val getInstance = clazz.getMethod("getInstance")
            val instance = getInstance.invoke(null)
            clazz.getMethod("setCrashlyticsCollectionEnabled", Boolean::class.javaPrimitiveType)
                .invoke(instance, true)
            Log.i(TAG, "Firebase Crashlytics enabled")
        }.onFailure {
            Log.i(TAG, "Crashlytics not configured (add app/google-services.json to enable)")
        }
    }

    fun record(throwable: Throwable) {
        Log.e(TAG, "non-fatal", throwable)
        runCatching {
            val clazz = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
            val instance = clazz.getMethod("getInstance").invoke(null)
            clazz.getMethod("recordException", Throwable::class.java).invoke(instance, throwable)
        }
    }
}

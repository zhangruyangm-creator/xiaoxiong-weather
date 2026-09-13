package com.xiaoxiong.weather.ui.settings

/** 通知开关相关动作（纯逻辑，便于单测）。 */
internal enum class NotificationToggleAction {
    SET_ENABLED,
    SET_DISABLED,
    REQUEST_PERMISSION,
    OPEN_SYSTEM_SETTINGS,
    NONE,
}

/**
 * 点击通知开关时的决策：
 * - 关闭：直接写设置；
 * - 打开且不需要权限/已有权限：写设置；
 * - 打开但未授权：请求 POST_NOTIFICATIONS。
 */
internal fun notificationToggleAction(
    turningOn: Boolean,
    permissionRequired: Boolean,
    permissionGranted: Boolean,
): NotificationToggleAction =
    when {
        !turningOn -> NotificationToggleAction.SET_DISABLED
        !permissionRequired || permissionGranted -> NotificationToggleAction.SET_ENABLED
        else -> NotificationToggleAction.REQUEST_PERMISSION
    }

/**
 * 权限请求结果回调决策：
 * - 授权：写入开启；
 * - 拒绝但下次仍可请求：不做额外动作（开关保持关闭）；
 * - 拒绝且不再可请求：引导到系统通知设置页。
 */
internal fun notificationPermissionResultAction(
    permissionGranted: Boolean,
    canAskAgain: Boolean,
): NotificationToggleAction =
    when {
        permissionGranted -> NotificationToggleAction.SET_ENABLED
        canAskAgain -> NotificationToggleAction.NONE
        else -> NotificationToggleAction.OPEN_SYSTEM_SETTINGS
    }

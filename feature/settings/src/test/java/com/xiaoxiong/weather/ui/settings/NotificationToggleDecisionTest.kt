package com.xiaoxiong.weather.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationToggleDecisionTest {
    @Test
    fun turningOff_alwaysDisables() {
        assertEquals(
            NotificationToggleAction.SET_DISABLED,
            notificationToggleAction(turningOn = false, permissionRequired = true, permissionGranted = false),
        )
        assertEquals(
            NotificationToggleAction.SET_DISABLED,
            notificationToggleAction(turningOn = false, permissionRequired = false, permissionGranted = true),
        )
    }

    @Test
    fun turningOn_withoutPermissionRequirement_enables() {
        assertEquals(
            NotificationToggleAction.SET_ENABLED,
            notificationToggleAction(turningOn = true, permissionRequired = false, permissionGranted = false),
        )
    }

    @Test
    fun turningOn_withPermissionGranted_enables() {
        assertEquals(
            NotificationToggleAction.SET_ENABLED,
            notificationToggleAction(turningOn = true, permissionRequired = true, permissionGranted = true),
        )
    }

    @Test
    fun turningOn_withPermissionMissing_requestsPermission() {
        assertEquals(
            NotificationToggleAction.REQUEST_PERMISSION,
            notificationToggleAction(turningOn = true, permissionRequired = true, permissionGranted = false),
        )
    }

    @Test
    fun permissionResult_granted_enables() {
        assertEquals(
            NotificationToggleAction.SET_ENABLED,
            notificationPermissionResultAction(permissionGranted = true, canAskAgain = true),
        )
        assertEquals(
            NotificationToggleAction.SET_ENABLED,
            notificationPermissionResultAction(permissionGranted = true, canAskAgain = false),
        )
    }

    @Test
    fun permissionResult_deniedButCanAskAgain_doesNothing() {
        assertEquals(
            NotificationToggleAction.NONE,
            notificationPermissionResultAction(permissionGranted = false, canAskAgain = true),
        )
    }

    @Test
    fun permissionResult_deniedForever_opensSystemSettings() {
        assertEquals(
            NotificationToggleAction.OPEN_SYSTEM_SETTINGS,
            notificationPermissionResultAction(permissionGranted = false, canAskAgain = false),
        )
    }
}

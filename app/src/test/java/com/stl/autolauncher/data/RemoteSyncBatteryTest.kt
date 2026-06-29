package com.stl.autolauncher.data

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSyncBatteryTest {
    @Test
    fun chargingStatusIsReportedAsCharging() {
        assertTrue(resolveRemoteChargingState(BatteryManager.BATTERY_STATUS_CHARGING, 0))
    }

    @Test
    fun fullStatusIsReportedAsCharging() {
        assertTrue(resolveRemoteChargingState(BatteryManager.BATTERY_STATUS_FULL, 0))
    }

    @Test
    fun pluggedPowerIsReportedAsChargingWhenStatusIsUnknown() {
        assertTrue(resolveRemoteChargingState(BatteryManager.BATTERY_STATUS_UNKNOWN, BatteryManager.BATTERY_PLUGGED_AC))
        assertTrue(resolveRemoteChargingState(BatteryManager.BATTERY_STATUS_UNKNOWN, BatteryManager.BATTERY_PLUGGED_USB))
        assertTrue(resolveRemoteChargingState(BatteryManager.BATTERY_STATUS_UNKNOWN, BatteryManager.BATTERY_PLUGGED_WIRELESS))
    }

    @Test
    fun unpluggedDischargingBatteryIsNotCharging() {
        assertFalse(resolveRemoteChargingState(BatteryManager.BATTERY_STATUS_DISCHARGING, 0))
        assertFalse(resolveRemoteChargingState(BatteryManager.BATTERY_STATUS_NOT_CHARGING, 0))
    }

    @Test
    fun batteryManagerPercentTakesPrecedenceWhenAvailable() {
        assertEquals(72, resolveRemoteBatteryPercent(managerPercent = 72, level = 12, scale = 20))
    }

    @Test
    fun levelAndScaleAreUsedWhenBatteryManagerPercentIsUnavailable() {
        assertEquals(60, resolveRemoteBatteryPercent(managerPercent = -1, level = 3, scale = 5))
        assertEquals(0, resolveRemoteBatteryPercent(managerPercent = -1, level = -1, scale = 100))
        assertEquals(0, resolveRemoteBatteryPercent(managerPercent = -1, level = 50, scale = 0))
    }
}

package com.unifiedremote.evo.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unifiedremote.evo.data.SavedDevice
import com.unifiedremote.evo.network.BluetoothSetupStatus
import com.unifiedremote.evo.network.ble.BleConnectionState
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BluetoothRecoveryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After fun closeActivity() { scenario?.close() }

    private fun showConfig(
        status: androidx.compose.runtime.State<BluetoothSetupStatus>,
        saved: List<SavedDevice> = emptyList(),
        onEnable: () -> Unit = {},
        onPermissions: () -> Unit = {},
        onConnect: (SavedDevice) -> Unit = {}
    ) {
        // Put the test host in the foreground before ActivityScenario starts it.
        // Some physical devices restrict activity launches from a background runner.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("am start -W -n com.unifiedremote.evo/androidx.activity.ComponentActivity").use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        scenario = ActivityScenario.launch(Intent(ApplicationProvider.getApplicationContext(), ComponentActivity::class.java))
        scenario!!.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    ServerConfigScreen(
                        onConnectTcp = { _, _ -> }, onConnectBluetooth = {}, onStartBleScan = {},
                        onConnectBleDevice = {}, onConnectSaved = onConnect, onRemoveSaved = {},
                        bluetoothAvailable = status.value == BluetoothSetupStatus.READY,
                        bleAvailable = status.value == BluetoothSetupStatus.READY,
                        pairedDevices = emptyList(), bleScannedDevices = emptyList(),
                        bleConnectionState = BleConnectionState.Disconnected, savedDevices = saved,
                        bluetoothSetupStatus = status.value, onEnableBluetooth = onEnable,
                        onRequestBluetoothPermissions = onPermissions
                    )
                }
            }
        }
    }

    @Test fun radioOffOffersEnableAndBothModesRemainActionable() {
        var enables = 0
        showConfig(mutableStateOf(BluetoothSetupStatus.OFF), onEnable = { enables++ })
        compose.onNodeWithText("藍牙已關閉").assertIsDisplayed()
        compose.onNodeWithText("開啟藍牙").performClick()
        compose.onNodeWithText("藍牙", substring = false).performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("BLE", substring = false).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(3, enables) }
    }

    @Test fun permissionPromptRequestsPermissionAndReadyRemovesIt() {
        val status = mutableStateOf(BluetoothSetupStatus.PERMISSION_REQUIRED)
        var requests = 0
        showConfig(status, onPermissions = { requests++ })
        compose.onNodeWithText("授予藍牙權限").performClick()
        compose.onNodeWithText("BLE", substring = false).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, requests); status.value = BluetoothSetupStatus.READY }
        compose.onNodeWithText("需要藍牙權限").assertDoesNotExist()
        compose.onNodeWithText("開啟藍牙").assertDoesNotExist()
        compose.onNodeWithText("BLE", substring = false).assertIsSelected()
    }

    @Test fun savedBluetoothConnectionWaitsForRadioAndResumesOnce() {
        val status = mutableStateOf(BluetoothSetupStatus.OFF)
        val device = SavedDevice.createBleEmulstick("Test Stick", "00:11:22:33:44:55")
        val connections = mutableListOf<SavedDevice>()
        var enables = 0
        showConfig(status, listOf(device), onEnable = { enables++ }, onConnect = { connections.add(it) })
        compose.onNodeWithText("Test Stick").performClick()
        compose.runOnIdle { assertEquals(1, enables); assertEquals(0, connections.size); status.value = BluetoothSetupStatus.READY }
        compose.waitUntil { connections.size == 1 }
        compose.runOnIdle { assertEquals(listOf(device), connections) }
    }

    @Test fun unsupportedHardwareExplainsWhyModesAreUnavailable() {
        showConfig(mutableStateOf(BluetoothSetupStatus.UNSUPPORTED))
        compose.onNodeWithText("此裝置不支援藍牙").assertIsDisplayed()
        compose.onNodeWithText("藍牙", substring = false).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("BLE", substring = false).assertIsNotEnabled()
    }
}

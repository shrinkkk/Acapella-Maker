package com.shrinkkk.acapellamaker.calibration

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Where sound is currently going. Each route has its own delay, so each gets its own calibration.
 * Android sends media to the most recently connected headset-type device, so we pick by priority.
 */
enum class AudioRoute(val key: String, val label: String) {
    BLUETOOTH("bluetooth", "Bluetooth earphones"),
    USB("usb", "USB-C earphones / adapter"),
    WIRED("wired", "Wired earphones (3.5 mm)"),
    SPEAKER("speaker", "Phone speaker");

    val isEarphones: Boolean get() = this != SPEAKER

    companion object {
        private val bluetoothTypes = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
        private val usbTypes = setOf(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE)
        private val wiredTypes = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        )

        fun current(context: Context): AudioRoute {
            val audioManager = context.getSystemService(AudioManager::class.java)
            val types = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.toSet()
            return when {
                types.any { it in bluetoothTypes } -> BLUETOOTH
                types.any { it in usbTypes } -> USB
                types.any { it in wiredTypes } -> WIRED
                else -> SPEAKER
            }
        }
    }
}

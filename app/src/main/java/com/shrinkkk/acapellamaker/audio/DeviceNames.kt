package com.shrinkkk.acapellamaker.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/** Human-readable name for an AudioDeviceInfo id, e.g. "USB-C Adapter (USB headset)". */
fun describeAudioDevice(context: Context, id: Int): String {
    if (id == 0) return "unknown"
    val audioManager = context.getSystemService(AudioManager::class.java)
    val device = audioManager.getDevices(AudioManager.GET_DEVICES_ALL).firstOrNull { it.id == id }
        ?: return "device #$id"
    val type = when (device.type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "phone speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "phone earpiece"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "phone mic"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired headphones"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (call mode)"
        else -> "type ${device.type}"
    }
    return "${device.productName} ($type)"
}

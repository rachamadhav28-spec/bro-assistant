package com.bro.assistant

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

private var torchIsOn = false

fun tryDeviceCommand(context: Context, spoken: String): String? {
    val lower = spoken.lowercase()
    val mentionsLight = lower.contains("flashlight") ||
        lower.contains("torch") ||
        Regex("\\blight\\b").containsMatchIn(lower)
    if (!mentionsLight) return null

    val wantsOn = Regex("\\bon\\b").containsMatchIn(lower)
    val wantsOff = Regex("\\boff\\b").containsMatchIn(lower)
    val turnOn = when {
        wantsOn && !wantsOff -> true
        wantsOff && !wantsOn -> false
        else -> !torchIsOn
    }

    return try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "I can't find a flashlight on this phone."
        cm.setTorchMode(id, turnOn)
        torchIsOn = turnOn
        if (turnOn) "Flashlight on." else "Flashlight off."
    } catch (e: Exception) {
        "I couldn't control the flashlight."
    }
}

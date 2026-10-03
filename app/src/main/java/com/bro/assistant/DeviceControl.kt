package com.bro.assistant

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

private var torchIsOn = false

fun tryDeviceCommand(context: Context, spoken: String): String? {
    val text = spoken.lowercase().trim().removeSuffix(".")
    val words = text.split(" ").filter { it.isNotBlank() }

    val mentionsLight = words.any { it == "light" || it == "torch" || it == "flashlight" }
    if (mentionsLight && words.size <= 6) {
        val wantsOff = words.any { it == "off" || it == "stop" || it == "close" }
        val wantsOn = words.any { it == "on" || it == "start" || it == "open" }
        val turnOn = when {
            wantsOff -> false
            wantsOn -> true
            else -> !torchIsOn
        }
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                val c = cm.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            if (cameraId == null) {
                "I could not find a flashlight on this phone"
            } else {
                cm.setTorchMode(cameraId, turnOn)
                torchIsOn = turnOn
                if (turnOn) "Light on" else "Light off"
            }
        } catch (e: Exception) {
            "I could not use the flashlight"
        }
    }

    return tryAccessibilityCommand(context, spoken)
}

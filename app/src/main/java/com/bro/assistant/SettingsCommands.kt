package com.bro.assistant

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

private class Target(val label: String, val names: List<String>, val screen: Intent)

private fun launchScreen(context: Context, intent: Intent): Boolean {
    return try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}

fun trySettingsCommand(context: Context, spoken: String): String? {
    val text = spoken.lowercase().trim().removeSuffix(".")
    val words = text.split(" ").filter { it.isNotBlank() }
    if (words.size > 7) return null

    val commandWords = setOf(
        "settings", "setting", "on", "off", "turn", "switch", "enable", "disable", "toggle"
    )
    if (words.none { it in commandWords }) return null

    val hasHotspot = words.contains("hotspot") || text.contains("hot spot")
    val hasBluetooth = words.contains("bluetooth")
    val hasWifi = words.contains("wifi") || text.contains("wi-fi") || text.contains("wi fi")
    val hasAirplane = words.contains("airplane") || words.contains("flight")
    val hasLocation = words.contains("location") || words.contains("gps")
    val hasMobileData = words.contains("mobile") && words.contains("data")
    val hasBrightness = words.contains("brightness")

    val target: Target = when {
        hasHotspot -> Target(
            "hotspot",
            listOf("hotspot", "hot spot", "tethering"),
            Intent().setComponent(
                ComponentName("com.android.settings", "com.android.settings.TetherSettings")
            )
        )
        hasBluetooth -> Target(
            "Bluetooth", listOf("bluetooth"), Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        )
        hasWifi -> Target(
            "Wi-Fi", listOf("wi-fi", "wifi", "wlan"), Intent(Settings.ACTION_WIFI_SETTINGS)
        )
        hasAirplane -> Target(
            "airplane mode",
            listOf("airplane", "flight mode", "flight"),
            Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        )
        hasLocation -> Target(
            "location", listOf("location", "gps"), Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        )
        hasMobileData -> Target(
            "mobile data",
            listOf("mobile data", "mobile network"),
            Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
        )
        hasBrightness -> Target(
            "display", emptyList(), Intent(Settings.ACTION_DISPLAY_SETTINGS)
        )
        else -> return null
    }

    val screenOnly = words.contains("settings") || words.contains("setting") || hasBrightness
    if (screenOnly) {
        var opened = launchScreen(context, target.screen)
        if (!opened && hasHotspot) {
            opened = launchScreen(context, Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
        return if (opened) "Opening ${target.label} settings" else "I could not open ${target.label} settings"
    }

    val service = BroAccessibilityService.instance
        ?: return "Please turn on Bro in Accessibility settings first."

    val wantOn: Boolean? = when {
        words.contains("off") || words.contains("disable") -> false
        words.contains("on") || words.contains("enable") -> true
        else -> null
    }
    val fallback = if (hasHotspot) Intent(Settings.ACTION_WIRELESS_SETTINGS) else target.screen

    service.toggleTile(target.names, wantOn, fallback)

    return when (wantOn) {
        true -> "Turning on ${target.label}"
        false -> "Turning off ${target.label}"
        null -> "Switching ${target.label}"
    }
}

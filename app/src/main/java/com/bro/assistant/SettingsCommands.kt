package com.bro.assistant
 
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import java.net.NetworkInterface
 
private class Target(
    val label: String,
    val names: List<String>,
    val screen: Intent,
    val state: (Context) -> Boolean?
)
 
private fun launchScreen(context: Context, intent: Intent): Boolean {
    return try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}
 
private fun globalIsOn(context: Context, key: String, onValues: Set<Int>): Boolean? {
    return try {
        val v = Settings.Global.getInt(context.contentResolver, key, -1)
        if (v == -1) null else (v in onValues)
    } catch (e: Exception) {
        null
    }
}
 
private fun locationIsOn(context: Context): Boolean? {
    return try {
        if (Build.VERSION.SDK_INT >= 28) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.isLocationEnabled
        } else {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0
        }
    } catch (e: Exception) {
        null
    }
}
 
// Returns true only when a hotspot network interface is clearly up.
// Returns null when we cannot tell, so Bro will just tap the tile.
private fun hotspotIsOn(): Boolean? {
    return try {
        val list = NetworkInterface.getNetworkInterfaces() ?: return null
        var on = false
        for (ni in list) {
            val n = ni.name.lowercase()
            val looksLikeAp = n.startsWith("ap") || n.startsWith("swlan") ||
                n.startsWith("softap") || n.startsWith("wlan1") || n.startsWith("wlan2")
            if (looksLikeAp && ni.isUp) on = true
        }
        if (on) true else null
    } catch (e: Exception) {
        null
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
            ),
            { _ -> hotspotIsOn() }
        )
        hasBluetooth -> Target(
            "Bluetooth",
            listOf("bluetooth"),
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            { c -> globalIsOn(c, "bluetooth_on", setOf(1)) }
        )
        hasWifi -> Target(
            "Wi-Fi",
            listOf("wi-fi", "wifi", "wlan"),
            Intent(Settings.ACTION_WIFI_SETTINGS),
            { c -> globalIsOn(c, "wifi_on", setOf(1, 2)) }
        )
        hasAirplane -> Target(
            "airplane mode",
            listOf("airplane", "flight mode", "flight"),
            Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS),
            { c -> globalIsOn(c, Settings.Global.AIRPLANE_MODE_ON, setOf(1)) }
        )
        hasLocation -> Target(
            "location",
            listOf("location", "gps"),
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            { c -> locationIsOn(c) }
        )
        hasMobileData -> Target(
            "mobile data",
            listOf("mobile data", "mobile network"),
            Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
            { c -> globalIsOn(c, "mobile_data", setOf(1)) }
        )
        hasBrightness -> Target(
            "display",
            emptyList(),
            Intent(Settings.ACTION_DISPLAY_SETTINGS),
            { _ -> null }
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
 
    val wantOn: Boolean? = when {
        words.contains("off") || words.contains("disable") -> false
        words.contains("on") || words.contains("enable") -> true
        else -> null
    }
 
    // Ask the phone itself whether it is already on or off.
    val current = target.state(context)
    if (wantOn != null && current != null && current == wantOn) {
        return if (wantOn) "${target.label} is already on" else "${target.label} is already off"
    }
 
    val service = BroAccessibilityService.instance
        ?: return "Please turn on Bro in Accessibility settings first."
 
    val fallback = if (hasHotspot) Intent(Settings.ACTION_WIRELESS_SETTINGS) else target.screen
 
    // Always just tap the tile once; the phone's own state was checked above.
    service.toggleTile(target.names, null, fallback)
 
    return when (wantOn) {
        true -> "Turning on ${target.label}"
        false -> "Turning off ${target.label}"
        null -> "Switching ${target.label}"
    }
}
 

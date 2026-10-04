package com.bro.assistant
 
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.os.PowerManager
import android.provider.Settings
 
private class QuickTile(
    val label: String,
    val phrases: List<String>,
    val names: List<String>,
    val state: (Context) -> Boolean?
)
 
private fun rotationOn(c: Context): Boolean? {
    return try {
        val v = Settings.System.getInt(c.contentResolver, "accelerometer_rotation", -1)
        if (v == -1) null else (v == 1)
    } catch (e: Exception) {
        null
    }
}
 
private fun darkModeOn(c: Context): Boolean? {
    return try {
        val mode = c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        mode == Configuration.UI_MODE_NIGHT_YES
    } catch (e: Exception) {
        null
    }
}
 
private fun dndOn(c: Context): Boolean? {
    return try {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val f = nm.currentInterruptionFilter
        if (f == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
            null
        } else {
            f != NotificationManager.INTERRUPTION_FILTER_ALL
        }
    } catch (e: Exception) {
        null
    }
}
 
private fun batterySaverOn(c: Context): Boolean? {
    return try {
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isPowerSaveMode
    } catch (e: Exception) {
        null
    }
}
 
// Longer phrases come first so "super battery saver" is checked before "battery saver".
private val quickTiles: List<QuickTile> = listOf(
    QuickTile(
        "super battery saver",
        listOf("super battery saver", "super battery"),
        listOf("super battery"),
        { _ -> null }
    ),
    QuickTile(
        "battery saver",
        listOf("battery saver", "power saving", "power saver"),
        listOf("^battery saver"),
        { c -> batterySaverOn(c) }
    ),
    QuickTile(
        "auto rotate",
        listOf("auto rotate", "autorotate", "screen rotation", "rotation"),
        listOf("rotat"),
        { c -> rotationOn(c) }
    ),
    QuickTile(
        "dark mode",
        listOf("dark mode"),
        listOf("dark mode"),
        { c -> darkModeOn(c) }
    ),
    QuickTile(
        "do not disturb",
        listOf("do not disturb", "dnd"),
        listOf("do not disturb"),
        { c -> dndOn(c) }
    ),
    QuickTile(
        "eye protection",
        listOf("eye protection", "eye comfort"),
        listOf("eye protection"),
        { _ -> null }
    ),
    QuickTile(
        "ultra game mode",
        listOf("ultra game mode", "game mode"),
        listOf("ultra game"),
        { _ -> null }
    ),
    QuickTile(
        "monster mode",
        listOf("monster mode"),
        listOf("monster"),
        { _ -> null }
    ),
    QuickTile(
        "speed up",
        listOf("speed up"),
        listOf("speed up"),
        { _ -> null }
    ),
    QuickTile(
        "iQOO share",
        listOf("iqooshare", "iqoo share", "iqoo shared"),
        listOf("iqooshar"),
        { _ -> null }
    ),
    QuickTile(
        "device controls",
        listOf("device controls", "device control"),
        listOf("device controls"),
        { _ -> null }
    ),
    QuickTile(
        "scan code",
        listOf("scan code", "scan qr", "qr scanner"),
        listOf("scan code"),
        { _ -> null }
    ),
    QuickTile(
        "split screen",
        listOf("split screen"),
        listOf("split"),
        { _ -> null }
    ),
    QuickTile(
        "screenshot",
        listOf("screenshot", "screen shot"),
        listOf("super screenshot", "screenshot"),
        { _ -> null }
    ),
    QuickTile(
        "screen recording",
        listOf("record screen", "screen record"),
        listOf("record screen", "screen record"),
        { _ -> null }
    ),
    QuickTile(
        "Wi-Fi calling",
        listOf("vowifi", "wifi calling", "wi fi calling"),
        listOf("vowifi"),
        { _ -> null }
    )
)
 
fun tryTileCommand(context: Context, spoken: String): String? {
    val text = spoken.lowercase().trim().removeSuffix(".").replace("-", " ")
    val words = text.split(" ").filter { it.isNotBlank() }
    if (words.size > 6) return null
 
    val verbs = setOf(
        "turn", "switch", "toggle", "enable", "disable", "start", "stop",
        "activate", "take", "record", "on", "off"
    )
    if (words.none { it in verbs }) return null
 
    val tile = quickTiles.firstOrNull { t -> t.phrases.any { text.contains(it) } }
        ?: return null
 
    val wantOn: Boolean? = when {
        words.contains("off") || words.contains("disable") || words.contains("stop") -> false
        words.contains("on") || words.contains("enable") || words.contains("start") -> true
        else -> null
    }
 
    val current = tile.state(context)
    if (wantOn != null && current != null && current == wantOn) {
        return if (wantOn) "${tile.label} is already on" else "${tile.label} is already off"
    }
 
    val service = BroAccessibilityService.instance
        ?: return "Please turn on Bro in Accessibility settings first."
 
    service.toggleTile(tile.names, null, null)
 
    return when (wantOn) {
        true -> "Turning on ${tile.label}"
        false -> "Turning off ${tile.label}"
        null -> "Okay, ${tile.label}"
    }
}
 

package com.bro.assistant

import android.content.Context
import android.media.AudioManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun tryExtraCommand(context: Context, spoken: String): String? {
    val text = spoken.lowercase().trim().removeSuffix(".").removeSuffix("?")
    val words = text.split(" ").filter { it.isNotBlank() }

    // Volume commands
    val volumeWord = words.any { it == "volume" || it == "sound" }
    val loudWord = words.any { it == "louder" || it == "quieter" || it == "softer" }
    val muteWord = words.any { it == "mute" || it == "unmute" }
    if ((volumeWord || loudWord || muteWord) && words.size <= 8) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        val max = audio.getStreamMaxVolume(stream)
        val upWords = setOf("up", "increase", "raise", "louder", "higher", "more")
        val downWords = setOf("down", "decrease", "lower", "quieter", "softer", "less", "reduce")
        return when {
            words.contains("unmute") -> {
                audio.setStreamVolume(stream, max / 2, AudioManager.FLAG_SHOW_UI)
                "Volume restored"
            }
            words.contains("mute") -> {
                audio.setStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI)
                "Volume muted"
            }
            words.contains("max") || words.contains("maximum") || words.contains("full") -> {
                audio.setStreamVolume(stream, max, AudioManager.FLAG_SHOW_UI)
                "Volume at maximum"
            }
            words.any { it in upWords } -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "Volume up"
            }
            words.any { it in downWords } -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "Volume down"
            }
            else -> null
        }
    }

    // Time
    val isTime = words.size <= 6 &&
        !words.contains("in") && !words.contains("complexity") && !words.contains("zone") &&
        (text == "time" || text.contains("the time") || text.contains("time now") ||
            text.contains("what time is it") || text.contains("current time"))
    if (isTime) {
        val now = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        return "It is $now"
    }

    // Date
    val askedDate = words.size <= 6 &&
        (words.contains("date") || text.contains("what day"))
    val dateContext = words.any { it == "what" || it == "what's" || it == "whats" ||
        it == "today" || it == "today's" || it == "tell" } || text == "date"
    if (askedDate && dateContext) {
        val today = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
        return "Today is $today"
    }

    return null
}

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
    if (words.contains("volume") && words.size <= 5) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        val max = audio.getStreamMaxVolume(stream)
        return when {
            words.contains("max") || words.contains("full") -> {
                audio.setStreamVolume(stream, max, AudioManager.FLAG_SHOW_UI)
                "Volume at maximum"
            }
            words.contains("mute") -> {
                audio.setStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI)
                "Volume muted"
            }
            words.contains("up") || words.contains("increase") || words.contains("raise") -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "Volume up"
            }
            words.contains("down") || words.contains("decrease") || words.contains("lower") -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "Volume down"
            }
            else -> null
        }
    }

    // Time
    val timePhrases = setOf(
        "time", "current time", "what time is it", "what is the time",
        "what's the time", "tell me the time", "what is the current time"
    )
    if (text in timePhrases) {
        val now = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        return "It is $now"
    }

    // Date
    val datePhrases = setOf(
        "date", "what is the date", "what's the date", "today's date", "what date is it",
        "what day is it", "what is today's date", "what day is today", "tell me the date"
    )
    if (text in datePhrases) {
        val today = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
        return "Today is $today"
    }

    return null
}

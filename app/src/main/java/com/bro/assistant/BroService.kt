package com.bro.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class BroService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val wakeRegex = Regex("(hey|hi|ok|okay|hay)?\\s*bro\\b")

    override fun onCreate() {
        super.onCreate()
        val notification = buildNotification("Listening for \"Hey Bro\"...")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(0.75f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        handler.post {
                            if (utteranceId == "wake") {
                                listen(true)
                            } else {
                                updateNotification("Listening for \"Hey Bro\"...")
                                listen(false)
                            }
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        handler.post { listen(false) }
                    }
                })
                ttsReady = true
            }
        }
        listen(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String): Notification {
        val channelId = "bro_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Bro Assistant", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Bro")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(1, buildNotification(text))
    }

    private fun listen(forCommand: Boolean) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    stopSelf()
                    return
                }
                if (forCommand) updateNotification("Listening for \"Hey Bro\"...")
                val quick = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                handler.postDelayed({ listen(false) }, if (quick) 300L else 1500L)
            }
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""
                if (forCommand) {
                    handleCommand(text)
                } else {
                    handleWake(text)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
        }
        recognizer?.startListening(intent)
    }

    private fun handleWake(text: String) {
        val lower = text.lowercase(Locale.US).trim()
        val match = wakeRegex.find(lower)
        if (match == null) {
            listen(false)
            return
        }
        val rest = lower.substring(match.range.last + 1).trim().trimStart(',', '.', '!', ' ')
        if (rest.isNotEmpty()) {
            handleCommand(rest)
        } else {
            updateNotification("Yes? Listening for your command...")
            if (ttsReady) {
                tts?.speak("Yes bro?", TextToSpeech.QUEUE_FLUSH, null, "wake")
            } else {
                listen(true)
            }
        }
    }

    private fun handleCommand(text: String) {
        if (text.isBlank()) {
            listen(false)
            return
        }
        updateNotification("Thinking...")
        val appReply = tryOpenApp(this, text) ?: tryDeviceCommand(this, text)
        if (appReply != null) {
            speakReply(appReply)
            return
        }
        val key = getSharedPreferences("bro_prefs", Context.MODE_PRIVATE)
            .getString("api_key", "") ?: ""
        if (key.isBlank()) {
            speakReply("I need an API key set up in the app first.")
            return
        }
        Thread {
            val (_, reply) = callGemini(key, text)
            handler.post { speakReply(reply) }
        }.start()
    }

    private fun speakReply(reply: String) {
        if (ttsReady) {
            tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "reply")
        } else {
            updateNotification("Listening for \"Hey Bro\"...")
            listen(false)
        }
    }

    private fun callGemini(apiKey: String, prompt: String): Pair<Boolean, String> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent"
        val body = JSONObject().put(
            "contents",
            JSONArray().put(
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", prompt))
                )
            )
        ).toString()

        var lastError = "Unknown error"
        for (attempt in 1..3) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-goog-api-key", apiKey)
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                if (code == 200) {
                    val raw = conn.inputStream.bufferedReader().readText()
                    val text = JSONObject(raw)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                    return Pair(true, text.trim())
                }
                lastError = "Error code $code"
                if (code == 503 || code == 429) {
                    Thread.sleep(1500L * attempt)
                } else {
                    break
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Network error"
                Thread.sleep(1000L * attempt)
            }
        }
        return Pair(false, "Sorry bro, something went wrong: $lastError")
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

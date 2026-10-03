package com.bro.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

class BroBubbleService : Service() {

    companion object {
        @Volatile
        var running = false
    }

    private enum class S { IDLE, LISTENING, THINKING, SPEAKING }

    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var bubble: TextView? = null
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var state = S.IDLE

    override fun onCreate() {
        super.onCreate()
        running = true
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("Please allow Display over other apps in Bro")
            stopSelf()
            return
        }
        setupTts()
        createBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun toast(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    private fun buildNotification(): Notification {
        val channelId = "bro_bubble_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Bro bubble", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Bro is ready")
            .setContentText("Tap the purple bubble and talk")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun setupTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(0.75f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        handler.post { setState(S.IDLE) }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        handler.post { setState(S.IDLE) }
                    }
                })
                ttsReady = true
            }
        }
    }

    private fun circle(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun setState(s: S) {
        state = s
        val color = when (s) {
            S.IDLE -> 0xFF7C4DFF.toInt()
            S.LISTENING -> 0xFFE040FB.toInt()
            S.THINKING -> 0xFF536DFE.toInt()
            S.SPEAKING -> 0xFF00BFA5.toInt()
        }
        bubble?.background = circle(color)
    }

    private fun createBubble() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = windowManager
        val size = (64 * resources.displayMetrics.density).toInt()
        val view = TextView(this).apply {
            text = "B"
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = circle(0xFF7C4DFF.toInt())
        }
        val lp = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 20
        lp.y = 400

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    touchX = e.rawX
                    touchY = e.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    if (moved) {
                        lp.x = startX + dx
                        lp.y = startY + dy
                        windowManager.updateViewLayout(view, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onBubbleTap()
                    true
                }
                else -> false
            }
        }
        windowManager.addView(view, lp)
        bubble = view
    }

    private fun onBubbleTap() {
        when (state) {
            S.IDLE -> startListening()
            S.LISTENING -> {
                recognizer?.cancel()
                setState(S.IDLE)
            }
            S.SPEAKING -> {
                tts?.stop()
                setState(S.IDLE)
            }
            S.THINKING -> {}
        }
    }

    private fun startListening() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast("Open Bro and allow the microphone")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Speech recognition is not available on this phone")
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (state != S.LISTENING) return
                    setState(S.IDLE)
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> toast("Didn't catch that, tap and try again")
                        SpeechRecognizer.ERROR_CLIENT -> {}
                        else -> toast("Mic problem (code $error)")
                    }
                }
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    handleText(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        }
        setState(S.LISTENING)
        recognizer?.startListening(intent)
    }

    private fun handleText(text: String) {
        if (text.isBlank()) {
            setState(S.IDLE)
            return
        }
        setState(S.THINKING)
        val local = try {
            tryOpenApp(this, text) ?: tryDeviceCommand(this, text)
        } catch (e: Exception) {
            "That didn't work: ${e.message}"
        }
        if (local != null) {
            speak(local)
            return
        }
        val key = getSharedPreferences("bro_prefs", Context.MODE_PRIVATE)
            .getString("api_key", "") ?: ""
        if (key.isBlank()) {
            speak("Open Bro and save your key first.")
            return
        }
        val prompt = "Answer in at most three short sentences, plain text only, no markdown or lists. $text"
        Thread {
            val (_, reply) = callGemini(key, prompt)
            handler.post { speak(reply) }
        }.start()
    }

    private fun speak(reply: String) {
        val clean = reply.replace("*", "").replace("#", "")
        if (!ttsReady) {
            toast(clean)
            setState(S.IDLE)
            return
        }
        setState(S.SPEAKING)
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "bro_reply")
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
        running = false
        handler.removeCallbacksAndMessages(null)
        try {
            bubble?.let { wm?.removeView(it) }
        } catch (e: Exception) {
        }
        bubble = null
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

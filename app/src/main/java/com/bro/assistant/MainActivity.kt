package com.bro.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

enum class BroState { IDLE, LISTENING, THINKING, SPEAKING }

data class ChatMessage(val text: String, val fromUser: Boolean)

private val BgColor = Color(0xFF14091F)
private val Accent = Color(0xFF7C4DFF)
private val AccentLight = Color(0xFFB388FF)

class MainActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val broState = mutableStateOf(BroState.IDLE)
    private val messages = mutableStateListOf<ChatMessage>()
    private val apiKey = mutableStateOf("")
    private val teluguMode = mutableStateOf(false)
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("bro_prefs", Context.MODE_PRIVATE)
        apiKey.value = prefs.getString("api_key", "") ?: ""
        teluguMode.value = prefs.getBoolean("telugu", false)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(0.75f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        handler.post { broState.value = BroState.IDLE }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        handler.post { broState.value = BroState.IDLE }
                    }
                })
                ttsReady = true
            }
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    background = BgColor,
                    surface = BgColor,
                    onSurface = Color.White,
                    onBackground = Color.White
                )
            ) {
                BroScreen(prefs)
            }
        }
    }

    @Composable
    private fun BroScreen(prefs: android.content.SharedPreferences) {
        var keyInput by remember { mutableStateOf("") }
        var textInput by remember { mutableStateOf("") }
        val listState = rememberLazyListState()

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) startListening()
        }

        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
                .padding(16.dp)
        ) {
            if (apiKey.value.isEmpty()) {
                Text("Welcome to Bro", color = AccentLight, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Paste your Gemini API key below. It is saved only on this phone.",
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("Gemini API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val k = keyInput.trim()
                        if (k.isNotEmpty()) {
                            prefs.edit().putString("api_key", k).apply()
                            apiKey.value = k
                        }
                    },
                    modifier = Modifier.padding(top = 12.dp)
                ) { Text("Save key") }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (msg.fromUser) Accent else Color(0xFF2A1A3F),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(msg.text, color = Color.White)
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BroOrb(broState.value) {
                        when (broState.value) {
                            BroState.SPEAKING -> {
                                tts?.stop()
                                broState.value = BroState.IDLE
                            }
                            BroState.IDLE -> {
                                if (ContextCompatCheck()) startListening()
                                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                            else -> {}
                        }
                    }
                }

                Text(
                    when (broState.value) {
                        BroState.IDLE -> "Tap the orb and talk"
                        BroState.LISTENING -> "Listening..."
                        BroState.THINKING -> "Thinking..."
                        BroState.SPEAKING -> "Speaking... tap to stop"
                    },
                    color = AccentLight,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Or type a message") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val t = textInput.trim()
                            if (t.isNotEmpty() && broState.value == BroState.IDLE) {
                                textInput = ""
                                sendToBro(t)
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Text("Send") }
                }
            }
        }
    }

    private fun ContextCompatCheck(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            messages.add(ChatMessage("Speech recognition isn't available on this phone.", false))
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
                    broState.value = BroState.IDLE
                }
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (text.isNullOrBlank()) {
                        broState.value = BroState.IDLE
                    } else {
                        sendToBro(text)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (teluguMode.value) Locale("te", "IN") else Locale.getDefault())
        }
        broState.value = BroState.LISTENING
        recognizer?.startListening(intent)
    }

    private fun sendToBro(text: String) {
        messages.add(ChatMessage(text, true))
        broState.value = BroState.THINKING
        Thread {
            val (ok, reply) = callGemini(text)
            handler.post {
                messages.add(ChatMessage(reply, false))
                if (ok && ttsReady) {
                    broState.value = BroState.SPEAKING
                    tts?.setSpeechRate(1.0f)
                    tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "bro_reply")
                } else {
                    broState.value = BroState.IDLE
                }
            }
        }.start()
    }

    private fun callGemini(prompt: String): Pair<Boolean, String> {
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
                conn.setRequestProperty("x-goog-api-key", apiKey.value)
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
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun BroOrb(state: BroState, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "orb")
    val duration = when (state) {
        BroState.IDLE -> 2400
        BroState.LISTENING -> 600
        BroState.THINKING -> 1200
        BroState.SPEAKING -> 900
    }
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "spin"
    )
    val amp = when (state) {
        BroState.IDLE -> 0.06f
        BroState.LISTENING -> 0.18f
        BroState.THINKING -> 0.04f
        BroState.SPEAKING -> 0.12f
    }
    val scale = 1f + amp * pulse

    Canvas(
        modifier = Modifier
            .size(140.dp)
            .clickable { onClick() }
    ) {
        val r = size.minDimension / 2f * 0.6f * scale
        val glowAlpha = if (state == BroState.SPEAKING) 0.2f + 0.3f * pulse else 0.25f
        drawCircle(Accent.copy(alpha = glowAlpha), radius = r * 1.4f, center = center)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AccentLight, Accent),
                center = center,
                radius = r
            ),
            radius = r,
            center = center
        )
        if (state == BroState.THINKING) {
            val ring = r * 1.25f
            drawArc(
                color = AccentLight,
                startAngle = spin,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(center.x - ring, center.y - ring),
                size = Size(ring * 2, ring * 2),
                style = Stroke(width = 8f)
            )
        }
    }
}

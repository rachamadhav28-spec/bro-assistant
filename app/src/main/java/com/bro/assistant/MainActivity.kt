package com.bro.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class ChatMessage(val text: String, val fromUser: Boolean)

enum class BroState { IDLE, LISTENING, THINKING, SPEAKING }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BroApp()
        }
    }
}

fun getSavedApiKey(context: Context): String? {
    val prefs = context.getSharedPreferences("bro_prefs", Context.MODE_PRIVATE)
    return prefs.getString("gemini_api_key", null)
}

fun saveApiKey(context: Context, key: String) {
    val prefs = context.getSharedPreferences("bro_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString("gemini_api_key", key).apply()
}

@Composable
fun BroApp() {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(getSavedApiKey(context)) }

    if (apiKey.isNullOrBlank()) {
        ApiKeyScreen(onSaved = { key ->
            saveApiKey(context, key)
            apiKey = key
        })
    } else {
        BroScreen(apiKey = apiKey!!)
    }
}

@Composable
fun ApiKeyScreen(onSaved: (String) -> Unit) {
    var keyInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14091F))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Enter your Gemini API key",
            color = Color(0xFFB388FF),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF241536), RoundedCornerShape(12.dp)),
            placeholder = { Text("Paste your API key here", color = Color(0xFF8A7A9B)) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF241536),
                unfocusedContainerColor = Color(0xFF241536),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { if (keyInput.isNotBlank()) onSaved(keyInput.trim()) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
        ) {
            Text("Save and continue")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "This is stored only on your phone, never uploaded anywhere.",
            color = Color(0xFF8A7A9B),
            fontSize = 12.sp
        )
    }
}

suspend fun askGemini(apiKey: String, userMessage: String): String {
    return withContext(Dispatchers.IO) {
        var attempts = 0
        var lastError = "Unknown error"

        while (attempts < 3) {
            attempts++
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key=$apiKey")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val requestBody = JSONObject().apply {
                    put("contents", JSONArray().put(
                        JSONObject().put("parts", JSONArray().put(
                            JSONObject().put("text", userMessage)
                        ))
                    ))
                }

                connection.outputStream.use { it.write(requestBody.toString().toByteArray()) }

                val responseCode = connection.responseCode
                if (responseCode == 503 || responseCode == 429) {
                    lastError = "Server busy (code $responseCode)"
                    kotlinx.coroutines.delay(1500L * attempts)
                    continue
                }
                if (responseCode != 200) {
                    val errorText = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    return@withContext "Error ($responseCode): $errorText"
                }

                val responseText = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                val candidates = json.getJSONArray("candidates")
                val content = candidates.getJSONObject(0).getJSONObject("content")
                val parts = content.getJSONArray("parts")
                return@withContext parts.getJSONObject(0).getString("text")
            } catch (e: Exception) {
                lastError = "Something went wrong: ${e.message}"
            }
        }
        "Bro couldn't reach the server after a few tries. ($lastError)"
    }
}

@Composable
fun BroOrb(state: BroState, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    val pulseDuration = when (state) {
        BroState.IDLE -> 2200
        BroState.LISTENING -> 700
        BroState.THINKING -> 500
        BroState.SPEAKING -> 350
    }

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == BroState.THINKING) 1200 else 6000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    val coreColor by animateColorAsState(
        targetValue = when (state) {
            BroState.IDLE -> Color(0xFF7C4DFF)
            BroState.LISTENING -> Color(0xFFB388FF)
            BroState.THINKING -> Color(0xFF9C6DFF)
            BroState.SPEAKING -> Color(0xFFD1B3FF)
        },
        label = "color"
    )

    Box(
        modifier = modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = size.minDimension / 2

            rotate(degrees = rotationAngle, pivot = center) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(coreColor.copy(alpha = 0.35f), Color.Transparent),
                        center = center,
                        radius = maxRadius
                    ),
                    radius = maxRadius * scale,
                    center = center
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreColor, coreColor.copy(alpha = 0.6f)),
                    center = center,
                    radius = maxRadius * 0.55f
                ),
                radius = (maxRadius * 0.5f) * scale,
                center = center
            )
        }
    }
}

@Composable
fun BroScreen(apiKey: String) {
    val context = LocalContext.current
    val messages = remember {
        mutableStateListOf(
            ChatMessage("Bro is online. Ask me anything.", fromUser = false)
        )
    }
    var input by remember { mutableStateOf("") }
    var broState by remember { mutableStateOf(BroState.IDLE) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.value?.language = Locale.US
                tts.value?.setSpeechRate(1.0f)
                tts.value?.setPitch(0.9f)
            }
        }
        tts.value = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    LaunchedEffect(tts.value) {
        tts.value?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                broState = BroState.SPEAKING
            }
            override fun onDone(utteranceId: String?) {
                broState = BroState.IDLE
            }
            override fun onError(utteranceId: String?) {
                broState = BroState.IDLE
            }
        })
    }

    fun speak(text: String) {
        tts.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bro_reply")
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return
        messages.add(ChatMessage(userText, fromUser = true))
        broState = BroState.THINKING
        scope.launch {
            listState.animateScrollToItem(messages.size)
            val reply = askGemini(apiKey, userText)
            messages.add(ChatMessage(reply, fromUser = false))
            listState.animateScrollToItem(messages.size - 1)
            speak(reply)
        }
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(Unit) {
        onDispose { speechRecognizer?.destroy() }
    }

    fun startListening() {
        if (speechRecognizer == null) {
            Toast.makeText(context, "Voice input not available on this device", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
        }
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { broState = BroState.LISTENING }
            override fun onBeginningOfSpeech() { broState = BroState.LISTENING }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { broState = BroState.THINKING }
            override fun onError(error: Int) {
                broState = BroState.IDLE
            }
            override fun onResults(results: Bundle?) {
                val spokenText = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!spokenText.isNullOrBlank()) {
                    sendMessage(spokenText)
                } else {
                    broState = BroState.IDLE
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer.startListening(intent)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening()
        else Toast.makeText(context, "Microphone permission is needed for voice input", Toast.LENGTH_SHORT).show()
    }

    fun onMicTapped() {
        if (broState == BroState.LISTENING) {
            speechRecognizer?.stopListening()
            broState = BroState.IDLE
            return
        }
        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14091F))
    ) {
        Text(
            text = "Bro",
            color = Color(0xFFB388FF),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            BroOrb(
                state = broState,
                modifier = Modifier.clickable { onMicTapped() }
            )
        }

        Text(
            text = when (broState) {
                BroState.IDLE -> "Tap the orb to speak"
                BroState.LISTENING -> "Listening..."
                BroState.THINKING -> "Thinking..."
                BroState.SPEAKING -> "Speaking..."
            },
            color = Color(0xFF8A7A9B),
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            textAlign = TextAlign.Center
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                ChatBubble(msg)
            }
            if (broState == BroState.THINKING) {
                item {
                    ChatBubble(ChatMessage("Bro is thinking...", fromUser = false))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF241536), RoundedCornerShape(24.dp)),
                placeholder = { Text("Message Bro...", color = Color(0xFF8A7A9B)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF241536),
                    unfocusedContainerColor = Color(0xFF241536),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    val text = input
                    input = ""
                    sendMessage(text)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val bubbleColor = if (message.fromUser) Color(0xFF7C4DFF) else Color(0xFF241536)
    val alignment = if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .background(bubbleColor, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(text = message.text, color = Color.White, fontSize = 15.sp)
        }
    }
}

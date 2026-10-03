package com.bro.assistant

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(val text: String, val fromUser: Boolean)

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
fun BroScreen(apiKey: String) {
    val messages = remember {
        mutableStateListOf(
            ChatMessage("Bro is online. Ask me anything.", fromUser = false)
        )
    }
    var input by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

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
            if (isThinking) {
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
                    if (input.isNotBlank() && !isThinking) {
                        val userText = input
                        messages.add(ChatMessage(userText, fromUser = true))
                        input = ""
                        isThinking = true
                        scope.launch {
                            listState.animateScrollToItem(messages.size)
                            val reply = askGemini(apiKey, userText)
                            isThinking = false
                            messages.add(ChatMessage(reply, fromUser = false))
                            listState.animateScrollToItem(messages.size - 1)
                        }
                    }
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

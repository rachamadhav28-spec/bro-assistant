package com.bro.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private val BgColor = Color(0xFF14091F)
private val Accent = Color(0xFF7C4DFF)
private val AccentLight = Color(0xFFB388FF)

class MainActivity : ComponentActivity() {

    private val apiKey = mutableStateOf("")
    private val micOk = mutableStateOf(false)
    private val overlayOk = mutableStateOf(false)
    private val running = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("bro_prefs", Context.MODE_PRIVATE)
        refresh(prefs)
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
                SetupScreen(prefs)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh(getSharedPreferences("bro_prefs", Context.MODE_PRIVATE))
    }

    private fun refresh(prefs: SharedPreferences) {
        apiKey.value = prefs.getString("api_key", "") ?: ""
        micOk.value = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        overlayOk.value = Settings.canDrawOverlays(this)
        running.value = BroBubbleService.running
    }

    @Composable
    private fun SetupScreen(prefs: SharedPreferences) {
        var keyInput by remember { mutableStateOf("") }

        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { refresh(prefs) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("Bro", color = AccentLight, style = MaterialTheme.typography.headlineLarge)

            if (apiKey.value.isEmpty()) {
                Text(
                    "Paste your Gemini API key. It is saved only on this phone.",
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
                Text(
                    "Microphone: " + if (micOk.value) "ready" else "needs permission",
                    color = Color.White,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    "Display over other apps: " + if (overlayOk.value) "ready" else "needs permission",
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                if (!micOk.value) {
                    Button(
                        onClick = {
                            val perms = if (Build.VERSION.SDK_INT >= 33) {
                                arrayOf(
                                    Manifest.permission.RECORD_AUDIO,
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            } else {
                                arrayOf(Manifest.permission.RECORD_AUDIO)
                            }
                            permLauncher.launch(perms)
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) { Text("1. Allow microphone") }
                }

                if (!overlayOk.value) {
                    Button(
                        onClick = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) { Text("2. Allow display over other apps") }
                }

                if (micOk.value && overlayOk.value) {
                    if (!running.value) {
                        Button(
                            onClick = {
                                ContextCompat.startForegroundService(
                                    this@MainActivity,
                                    Intent(this@MainActivity, BroBubbleService::class.java)
                                )
                                running.value = true
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) { Text("Start Bro bubble") }
                    } else {
                        Text(
                            "Bro bubble is ON. Go to any app, tap the purple bubble, then talk.",
                            color = AccentLight,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Button(
                            onClick = {
                                stopService(Intent(this@MainActivity, BroBubbleService::class.java))
                                running.value = false
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) { Text("Stop Bro bubble") }
                    }
                }

                Button(
                    onClick = {
                        prefs.edit().remove("api_key").apply()
                        apiKey.value = ""
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) { Text("Change API key") }
            }
        }
    }
}

package com.bro.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BroScreen()
        }
    }
}

@Composable
fun BroScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14091F)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Bro is online",
            color = Color(0xFFB388FF),
            fontSize = 24.sp
        )
    }
}

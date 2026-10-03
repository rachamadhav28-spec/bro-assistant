package com.bro.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class BroAccessibilityService : AccessibilityService() {

    companion object {
        var instance: BroAccessibilityService? = null
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun goBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun swipe(startFraction: Float, endFraction: Float, durationMs: Long) {
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val startY = dm.heightPixels * startFraction
        val endY = dm.heightPixels * endFraction
        val path = Path()
        path.moveTo(x, startY)
        path.lineTo(x, endY)
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun scrollDown() {
        swipe(0.70f, 0.30f, 350)
    }

    fun scrollUp() {
        swipe(0.30f, 0.70f, 350)
    }

    fun closeCurrentApp() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        handler.postDelayed({
            swipe(0.55f, 0.10f, 200)
            handler.postDelayed({
                performGlobalAction(GLOBAL_ACTION_HOME)
            }, 600)
        }, 900)
    }
}

fun tryAccessibilityCommand(context: Context, spoken: String): String? {
    val text = spoken.lowercase().trim().removeSuffix(".")

    val isScrollDown = text.contains("scroll") && text.contains("down")
    val isScrollUp = text.contains("scroll") && text.contains("up")
    val isBack = text == "go back" || text == "back"
    val isHome = text == "go home" || text == "home" || text.contains("home screen")
    val isClose = text.startsWith("close")

    if (!isScrollDown && !isScrollUp && !isBack && !isHome && !isClose) return null

    val service = BroAccessibilityService.instance
        ?: return "Please turn on Bro in Accessibility settings first."

    return when {
        isScrollDown -> { service.scrollDown(); "Scrolling down" }
        isScrollUp -> { service.scrollUp(); "Scrolling up" }
        isBack -> { service.goBack(); "Going back" }
        isHome -> { service.goHome(); "Going home" }
        else -> { service.closeCurrentApp(); "Closing the app" }
    }
}

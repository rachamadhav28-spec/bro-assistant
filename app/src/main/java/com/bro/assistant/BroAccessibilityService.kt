package com.bro.assistant
 
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
 
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
 
    // ---------- Quick Settings tile toggling ----------
 
    fun toggleTile(names: List<String>, wantOn: Boolean?, fallback: Intent?) {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        attemptTile(names, wantOn, fallback, 0)
    }
 
    private fun attemptTile(names: List<String>, wantOn: Boolean?, fallback: Intent?, attempt: Int) {
        val delay = if (attempt == 0) 1200L else 700L
        handler.postDelayed({
            val done = tryClickTile(names, wantOn)
            if (done) {
                handler.postDelayed({ closeShade() }, 600)
            } else if (attempt < 3) {
                attemptTile(names, wantOn, fallback, attempt + 1)
            } else {
                closeShade()
                if (fallback != null) {
                    try {
                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(fallback)
                    } catch (e: Exception) {
                    }
                }
            }
        }, delay)
    }
 
    private fun closeShade() {
        if (Build.VERSION.SDK_INT >= 31) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }
 
    private fun tryClickTile(names: List<String>, wantOn: Boolean?): Boolean {
        val roots = ArrayList<AccessibilityNodeInfo>()
        try {
            for (w in windows) {
                val r = w.root
                if (r != null) roots.add(r)
            }
        } catch (e: Exception) {
        }
        val active = rootInActiveWindow
        if (active != null) roots.add(active)
 
        for (root in roots) {
            val node = findTileNode(root, names, 0) ?: continue
            val state = readTileState(node)
            if (wantOn != null && state != null && state == wantOn) {
                return true
            }
            val clickable = findClickableParent(node) ?: continue
            if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
        }
        return false
    }
 
    private fun findTileNode(
        node: AccessibilityNodeInfo,
        names: List<String>,
        depth: Int
    ): AccessibilityNodeInfo? {
        if (depth > 30) return null
        val label = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
        val lower = label.lowercase().trim()
        val matches = names.any { n ->
            if (n.startsWith("^")) lower.startsWith(n.substring(1)) else lower.contains(n)
        }
        if (lower.isNotEmpty() && lower.length <= 40 && matches) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findTileNode(child, names, depth + 1)
            if (found != null) return found
        }
        return null
    }
 
    private fun findClickableParent(start: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = start
        var depth = 0
        while (n != null && depth < 5) {
            if (n.isClickable) return n
            n = n.parent
            depth++
        }
        return null
    }
 
    private fun readTileState(start: AccessibilityNodeInfo): Boolean? {
        var n: AccessibilityNodeInfo? = start
        var depth = 0
        while (n != null && depth < 4) {
            if (n.isCheckable) return n.isChecked
            val desc = StringBuilder()
            if (Build.VERSION.SDK_INT >= 30) {
                val sd = n.stateDescription
                if (sd != null) desc.append(sd).append(' ')
            }
            val cd = n.contentDescription
            if (cd != null) desc.append(cd).append(' ')
            val tx = n.text
            if (tx != null) desc.append(tx)
            val parts = desc.toString().lowercase().split(Regex("[^a-z]+"))
            if (parts.contains("off") || parts.contains("disabled")) return false
            if (parts.contains("on") || parts.contains("enabled")) return true
            n = n.parent
            depth++
        }
        return null
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
 

package com.bro.assistant

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo

private val igHandler = Handler(Looper.getMainLooper())

private fun openInstagramThen(context: Context, action: () -> Unit) {
    val pm = context.packageManager
    val launchIntent = pm.getLaunchIntentForPackage("com.instagram.android")
    if (launchIntent == null) {
        return
    }
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(launchIntent)
    igHandler.postDelayed({ action() }, 1500)
}

private fun isInstagramInForeground(service: BroAccessibilityService): Boolean {
    val root = service.rootInActiveWindow
    return root?.packageName == "com.instagram.android"
}

private fun findNodeByDesc(
    node: AccessibilityNodeInfo?,
    keywords: List<String>,
    depth: Int = 0
): AccessibilityNodeInfo? {
    if (node == null || depth > 25) return null
    val desc = (node.contentDescription?.toString() ?: "").lowercase()
    if (desc.isNotEmpty() && keywords.any { desc.contains(it) }) {
        return node
    }
    for (i in 0 until node.childCount) {
        val found = findNodeByDesc(node.getChild(i), keywords, depth + 1)
        if (found != null) return found
    }
    return null
}

private fun tapNodeByDesc(service: BroAccessibilityService, keywords: List<String>): Boolean {
    val root = service.rootInActiveWindow ?: return false
    val node = findNodeByDesc(root, keywords) ?: return false
    var n: AccessibilityNodeInfo? = node
    var depth = 0
    while (n != null && depth < 5) {
        if (n.isClickable) {
            return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        n = n.parent
        depth++
    }
    return false
}

fun tryInstagramCommand(context: Context, spoken: String): String? {
    val text = spoken.lowercase().trim().removeSuffix(".")
    if (!text.contains("instagram") && !text.contains("insta")) return null

    val service = BroAccessibilityService.instance
        ?: return "Please turn on Bro in Accessibility settings first."

    val goHome = text.contains("home")
    val goProfile = text.contains("profile") || text.contains("my page")
    val goMessages = text.contains("message") || text.contains("dm") || text.contains("inbox")
    val goSearch = text.contains("search") || text.contains("explore")

    if (!goHome && !goProfile && !goMessages && !goSearch) return null

    val keywords = when {
        goProfile -> listOf("profile")
        goMessages -> listOf("direct", "messages", "inbox")
        goSearch -> listOf("search", "explore")
        else -> listOf("home")
    }
    val label = when {
        goProfile -> "your profile"
        goMessages -> "Messages"
        goSearch -> "Search"
        else -> "Home"
    }

    if (isInstagramInForeground(service)) {
        val ok = tapNodeByDesc(service, keywords)
        return if (ok) "Opening $label" else "I couldn't find that in Instagram right now"
    } else {
        openInstagramThen(context) {
            tapNodeByDesc(service, keywords)
        }
        return "Opening Instagram, then $label"
    }
}

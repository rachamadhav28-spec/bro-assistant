package com.bro.assistant

import android.content.Context
import android.content.Intent

private fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

fun tryOpenApp(context: Context, spoken: String): String? {
    val lower = spoken.lowercase().trim()
    val match = Regex("^(please\\s+)?(open|launch|start)\\s+(the\\s+)?(.+)$").find(lower) ?: return null
    val verb = match.groupValues[2]
    val wanted = match.groupValues[4]
        .replace(Regex("\\s+app$"), "")
        .trim()
        .trimEnd('.', '!', '?')
    if (wanted.isEmpty()) return null

    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val apps = pm.queryIntentActivities(launcherIntent, 0).map {
        Pair(it.loadLabel(pm).toString(), it.activityInfo.packageName)
    }

    val w = norm(wanted)
    if (w.isEmpty()) return null
    val found = apps.firstOrNull { norm(it.first) == w }
        ?: apps.firstOrNull { norm(it.first).startsWith(w) }
        ?: apps.firstOrNull { norm(it.first).contains(w) }
        ?: apps.firstOrNull { norm(it.first).length >= 3 && w.contains(norm(it.first)) }

    if (found == null) {
        return if (verb == "start") null else "I couldn't find an app called $wanted"
    }
    val launch = pm.getLaunchIntentForPackage(found.second)
        ?: return "I couldn't open ${found.first}"
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(launch)
    return "Opening ${found.first}"
}

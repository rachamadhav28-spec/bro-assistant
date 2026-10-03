package com.bro.assistant

import android.content.Context
import android.content.Intent

fun tryOpenApp(context: Context, spoken: String): String? {
    val lower = spoken.lowercase().trim()
    val prefixes = listOf("open ", "launch ", "start ")
    val prefix = prefixes.firstOrNull { lower.startsWith(it) } ?: return null
    val name = lower.removePrefix(prefix).trim().removePrefix("the ").trim()
    if (name.isEmpty()) return null

    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val apps = pm.queryIntentActivities(launcherIntent, 0)

    val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == name }
        ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(name) }
        ?: return "I couldn't find an app called $name."

    val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
        ?: return "I can't open $name."
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(launch)
    return "Opening ${match.loadLabel(pm)}."
}

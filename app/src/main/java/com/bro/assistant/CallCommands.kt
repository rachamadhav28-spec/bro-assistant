package com.bro.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract

fun tryCallCommand(context: Context, spoken: String): String? {
    val text = spoken.lowercase().trim().removeSuffix(".")
    val prefix = when {
        text.startsWith("call ") -> "call "
        text.startsWith("phone ") -> "phone "
        else -> return null
    }
    val name = text.removePrefix(prefix).trim()
    if (name.isEmpty()) return null

    val hasContacts = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED
    val hasCall = context.checkSelfPermission(Manifest.permission.CALL_PHONE) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasContacts || !hasCall) {
        val permIntent = Intent(context, PermissionActivity::class.java)
        permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(permIntent)
        return "Please allow contacts and phone permission, then ask again"
    }

    val phone = ContactsContract.CommonDataKinds.Phone
    val cursor = context.contentResolver.query(
        phone.CONTENT_URI,
        arrayOf(phone.DISPLAY_NAME, phone.NUMBER),
        phone.DISPLAY_NAME + " LIKE ?",
        arrayOf("%$name%"),
        null
    )
    val result: Pair<String?, String?>? = cursor?.use {
        if (it.moveToFirst()) Pair(it.getString(0), it.getString(1)) else null
    }
    val foundName = result?.first
    val number = result?.second
    if (foundName == null || number == null) {
        return "I could not find $name in your contacts"
    }

    val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)))
    callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(callIntent)
    return "Calling $foundName"
}

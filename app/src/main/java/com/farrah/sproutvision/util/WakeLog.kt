package com.farrah.sproutvision.util

import android.content.Context
import java.io.File

/**
 * Wake-path diagnostics. App Logcat output never reaches the device log
 * on this headset build, so lifecycle/recreate/tick markers go to a
 * private file instead — pull with:
 * adb shell run-as com.farrah.sproutvision.quest cat files/wake.log
 * Lifecycle lines only (no keys, no PII), capped at ~200 KB.
 */
object WakeLog {
    private const val NAME = "wake.log"
    private const val MAX_BYTES = 200_000L

    @Synchronized
    fun log(context: Context, msg: String) {
        try {
            val file = File(context.filesDir, NAME)
            if (file.length() > MAX_BYTES) file.delete()
            file.appendText("${System.currentTimeMillis()} $msg\n")
        } catch (_: Exception) {
            // Diagnostics must never break the app.
        }
    }
}

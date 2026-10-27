package com.farrah.sproutvision.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Builds the one-line spoken verdict for a completed scan: variety and day
 * count first (as shown on the verdict card), then the verdict frame. Kept
 * under 20 words by construction: the fixed frame is 9 words and the top
 * issue is truncated to its first 8 words. Spoken string only — the card
 * UI is unchanged.
 */
fun verdictUtterance(variety: String, day: Int, healthScore: Int, topIssue: String?): String {
    val issue = topIssue
        ?.split(Regex("\\s+"))
        ?.filter { it.isNotBlank() }
        ?.take(8)
        ?.joinToString(" ")
        .orEmpty()
    val lead = "$variety, day $day — "
    return if (issue.isBlank()) {
        lead + "Tray health $healthScore. No issues spotted."
    } else {
        lead + "Tray health $healthScore. Top issue: $issue."
    }
}

/**
 * Fire-and-forget TextToSpeech for the post-scan verdict. Lazily binds the
 * engine on first use (application context — safe to call from any panel)
 * and silently no-ops when the device has no TTS engine, so a missing
 * engine can never break the scan flow.
 */
object VoiceReadout {
    private const val TAG = "VoiceReadout"

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var initializing = false
    private val lock = Any()
    private var pendingText: String? = null

    fun speak(context: Context, text: String) {
        if (text.isBlank()) return
        try {
            val engine = tts
            if (engine != null) {
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sprout-verdict")
                return
            }
            synchronized(lock) {
                pendingText = text
                if (initializing) return
                initializing = true
            }
            val app = context.applicationContext
            tts = TextToSpeech(app) { status ->
                synchronized(lock) { initializing = false }
                if (status != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "TTS init failed: $status")
                    return@TextToSpeech
                }
                try {
                    val engine = tts ?: return@TextToSpeech
                    val lang = engine.setLanguage(Locale.US)
                    if (lang == TextToSpeech.LANG_MISSING_DATA ||
                        lang == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        Log.w(TAG, "US English TTS voice missing; using default locale")
                        engine.language = Locale.getDefault()
                    }
                    val textToSpeak = synchronized(lock) {
                        pendingText.also { pendingText = null }
                    }
                    if (!textToSpeak.isNullOrBlank()) {
                        engine.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "sprout-verdict")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "TTS speak failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TTS unavailable: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        synchronized(lock) {
            initializing = false
            pendingText = null
        }
    }
}

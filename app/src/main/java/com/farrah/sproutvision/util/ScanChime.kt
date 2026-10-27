package com.farrah.sproutvision.util

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

/**
 * Short, soft confirmation chime played the moment a scan completes, just
 * before the TTS verdict speaks. Uses a system tone via [ToneGenerator] —
 * no audio file to bundle, no permissions needed.
 *
 * One lazily-created generator is reused for the app's lifetime (creating
 * one per scan would leak native audio resources); [shutdown] releases it.
 * Everything is best-effort: a missing audio path must never break scans.
 */
object ScanChime {
    private const val TAG = "ScanChime"

    @Volatile private var tone: ToneGenerator? = null
    private val lock = Any()

    fun play() {
        try {
            val tg = tone ?: synchronized(lock) {
                tone ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, CHIME_VOLUME)
                    .also { tone = it }
            }
            tg.startTone(ToneGenerator.TONE_PROP_ACK, CHIME_DURATION_MS)
        } catch (e: Exception) {
            Log.w(TAG, "Chime failed: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            synchronized(lock) {
                tone?.release()
                tone = null
            }
        } catch (_: Exception) {
        }
    }

    /** 0–100 scale: soft confirmation, not an alarm. */
    private const val CHIME_VOLUME = 60
    private const val CHIME_DURATION_MS = 150
}

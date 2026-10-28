package com.farrah.sproutvision.settings

import java.time.Instant
import java.time.ZoneId

/** Default daily detection cap: a heavy real day with headroom, one tap to raise. */
const val DEFAULT_DAILY_DETECTION_CAP = 25
const val MIN_DAILY_DETECTION_CAP = 1
const val MAX_DAILY_DETECTION_CAP = 999

/**
 * Cost-controls core: detections-today counter + spend guard. A
 * detection is one scan admitted to run Meta SAM (the metered path);
 * demo, Gemini, no-key, kill-switch-off, and already-capped scans never
 * count. Pure so the money rules stay unit-tested; SettingsStore owns
 * persistence, the ViewModel owns admission.
 */
fun todayString(
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): String = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toString()

fun clampCap(cap: Int): Int = cap.coerceIn(MIN_DAILY_DETECTION_CAP, MAX_DAILY_DETECTION_CAP)

/** Count that applies today: the stored count, or zero once its day has passed. */
fun countForToday(storedDay: String, storedCount: Int, today: String): Int =
    if (storedDay == today) storedCount.coerceAtLeast(0) else 0

/** SAM runs while today's count is under the (clamped) cap. */
fun samAllowed(countToday: Int, cap: Int): Boolean = countToday < clampCap(cap)

/** Next stored (day, count) after recording one detection. */
fun recordTransition(storedDay: String, storedCount: Int, today: String): Pair<String, Int> =
    if (storedDay == today) today to storedCount.coerceAtLeast(0) + 1 else today to 1

/**
 * Idempotency: each scan stamp counts once per process. Scans don't
 * survive restart (pending state is memory-only), so the stamp lives in
 * memory too — a new process means genuinely new scans.
 */
fun shouldRecordDetection(lastStamp: Long?, stamp: Long): Boolean = lastStamp != stamp

/** Settings row: "12 / 25 detections today", with the reached suffix at cap. */
fun detectionDisplay(countToday: Int, cap: Int): String {
    val clamped = clampCap(cap)
    return "$countToday / $clamped detections today" +
        if (countToday >= clamped) " — daily limit reached" else ""
}

/** SAM-unavailable reason detail carried by the existing failure channels. */
fun limitReachedNote(cap: Int): String =
    "daily detection limit reached, ${clampCap(cap)}/day — raise it in Settings"

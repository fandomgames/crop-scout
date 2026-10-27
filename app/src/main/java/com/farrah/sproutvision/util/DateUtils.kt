package com.farrah.sproutvision.util

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

const val MILLIS_PER_DAY = 86_400_000L

fun formatDate(epochMs: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(epochMs))

/** Sortable timestamp for CSV export ("2026-10-16 14:05"). */
fun formatDateTime(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMs))

/**
 * Day number of [atMs] relative to a [seededAt] timestamp (seed day =
 * Day 0), from calendar dates only: time of day is ignored, so the
 * number flips at local midnight and is identical morning to night.
 * DST-safe by construction (a 23 h or 25 h day is still one date).
 */
fun dayOn(
    seededAt: Long,
    atMs: Long,
    zone: ZoneId = ZoneId.systemDefault()
): Int =
    ChronoUnit.DAYS.between(
        Instant.ofEpochMilli(seededAt).atZone(zone).toLocalDate(),
        Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate()
    ).toInt().coerceAtLeast(0)

fun startOfDay(epochMs: Long): Long = (epochMs / MILLIS_PER_DAY) * MILLIS_PER_DAY

fun daysUntil(futureEpochMs: Long): Int {
    val now = startOfDay(System.currentTimeMillis())
    return ((startOfDay(futureEpochMs) - now) / MILLIS_PER_DAY).toInt()
}

/**
 * Calendar days elapsed since [epochMs] (seed day = Day 0), clamped
 * at 0. Used for every "Day N" readout (HUD, cards, Manage, history
 * export, voice). The explicit [nowMs]/[zone] exist for tests;
 * callers keep the live defaults.
 */
fun daysSince(
    epochMs: Long,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): Int = dayOn(epochMs, nowMs, zone)

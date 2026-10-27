package com.farrah.sproutvision.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.farrah.sproutvision.data.GrowIntent
import com.farrah.sproutvision.data.GrowthStage
import com.farrah.sproutvision.data.SproutDatabase
import com.farrah.sproutvision.settings.SettingsStore
import com.farrah.sproutvision.util.MILLIS_PER_DAY
import com.farrah.sproutvision.util.daysUntil
import kotlinx.coroutines.flow.first

/**
 * Runs once a day: posts a harvest notification for trays whose harvest window
 * is open (or opens within 2 days) and a daily watering/canopy check nudge.
 */
class DailyCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = SettingsStore(context).settings.first()
        if (!settings.notificationsEnabled) return Result.success()

        val trays = SproutDatabase.getInstance(context).dao().getTraysOnce()
        val active = trays.filter { it.currentStage != GrowthStage.HARVEST }

        active.forEach { tray ->
            // Grow-out trays have no microgreen harvest window — skip the
            // countdown warnings (they still get the daily check nudge).
            if (tray.growIntent == GrowIntent.GROW_OUT) return@forEach
            // Trays without expected days skip the countdown warnings
            // (they still get the daily check nudge below).
            val expected = tray.expectedDaysToHarvest ?: return@forEach
            val harvestAt = tray.seededAtEpochMs + expected * MILLIS_PER_DAY
            val daysLeft = daysUntil(harvestAt)
            val id = (tray.id % Int.MAX_VALUE).toInt()
            when {
                daysLeft <= 0 -> NotificationHelper.notify(
                    context, id,
                    "Harvest time: ${tray.name}",
                    "${tray.variety} is ready to cut."
                )
                daysLeft <= 2 -> NotificationHelper.notify(
                    context, id,
                    "Harvest window approaching: ${tray.name}",
                    "${tray.variety} should be ready in ~$daysLeft day(s)."
                )
            }
        }

        if (active.isNotEmpty()) {
            NotificationHelper.notify(
                context, 9001,
                "Daily tray check",
                "Time to check water levels and canopy on your trays."
            )
        }
        return Result.success()
    }
}

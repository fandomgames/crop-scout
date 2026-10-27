package com.farrah.sproutvision

import android.app.Application
import com.farrah.sproutvision.reminders.NotificationHelper
import com.farrah.sproutvision.reminders.ReminderScheduler

class SproutVisionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        ReminderScheduler.setDailyRemindersEnabled(this, enabled = true)
    }
}

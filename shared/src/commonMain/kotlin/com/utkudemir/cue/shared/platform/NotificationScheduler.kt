package com.utkudemir.cue.shared.platform

import com.utkudemir.cue.shared.domain.model.ScheduledReminder

interface NotificationScheduler {
    suspend fun requestPermissionIfNeeded(): Boolean
    suspend fun areNotificationsEnabled(): Boolean
    suspend fun schedule(reminders: List<ScheduledReminder>)
    suspend fun cancelAll()
}

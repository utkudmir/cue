package app.debridhub.shared.platform

import app.debridhub.shared.domain.model.ScheduledReminder

interface NotificationScheduler {
    suspend fun requestPermissionIfNeeded(): Boolean
    suspend fun areNotificationsEnabled(): Boolean
    suspend fun schedule(reminders: List<ScheduledReminder>)
    suspend fun cancelAll()
}

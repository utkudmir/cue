package app.debridhub.shared.domain.repository

import app.debridhub.shared.domain.model.AccountStatus
import app.debridhub.shared.domain.model.ReminderConfig
import app.debridhub.shared.domain.model.ScheduledReminder

interface ReminderRepository {
    suspend fun getConfig(): ReminderConfig
    suspend fun updateConfig(config: ReminderConfig)
    suspend fun previewReminders(accountStatus: AccountStatus): List<ScheduledReminder>
    suspend fun scheduleReminders(accountStatus: AccountStatus): List<ScheduledReminder>
    suspend fun cancelReminders()
}

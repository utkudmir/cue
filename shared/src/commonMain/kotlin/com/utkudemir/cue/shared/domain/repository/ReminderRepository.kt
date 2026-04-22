package com.utkudemir.cue.shared.domain.repository

import com.utkudemir.cue.shared.domain.model.AccountStatus
import com.utkudemir.cue.shared.domain.model.ReminderConfig
import com.utkudemir.cue.shared.domain.model.ScheduledReminder

interface ReminderRepository {
    suspend fun getConfig(): ReminderConfig
    suspend fun updateConfig(config: ReminderConfig)
    suspend fun previewReminders(accountStatus: AccountStatus): List<ScheduledReminder>
    suspend fun scheduleReminders(accountStatus: AccountStatus): List<ScheduledReminder>
    suspend fun cancelReminders()
}

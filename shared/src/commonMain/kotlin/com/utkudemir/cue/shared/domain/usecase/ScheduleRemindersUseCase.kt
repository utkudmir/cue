package com.utkudemir.cue.shared.domain.usecase

import com.utkudemir.cue.shared.domain.model.ScheduledReminder
import com.utkudemir.cue.shared.domain.repository.AccountRepository
import com.utkudemir.cue.shared.domain.repository.ReminderRepository

class ScheduleRemindersUseCase(
    private val accountRepository: AccountRepository,
    private val reminderRepository: ReminderRepository
) {
    suspend operator fun invoke(): Result<List<ScheduledReminder>> {
        return accountRepository.refreshAccountStatus().mapCatching { status ->
            reminderRepository.scheduleReminders(status)
        }
    }
}

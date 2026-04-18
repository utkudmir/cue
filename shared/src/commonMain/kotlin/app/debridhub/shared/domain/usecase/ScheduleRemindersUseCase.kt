package app.debridhub.shared.domain.usecase

import app.debridhub.shared.domain.model.ScheduledReminder
import app.debridhub.shared.domain.repository.AccountRepository
import app.debridhub.shared.domain.repository.ReminderRepository

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

package app.debridhub.shared.data.repository

import app.debridhub.shared.domain.model.AccountStatus
import app.debridhub.shared.domain.model.ReminderConfig
import app.debridhub.shared.domain.model.ScheduledReminder
import app.debridhub.shared.domain.repository.ReminderRepository
import app.debridhub.shared.platform.NotificationScheduler
import app.debridhub.shared.platform.ReminderConfigStore
import app.debridhub.shared.reminders.ReminderPlanner
import kotlinx.datetime.Clock

class ReminderRepositoryImpl(
    private val configStore: ReminderConfigStore,
    private val planner: ReminderPlanner,
    private val notificationScheduler: NotificationScheduler
) : ReminderRepository {
    override suspend fun getConfig(): ReminderConfig = configStore.read()

    override suspend fun updateConfig(config: ReminderConfig) {
        configStore.write(config)
    }

    override suspend fun previewReminders(accountStatus: AccountStatus): List<ScheduledReminder> =
        planner.planReminders(
            now = Clock.System.now(),
            accountStatus = accountStatus,
            config = configStore.read()
        )

    override suspend fun scheduleReminders(accountStatus: AccountStatus): List<ScheduledReminder> {
        val planned = previewReminders(accountStatus)
        notificationScheduler.schedule(planned)
        return planned
    }

    override suspend fun cancelReminders() {
        notificationScheduler.cancelAll()
    }
}

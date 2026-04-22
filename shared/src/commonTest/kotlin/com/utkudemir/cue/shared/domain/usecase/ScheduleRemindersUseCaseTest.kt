package com.utkudemir.cue.shared.domain.usecase

import com.utkudemir.cue.shared.domain.model.AccountStatus
import com.utkudemir.cue.shared.domain.model.ExpiryState
import com.utkudemir.cue.shared.domain.model.ScheduledReminder
import com.utkudemir.cue.shared.domain.repository.AccountRepository
import com.utkudemir.cue.shared.domain.repository.ReminderRepository
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleRemindersUseCaseTest {
    @Test
    fun `successful refresh schedules reminders`() = runBlocking {
        val status = sampleAccountStatus()
        val reminders = listOf(ScheduledReminder(Instant.parse("2026-04-20T09:00:00Z"), "3 days left"))
        val reminderRepository = FakeReminderRepository(reminders)
        val useCase = ScheduleRemindersUseCase(
            accountRepository = FakeAccountRepository(Result.success(status)),
            reminderRepository = reminderRepository
        )

        val result = useCase()

        assertEquals(reminders, result.getOrThrow())
        assertEquals(1, reminderRepository.scheduleCalls)
    }

    @Test
    fun `failed refresh returns failure without scheduling`() = runBlocking {
        val reminderRepository = FakeReminderRepository(emptyList())
        val useCase = ScheduleRemindersUseCase(
            accountRepository = FakeAccountRepository(Result.failure(IllegalStateException("Not authenticated"))),
            reminderRepository = reminderRepository
        )

        val result = useCase()

        assertTrue(result.isFailure)
        assertEquals(0, reminderRepository.scheduleCalls)
    }

    private fun sampleAccountStatus() = AccountStatus(
        username = "tester",
        expiration = Instant.parse("2026-04-23T09:00:00Z"),
        remainingDays = 5,
        premiumSeconds = 432000,
        isPremium = true,
        lastCheckedAt = Instant.parse("2026-04-18T09:00:00Z"),
        expiryState = ExpiryState.ACTIVE
    )
}

private class FakeAccountRepository(
    private val refreshResult: Result<AccountStatus>
) : AccountRepository {
    override suspend fun refreshAccountStatus(): Result<AccountStatus> = refreshResult

    override suspend fun getCachedAccountStatus(): AccountStatus? = null
}

private class FakeReminderRepository(
    private val scheduledReminders: List<ScheduledReminder>
) : ReminderRepository {
    var scheduleCalls: Int = 0

    override suspend fun getConfig() = error("Unused")

    override suspend fun updateConfig(config: com.utkudemir.cue.shared.domain.model.ReminderConfig) = error("Unused")

    override suspend fun previewReminders(accountStatus: AccountStatus): List<ScheduledReminder> = emptyList()

    override suspend fun scheduleReminders(accountStatus: AccountStatus): List<ScheduledReminder> {
        scheduleCalls += 1
        return scheduledReminders
    }

    override suspend fun cancelReminders() = Unit
}

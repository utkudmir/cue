package com.utkudemir.cue.shared

import com.utkudemir.cue.shared.domain.model.AccountStatus
import com.utkudemir.cue.shared.domain.model.DiagnosticsBundle
import com.utkudemir.cue.shared.domain.model.ExpiryState
import com.utkudemir.cue.shared.domain.model.ReminderConfig
import com.utkudemir.cue.shared.domain.model.ScheduledReminder
import com.utkudemir.cue.shared.domain.model.StoredAuthState
import com.utkudemir.cue.shared.domain.repository.AccountRepository
import com.utkudemir.cue.shared.domain.repository.AuthRepository
import com.utkudemir.cue.shared.domain.repository.DiagnosticsRepository
import com.utkudemir.cue.shared.domain.repository.ReminderRepository
import com.utkudemir.cue.shared.domain.usecase.ExportDiagnosticsUseCase
import com.utkudemir.cue.shared.domain.usecase.PreviewDiagnosticsUseCase
import com.utkudemir.cue.shared.platform.ExportedFile
import com.utkudemir.cue.shared.platform.FileExporter
import com.utkudemir.cue.shared.platform.NotificationScheduler
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CueControllerTest {
    @Test
    fun `sync reminders uses cached status and schedules when notifications are enabled`() = runBlocking {
        val reminderRepository = FakeControllerReminderRepository(
            scheduledReminders = listOf(ScheduledReminder(Instant.parse("2026-04-20T09:00:00Z"), "Soon"))
        )
        val controller = buildController(
            accountRepository = FakeControllerAccountRepository(cachedStatus = sampleAccountStatus()),
            reminderRepository = reminderRepository,
            notificationScheduler = FakeControllerNotificationScheduler(enabled = true)
        )

        val count = controller.syncReminders()

        assertEquals(1, count)
        assertEquals(1, reminderRepository.scheduleCalls)
    }

    @Test
    fun `sync reminders cancels when notifications are disabled`() = runBlocking {
        val reminderRepository = FakeControllerReminderRepository()
        val controller = buildController(
            accountRepository = FakeControllerAccountRepository(cachedStatus = sampleAccountStatus()),
            reminderRepository = reminderRepository,
            notificationScheduler = FakeControllerNotificationScheduler(enabled = false)
        )

        val count = controller.syncReminders()

        assertEquals(0, count)
        assertEquals(1, reminderRepository.cancelCalls)
    }

    @Test
    fun `preview reminders refreshes account status when cache is empty`() = runBlocking {
        val status = sampleAccountStatus()
        val preview = listOf(ScheduledReminder(Instant.parse("2026-04-20T09:00:00Z"), "Soon"))
        val accountRepository = FakeControllerAccountRepository(
            cachedStatus = null,
            refreshResult = Result.success(status)
        )
        val controller = buildController(
            accountRepository = accountRepository,
            reminderRepository = FakeControllerReminderRepository(previewReminders = preview)
        )

        val reminders = controller.previewReminders()

        assertEquals(preview, reminders)
        assertEquals(1, accountRepository.refreshCalls)
    }

    private fun buildController(
        accountRepository: FakeControllerAccountRepository = FakeControllerAccountRepository(cachedStatus = sampleAccountStatus()),
        reminderRepository: FakeControllerReminderRepository = FakeControllerReminderRepository(),
        notificationScheduler: FakeControllerNotificationScheduler = FakeControllerNotificationScheduler(enabled = true)
    ) = CueController(
        authRepository = FakeControllerAuthRepository(),
        accountRepository = accountRepository,
        reminderRepository = reminderRepository,
        notificationScheduler = notificationScheduler,
        exportDiagnosticsUseCase = ExportDiagnosticsUseCase(FakeControllerDiagnosticsRepository(), FakeControllerFileExporter()),
        previewDiagnosticsUseCase = PreviewDiagnosticsUseCase(FakeControllerDiagnosticsRepository())
    )

    private companion object {
        fun sampleAccountStatus() = AccountStatus(
            username = "sample-user",
            expiration = Instant.parse("2026-04-23T09:00:00Z"),
            remainingDays = 5,
            premiumSeconds = 432000,
            isPremium = true,
            lastCheckedAt = Instant.parse("2026-04-18T09:00:00Z"),
            expiryState = ExpiryState.ACTIVE
        )
    }
}

private class FakeControllerAuthRepository : AuthRepository {
    override suspend fun startAuthorization() = error("Unused")

    override suspend fun pollAuthorization() = error("Unused")

    override suspend fun getStoredAuthState(): StoredAuthState? = null

    override suspend fun ensureValidAccessToken(): String? = null

    override suspend fun isAuthenticated(): Boolean = false

    override suspend fun disconnect() = Unit
}

private class FakeControllerAccountRepository(
    private val cachedStatus: AccountStatus?,
    private val refreshResult: Result<AccountStatus> = cachedStatus?.let(Result.Companion::success)
        ?: Result.failure(IllegalStateException("No status"))
) : AccountRepository {
    var refreshCalls: Int = 0

    override suspend fun refreshAccountStatus(): Result<AccountStatus> {
        refreshCalls += 1
        return refreshResult
    }

    override suspend fun getCachedAccountStatus(): AccountStatus? = cachedStatus
}

private class FakeControllerReminderRepository(
    private val previewReminders: List<ScheduledReminder> = emptyList(),
    private val scheduledReminders: List<ScheduledReminder> = emptyList()
) : ReminderRepository {
    var scheduleCalls: Int = 0
    var cancelCalls: Int = 0

    override suspend fun getConfig(): ReminderConfig = ReminderConfig()

    override suspend fun updateConfig(config: ReminderConfig) = Unit

    override suspend fun previewReminders(accountStatus: AccountStatus): List<ScheduledReminder> = previewReminders

    override suspend fun scheduleReminders(accountStatus: AccountStatus): List<ScheduledReminder> {
        scheduleCalls += 1
        return scheduledReminders
    }

    override suspend fun cancelReminders() {
        cancelCalls += 1
    }
}

private class FakeControllerNotificationScheduler(
    private val enabled: Boolean
) : NotificationScheduler {
    override suspend fun requestPermissionIfNeeded(): Boolean = enabled

    override suspend fun areNotificationsEnabled(): Boolean = enabled

    override suspend fun schedule(reminders: List<ScheduledReminder>) = Unit

    override suspend fun cancelAll() = Unit
}

private class FakeControllerDiagnosticsRepository : DiagnosticsRepository {
    override suspend fun collectDiagnostics(): DiagnosticsBundle = DiagnosticsBundle(
        appVersion = "1.0.0",
        os = "test",
        lastSync = null,
        accountState = null,
        additionalInfo = emptyMap()
    )
}

private class FakeControllerFileExporter : FileExporter {
    override suspend fun exportTextFile(fileName: String, content: String): ExportedFile =
        ExportedFile(displayName = fileName, location = "/tmp/$fileName")
}

package com.utkudemir.cue.shared

import com.utkudemir.cue.shared.domain.model.AccountStatus
import com.utkudemir.cue.shared.domain.model.ExpiryState
import com.utkudemir.cue.shared.domain.model.ReminderConfig
import com.utkudemir.cue.shared.domain.model.ReminderConfigSnapshot
import com.utkudemir.cue.shared.domain.model.ScheduledReminder
import com.utkudemir.cue.shared.domain.model.toReminderConfig
import com.utkudemir.cue.shared.domain.model.toSnapshot
import com.utkudemir.cue.shared.reminders.ReminderPlanner
import kotlinx.datetime.Instant

object CueDemoContent {
    private val now = Instant.parse("2026-04-22T10:00:00Z")
    private val expiration = Instant.parse("2026-04-25T18:00:00Z")
    private val defaultReminderConfig = ReminderConfig(
        enabled = true,
        daysBefore = setOf(3, 1),
        notifyOnExpiry = true,
        notifyAfterExpiry = false,
    )
    private val reminderPlanner = ReminderPlanner()

    fun accountStatus(): AccountStatus = AccountStatus(
        username = "demo-account",
        expiration = expiration,
        remainingDays = 3,
        premiumSeconds = 3 * 24L * 60L * 60L,
        isPremium = true,
        lastCheckedAt = now,
        expiryState = ExpiryState.EXPIRING_SOON,
    )

    fun reminderConfig(): ReminderConfig = defaultReminderConfig

    fun reminderConfigSnapshot(): ReminderConfigSnapshot = defaultReminderConfig.toSnapshot()

    fun scheduledReminders(config: ReminderConfig = defaultReminderConfig): List<ScheduledReminder> =
        reminderPlanner.planReminders(now = now, accountStatus = accountStatus(), config = config)

    fun scheduledRemindersForSnapshot(snapshot: ReminderConfigSnapshot): List<ScheduledReminder> =
        scheduledReminders(snapshot.toReminderConfig())

    fun diagnosticsPreview(): String = """
        {
          "appVersion": "1.0.0-demo",
          "os": "Demo Mode",
          "lastSync": "${now}",
          "accountState": "EXPIRING_SOON",
          "additionalInfo": {
            "notificationsEnabled": "false",
            "demoMode": "true",
            "premiumState": "active"
          }
        }
    """.trimIndent()
}

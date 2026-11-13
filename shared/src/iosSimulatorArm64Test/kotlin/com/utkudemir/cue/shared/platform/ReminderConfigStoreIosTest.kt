package com.utkudemir.cue.shared.platform

import com.utkudemir.cue.shared.domain.model.ReminderConfig
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderConfigStoreIosTest {
    @Test
    fun savesSelectedReminderDaysAcrossStoreInstances() = runBlocking {
        val suite = "com.utkudemir.cue.reminder-tests.${NSUUID().UUIDString}"
        val defaults = requireNotNull(NSUserDefaults(suiteName = suite))
        val store = ReminderConfigStoreImpl(defaults = defaults)
        val config = ReminderConfig(daysBefore = setOf(3, 1), notifyAfterExpiry = true)

        try {
            store.write(config)
            val reopened = ReminderConfigStoreImpl(defaults = defaults)
            assertEquals(config, reopened.read())
        } finally {
            defaults.removePersistentDomainForName(suite)
        }
    }
}

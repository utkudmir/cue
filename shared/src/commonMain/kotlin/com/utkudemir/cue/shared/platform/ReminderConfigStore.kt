package com.utkudemir.cue.shared.platform

import com.utkudemir.cue.shared.domain.model.ReminderConfig

interface ReminderConfigStore {
    suspend fun read(): ReminderConfig
    suspend fun write(config: ReminderConfig)
}

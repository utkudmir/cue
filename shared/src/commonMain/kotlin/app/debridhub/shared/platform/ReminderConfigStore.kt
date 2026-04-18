package app.debridhub.shared.platform

import app.debridhub.shared.domain.model.ReminderConfig

interface ReminderConfigStore {
    suspend fun read(): ReminderConfig
    suspend fun write(config: ReminderConfig)
}

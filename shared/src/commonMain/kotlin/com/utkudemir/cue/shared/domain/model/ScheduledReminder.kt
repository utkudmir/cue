package com.utkudemir.cue.shared.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ScheduledReminder(
    val fireAt: Instant,
    val message: String
)

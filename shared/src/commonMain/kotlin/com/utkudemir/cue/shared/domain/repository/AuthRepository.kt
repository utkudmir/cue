package com.utkudemir.cue.shared.domain.repository

import com.utkudemir.cue.shared.domain.model.AuthPollResult
import com.utkudemir.cue.shared.domain.model.DeviceAuthSession
import com.utkudemir.cue.shared.domain.model.StoredAuthState

interface AuthRepository {
    suspend fun startAuthorization(): DeviceAuthSession
    suspend fun pollAuthorization(): AuthPollResult
    suspend fun getStoredAuthState(): StoredAuthState?
    suspend fun ensureValidAccessToken(): String?
    suspend fun isAuthenticated(): Boolean
    suspend fun disconnect()
}

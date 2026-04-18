package app.debridhub.shared.domain.repository

import app.debridhub.shared.domain.model.AuthPollResult
import app.debridhub.shared.domain.model.DeviceAuthSession
import app.debridhub.shared.domain.model.StoredAuthState

interface AuthRepository {
    suspend fun startAuthorization(): DeviceAuthSession
    suspend fun pollAuthorization(): AuthPollResult
    suspend fun getStoredAuthState(): StoredAuthState?
    suspend fun ensureValidAccessToken(): String?
    suspend fun isAuthenticated(): Boolean
    suspend fun disconnect()
}

package app.debridhub.shared.platform

import app.debridhub.shared.domain.model.StoredAuthState

interface SecureTokenStore {
    suspend fun read(): StoredAuthState?
    suspend fun write(state: StoredAuthState)
    suspend fun clear()
}

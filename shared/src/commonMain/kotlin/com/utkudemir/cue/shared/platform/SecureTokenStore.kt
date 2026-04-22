package com.utkudemir.cue.shared.platform

import com.utkudemir.cue.shared.domain.model.StoredAuthState

interface SecureTokenStore {
    suspend fun read(): StoredAuthState?
    suspend fun write(state: StoredAuthState)
    suspend fun clear()
}

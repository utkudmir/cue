package com.utkudemir.cue.shared.data.repository

import com.utkudemir.cue.shared.data.remote.RealDebridService
import com.utkudemir.cue.shared.domain.model.AccountStatus
import com.utkudemir.cue.shared.domain.repository.AccountRepository
import com.utkudemir.cue.shared.domain.repository.AuthRepository
import com.utkudemir.cue.shared.domain.usecase.ComputeExpiryStateUseCase
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class AccountRepositoryImpl(
    private val api: RealDebridService,
    private val authRepository: AuthRepository,
    private val computeExpiryState: ComputeExpiryStateUseCase = ComputeExpiryStateUseCase(),
    private val nowProvider: () -> Instant = { Clock.System.now() }
) : AccountRepository {
    private var cached: AccountStatus? = null

    override suspend fun refreshAccountStatus(): Result<AccountStatus> {
        val token = authRepository.ensureValidAccessToken()
            ?: return Result.failure(IllegalStateException("Not authenticated"))

        return runCatching {
            val dto = api.getUser(token)
            val expiration = dto.expiration?.let(Instant::parse)
            val premiumSeconds = dto.premium
            val remainingDays = premiumSeconds
                ?.takeIf { it > 0 }
                ?.div(24 * 3600)
                ?.toInt()
            val isPremium = dto.type == "premium" || (premiumSeconds ?: 0L) > 0L
            AccountStatus(
                username = dto.username,
                expiration = expiration,
                remainingDays = remainingDays,
                premiumSeconds = premiumSeconds,
                isPremium = isPremium,
                lastCheckedAt = nowProvider(),
                expiryState = computeExpiryState(isPremium, remainingDays)
            ).also { cached = it }
        }
    }

    override suspend fun getCachedAccountStatus(): AccountStatus? = cached
}

package com.algorand.android.usecase

import com.algorand.android.repository.Arc200Repository
import com.algorand.android.models.Result
import javax.inject.Inject

/**
 * Use case to trigger the refresh of ARC-200 token cache for a specific account.
 */
class RefreshArc200CacheUseCase @Inject constructor(
    private val arc200Repository: Arc200Repository
) {
    suspend operator fun invoke(accountId: String): Result<Unit> {
        return arc200Repository.refreshArc200CacheForAccount(accountId)
    }
}

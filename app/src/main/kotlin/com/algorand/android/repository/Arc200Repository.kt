package com.algorand.android.repository

import com.algorand.android.models.Result
import com.algorand.wallet.asset.domain.model.AssetDetail

/**
 * Repository for fetching ARC-200 token data and managing its cache.
 */
interface Arc200Repository {

    /**
     * Fetches ARC-200 balances and details for a given account from the Mimir API
     * and updates the local Room database cache.
     *
     * @param accountId The Algorand address of the account.
     * @return A Result indicating success or failure.
     */
    suspend fun refreshArc200CacheForAccount(accountId: String): Result<Unit>

    /**
     * Fetches ARC-200 asset details for a given assetId from the Mimir API.
     *
     * @param assetId The ID of the ARC-200 asset.
     * @return A Result containing the AssetDetail on success, or an error on failure.
     */
    suspend fun getArc200AssetDetail(assetId: Long): Result<AssetDetail>
}

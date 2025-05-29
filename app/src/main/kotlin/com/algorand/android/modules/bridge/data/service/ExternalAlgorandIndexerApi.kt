package com.algorand.android.modules.bridge.data.service

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import java.math.BigInteger

// DTOs for parsing Algorand Indexer account response

data class ExternalIndexerAccountQueryResponse(
    @SerializedName("account") val account: ExternalIndexerAccountDetail,
    @SerializedName("current-round") val currentRound: Long
)

data class ExternalIndexerAccountDetail(
    @SerializedName("address") val address: String,
    @SerializedName("amount") val amount: BigInteger, // Native Algo balance
    @SerializedName("assets") val assets: List<ExternalIndexerAssetHolding>?
    // Add other fields if needed, e.g., "amount-without-pending-rewards"
)

data class ExternalIndexerAssetHolding(
    @SerializedName("asset-id") val assetId: Long,
    @SerializedName("amount") val amount: BigInteger,
    @SerializedName("is-frozen") val isFrozen: Boolean? = null
    // Add other fields if needed, e.g., "deleted", "opted-in-at-round"
)

/**
 * Service to interact with a public Algorand Indexer API, specifically for fetching
 * account details to find aVOI balances.
 */
interface ExternalAlgorandIndexerApi {

    @GET("v2/accounts/{accountAddress}")
    suspend fun getAccountDetail(
        @Path("accountAddress") accountAddress: String
    ): Response<ExternalIndexerAccountQueryResponse>
}

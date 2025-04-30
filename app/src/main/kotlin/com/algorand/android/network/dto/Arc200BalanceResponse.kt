package com.algorand.android.network.dto

import com.google.gson.annotations.SerializedName

/**
 * Represents the full response from the Mimir API /arc200/balances endpoint.
 */
data class Arc200BalanceResponse(
    @SerializedName("balances")
    val balances: List<Arc200ApiBalanceInfo>?,
    @SerializedName("next-token") // Matches the API response field name
    val nextToken: String?,
    @SerializedName("total-count")
    val totalCount: Long?,
    @SerializedName("current-round")
    val currentRound: Long?
)

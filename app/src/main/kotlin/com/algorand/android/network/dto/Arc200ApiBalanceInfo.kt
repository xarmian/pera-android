package com.algorand.android.network.dto

import com.google.gson.annotations.SerializedName

/**
 * Represents a single ARC-200 token balance from the Mimir API /arc200/balances endpoint.
 * Matches the structure within the "balances" array.
 */
data class Arc200ApiBalanceInfo(
    @SerializedName("name")
    val name: String?,
    @SerializedName("symbol")
    val symbol: String?,
    @SerializedName("balance")
    val balance: String?, // Keep as String due to potential size
    @SerializedName("decimals")
    val decimals: Int?,
    @SerializedName("verified")
    val verified: Int?, // API shows 1, assuming Int
    @SerializedName("accountId")
    val accountId: String?,
    @SerializedName("contractId")
    val contractId: Long?, // Assuming contractId is a Long
    @SerializedName("imageUrl")
    val imageUrl: String?
)

package com.algorand.android.network.dto

import com.google.gson.annotations.SerializedName

/**
 * Represents a single ARC-200 token detail from the Mimir API /arc200/tokens endpoint.
 * Matches the structure within the "tokens" array.
 */
data class Arc200ApiTokenDetail(
    @SerializedName("contractId")
    val contractId: Long?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("symbol")
    val symbol: String?,
    @SerializedName("decimals")
    val decimals: Int?,
    @SerializedName("totalSupply")
    val totalSupply: String?, // Keep as String due to potential size
    @SerializedName("creator")
    val creator: String?,
    @SerializedName("deleted")
    val deleted: Int?, // API shows 0, assuming Int
    @SerializedName("verified")
    val verified: Int?, // API shows 1, assuming Int
    @SerializedName("mintRound")
    val mintRound: Long?,
    @SerializedName("imageUrl")
    val imageUrl: String?
    // "globalState" is omitted as it's an empty array in the example and likely not needed for display
)

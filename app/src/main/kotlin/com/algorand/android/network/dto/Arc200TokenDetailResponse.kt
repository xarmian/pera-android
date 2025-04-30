package com.algorand.android.network.dto

import com.google.gson.annotations.SerializedName

/**
 * Represents the full response from the Mimir API /arc200/tokens endpoint.
 */
data class Arc200TokenDetailResponse(
    @SerializedName("tokens")
    val tokens: List<Arc200ApiTokenDetail>?,
    @SerializedName("next-token") // Matches the API response field name
    val nextToken: String?,
    @SerializedName("total-count")
    val totalCount: Long?,
    @SerializedName("current-round")
    val currentRound: Long?
)

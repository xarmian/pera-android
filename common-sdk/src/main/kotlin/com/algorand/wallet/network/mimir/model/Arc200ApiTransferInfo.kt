package com.algorand.wallet.network.mimir.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a single ARC-200 token transfer from the Mimir API /arc200/transfers endpoint.
 * Matches the structure within the "transfers" array.
 */
data class Arc200ApiTransferInfo(
    @SerializedName("transactionId")
    val transactionId: String?,
    @SerializedName("contractId")
    val contractId: Long?,
    @SerializedName("timestamp")
    val timestamp: Long?,
    @SerializedName("round")
    val round: Long?,
    @SerializedName("sender")
    val sender: String?,
    @SerializedName("receiver")
    val receiver: String?,
    @SerializedName("amount")
    val amount: String? // Keep as String due to potential size
)

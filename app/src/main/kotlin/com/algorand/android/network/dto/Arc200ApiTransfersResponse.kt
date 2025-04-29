package com.algorand.android.network.dto

import com.google.gson.annotations.SerializedName

/**
 * Represents the top-level response structure for the Mimir API /arc200/transfers endpoint.
 */
data class Arc200ApiTransfersResponse(
    @SerializedName("transfers")
    val transfers: List<Arc200ApiTransferInfo>?
)

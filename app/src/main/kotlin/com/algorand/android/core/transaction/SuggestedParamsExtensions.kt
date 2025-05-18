package com.algorand.android.core.transaction

import com.algorand.algosdk.sdk.SuggestedParams
import com.algorand.algosdk.v2.client.model.TransactionParametersResponse

fun SuggestedParams.toTransactionParametersResponse(): TransactionParametersResponse {
    return TransactionParametersResponse().apply {
        fee = this@toTransactionParametersResponse.fee
        minFee = this@toTransactionParametersResponse.fee
        firstRoundValid = this@toTransactionParametersResponse.lastRoundValid - 2000L
        lastRound = this@toTransactionParametersResponse.lastRoundValid - 1000L
        genesisID = this@toTransactionParametersResponse.genesisID
        genesisHash = this@toTransactionParametersResponse.genesisHash
    }
}

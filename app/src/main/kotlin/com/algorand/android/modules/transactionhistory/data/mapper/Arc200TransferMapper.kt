package com.algorand.android.modules.transactionhistory.data.mapper

import com.algorand.android.modules.transaction.common.domain.model.TransactionDTO
import com.algorand.android.modules.transaction.common.domain.model.TransactionTypeDTO
import com.algorand.android.modules.transaction.common.domain.model.AssetTransferDTO
import com.algorand.android.modules.transactionhistory.domain.model.PaginatedTransactionsDTO
import com.algorand.android.network.dto.Arc200ApiTransferInfo
import com.algorand.android.network.dto.Arc200ApiTransfersResponse
import java.math.BigInteger
import javax.inject.Inject

class Arc200TransferMapper @Inject constructor() {

    fun mapToPaginatedTransactionsDTO(response: Arc200ApiTransfersResponse): PaginatedTransactionsDTO {
        val transactions = response.transfers?.mapNotNull {
            mapToTransactionDTO(it)
        } ?: emptyList()
        // Mimir API doesn't support pagination, so nextToken is always null
        return PaginatedTransactionsDTO(nextToken = null, transactionList = transactions)
    }

    private fun mapToTransactionDTO(transfer: Arc200ApiTransferInfo): TransactionDTO? {
        // Basic mapping - Some fields might be missing or need default values
        // as ARC-200 transfers have less info than indexer transactions.
        val amount = transfer.amount?.toBigIntegerOrNull() ?: BigInteger.ZERO
        val fee = 0L // Corrected type to Long

        // Determine if it's an asset transfer (should always be for ARC-200)
        val transactionType = TransactionTypeDTO.ASSET_TRANSACTION

        // Create AssetTransfer object
        val assetTransfer = AssetTransferDTO(
            assetId = transfer.contractId,
            amount = amount,
            receiverAddress = transfer.receiver,
            closeTo = null // Not provided by Mimir arc200/transfers
        )

        return TransactionDTO(
            id = transfer.transactionId,
            roundTimeAsTimestamp = transfer.timestamp,
            fee = fee,
            senderAddress = transfer.sender,
            confirmedRound = transfer.round,
            signature = null,
            noteInBase64 = null,
            payment = null,
            assetTransfer = assetTransfer,
            assetConfiguration = null,
            applicationCall = null,
            assetFreezeTransaction = null,
            keyRegTransactionDTO = null,
            closeAmount = null,
            transactionType = transactionType,
            innerTransactions = null,
            rekeyTo = null,
            createdAssetIndex = null
        )
    }
}

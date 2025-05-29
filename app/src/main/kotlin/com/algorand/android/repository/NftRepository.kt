package com.algorand.android.repository

import com.algorand.android.models.Result
import com.algorand.wallet.network.mimir.model.MimirNftItemDto

/**
 * Repository for fetching NFT (collectible) data.
 */
interface NftRepository {

    /**
     * Fetches NFT data for a given account directly from the Mimir NFT Indexer API.
     * This does not interact with local cache.
     *
     * @param accountAddress The Algorand address of the account.
     * @param limit The maximum number of items to return.
     * @param nextToken The token for fetching the next page of results.
     * @return A Result containing a Pair of the NFT list and the next token for pagination, or an error.
     */
    suspend fun getAccountNftsFromMimir(
        accountAddress: String,
        limit: Int,
        nextToken: String?
    ): Result<MimirNftsResponse>

    /**
     * Fetches a specific NFT detail from the Mimir NFT Indexer API using contractId and tokenId.
     *
     * @param contractId The Algorand Standard Asset (ASA) ID, also known as the contract ID.
     * @param tokenId The specific token ID within the contract.
     * @return A Result containing the MimirNftItemDto on success, or an error.
     */
    suspend fun getNftDetailFromMimir(
        contractId: Long,
        tokenId: String
    ): Result<MimirNftItemDto>
}

// Add a data class to hold the response
data class MimirNftsResponse(
    val items: List<MimirNftItemDto>,
    val nextToken: String?,
    val totalCount: Long?
)

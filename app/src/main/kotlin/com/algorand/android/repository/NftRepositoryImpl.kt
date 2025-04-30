package com.algorand.android.repository

import com.algorand.android.models.Result
import com.algorand.android.network.MimirApi
import com.algorand.android.network.request
import com.algorand.android.network.dto.MimirNftItemDto
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NftRepositoryImpl @Inject constructor(
    private val mimirApi: MimirApi
) : NftRepository {

    override suspend fun getAccountNftsFromMimir(
        accountAddress: String,
        limit: Int,
        nextToken: String?
    ): Result<MimirNftsResponse> {
        return withContext(Dispatchers.IO) {
            request {
                mimirApi.getAccountNfts(
                    ownerAddress = accountAddress,
                    limit = limit,
                    nextToken = nextToken
                )
            }.run {
                when (this) {
                    is Result.Success -> {
                        val response = data
                        Result.Success(
                            MimirNftsResponse(
                                items = response.tokens.orEmpty(),
                                nextToken = response.nextToken,
                                totalCount = response.totalCount
                            )
                        )
                    }
                    is Result.Error -> {
                        Result.Error(exception)
                    }
                }
            }
        }
    }

    override suspend fun getNftDetailFromMimir(
        contractId: Long,
        tokenId: String
    ): Result<MimirNftItemDto> {
        return withContext(Dispatchers.IO) {
            request {
                mimirApi.getTokens(
                    contractId = contractId,
                    tokenId = tokenId,
                    limit = 1
                )
            }.run {
                when (this) {
                    is Result.Success -> {
                        val nftItem = data.tokens?.firstOrNull()
                        if (nftItem != null) {
                            Result.Success(nftItem)
                        } else {
                            Result.Error(Exception("NFT not found for contractId=$contractId, tokenId=$tokenId"))
                        }
                    }
                    is Result.Error -> {
                        Result.Error(exception)
                    }
                }
            }
        }
    }
}

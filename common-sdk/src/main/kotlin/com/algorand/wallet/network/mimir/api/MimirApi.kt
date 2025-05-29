/*
 * Copyright 2025 Pera Wallet, LDA
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.algorand.wallet.network.mimir.api

import com.algorand.wallet.network.mimir.model.Arc200BalanceResponse
import com.algorand.wallet.network.mimir.model.Arc200TokenDetailResponse
import com.algorand.wallet.network.mimir.model.Arc200ApiTransfersResponse
import com.algorand.wallet.network.mimir.model.MimirNftListResponse
import com.algorand.wallet.block.data.model.ShouldRefreshRequestBody
import com.algorand.wallet.block.data.model.ShouldRefreshResponse
import com.algorand.wallet.block.data.service.BlockPollingNetworkService
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface MimirApi : BlockPollingNetworkService {

    @POST("account/should-refresh")
    override suspend fun shouldRefresh(@Body body: ShouldRefreshRequestBody): Response<ShouldRefreshResponse>

    @GET("account/assets/")
    suspend fun getArc200Balances(
        @Query("accountId") accountId: String,
        @Query("limit") limit: Int?,
        @Query("next") nextToken: String? // Using "next" as per revised plan 2.1
    ): Response<Arc200BalanceResponse>

    @GET("arc200/tokens/")
    suspend fun getArc200Tokens(
        @Query("contractId") contractIds: String // Comma-separated list
    ): Response<Arc200TokenDetailResponse>

    @GET("arc200/transfers")
    suspend fun getArc200Transfers(
        @Query("limit") limit: Int = 50,
        @Query("contractId") contractId: Long,
        @Query("user") userAddress: String
    ): Response<Arc200ApiTransfersResponse>

    @GET("nft-indexer/v1/tokens")
    suspend fun getAccountNfts(
        @Query("owner") ownerAddress: String,
        @Query("limit") limit: Int?,
        @Query("next-token") nextToken: String?
    ): Response<MimirNftListResponse>

    @GET("nft-indexer/v1/tokens")
    suspend fun getTokens(
        @Query("contractId") contractId: Long,
        @Query("tokenId") tokenId: String,
        @Query("limit") limit: Int = 1 // Ensure we only get one result
    ): Response<MimirNftListResponse> // Reuse existing DTO as structure matches
}

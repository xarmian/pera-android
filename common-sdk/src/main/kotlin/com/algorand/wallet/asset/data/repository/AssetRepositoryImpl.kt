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

package com.algorand.wallet.asset.data.repository

import com.algorand.wallet.asset.data.database.dao.AssetDetailDao
import com.algorand.wallet.asset.data.database.dao.CollectibleDao
import com.algorand.wallet.asset.data.database.dao.CollectibleMediaDao
import com.algorand.wallet.asset.data.database.dao.CollectibleTraitDao
import com.algorand.wallet.asset.data.mapper.model.AlgoAssetDetailMapper
import com.algorand.wallet.asset.data.mapper.model.AssetMapper
import com.algorand.wallet.asset.data.mapper.model.collectible.CollectibleDetailMapper
import com.algorand.wallet.asset.data.model.AssetResponse
import com.algorand.wallet.asset.data.service.AssetDetailApiService
import com.algorand.wallet.asset.data.service.AssetDetailNodeApiService
import com.algorand.wallet.asset.data.utils.toQueryString
import com.algorand.wallet.asset.domain.model.Asset
import com.algorand.wallet.asset.domain.model.AssetDetail
import com.algorand.wallet.asset.domain.model.CollectibleDetail
import com.algorand.wallet.asset.domain.repository.AssetRepository
import com.algorand.wallet.asset.domain.util.AssetConstants.ALGO_ID
import com.algorand.wallet.foundation.PeraResult
import com.algorand.wallet.foundation.network.utils.request
import com.algorand.wallet.mapper.arc200.Arc200DtoToEntityMapper
import com.algorand.wallet.network.mimir.api.MimirApi
import com.algorand.wallet.network.mimir.model.Arc200TokenDetailResponse
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

internal class AssetRepositoryImpl @Inject constructor(
    private val assetDetailApi: AssetDetailApiService,
    private val assetDetailNodeApi: AssetDetailNodeApiService,
    private val assetDetailCacheHelper: AssetDetailCacheHelper,
    private val assetDetailDao: AssetDetailDao,
    private val collectibleDao: CollectibleDao,
    private val assetMapper: AssetMapper,
    private val algoAssetDetailMapper: AlgoAssetDetailMapper,
    private val collectibleDetailMapper: CollectibleDetailMapper,
    private val collectibleMediaDao: CollectibleMediaDao,
    private val collectibleTraitDao: CollectibleTraitDao,
    private val mimirApi: MimirApi,
    private val arc200DtoToEntityMapper: Arc200DtoToEntityMapper,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AssetRepository {

    override suspend fun fetchAsset(assetId: Long): PeraResult<Asset> {
        return withContext(coroutineDispatcher) {
            try {
                val mimirResponse = mimirApi.getArc200Tokens(contractIds = assetId.toString())

                if (mimirResponse.isSuccessful && mimirResponse.body() != null) {
                    val tokenDetail = mimirResponse.body()?.tokens?.firstOrNull()
                    if (tokenDetail == null) {
                        PeraResult.Error(Exception("No asset found with id: $assetId from Mimir"))
                    } else {
                        val assetDetail = arc200DtoToEntityMapper.mapTokenDetailToAssetDetail(tokenDetail)
                        if (assetDetail != null) {
                            PeraResult.Success(assetDetail)
                        } else {
                            PeraResult.Error(Exception("Failed to map Arc200ApiTokenDetail to AssetDetail for id: $assetId"))
                        }
                    }
                } else {
                    PeraResult.Error(Exception("Failed to fetch asset $assetId from Mimir: ${mimirResponse.code()} ${mimirResponse.message()}"))
                }
            } catch (exception: Exception) {
                PeraResult.Error(exception)
            }
        }
    }

    override suspend fun fetchAssets(assetIds: List<Long>): PeraResult<List<Asset>> {
        return try {
            withContext(coroutineDispatcher) {
                val uniqueAssetIds = assetIds.toSet()
                if (uniqueAssetIds.isEmpty()) return@withContext PeraResult.Success(emptyList())

                val chunkedAssetIds = uniqueAssetIds.chunked(MAX_ASSET_FETCH_COUNT)
                val mappedResults = mutableListOf<Asset>()

                for (chunk in chunkedAssetIds) {
                    val mimirResponse = mimirApi.getArc200Tokens(contractIds = chunk.joinToString(","))
                    if (mimirResponse.isSuccessful && mimirResponse.body() != null) {
                        val tokenDetailsList = mimirResponse.body()?.tokens
                        if (!tokenDetailsList.isNullOrEmpty()) {
                            tokenDetailsList.forEach { tokenDetail ->
                                arc200DtoToEntityMapper.mapTokenDetailToAssetDetail(tokenDetail)?.let {
                                    mappedResults.add(it)
                                }
                            }
                        }
                    } else {
                        return@withContext PeraResult.Error(
                            Exception("Failed to fetch assets from Mimir: ${mimirResponse.code()} ${mimirResponse.message()} for IDs: ${chunk.joinToString(",")}")
                        )
                    }
                }
                PeraResult.Success(mappedResults)
            }
        } catch (exception: Exception) {
            PeraResult.Error(exception)
        }
    }

    override suspend fun fetchAssetDetailFromNode(assetId: Long): PeraResult<AssetDetail> {
        return withContext(coroutineDispatcher) {
            request { assetDetailNodeApi.getAssetDetail(assetId) }.map {
                assetMapper(assetId, it)
            }
        }
    }

    override suspend fun fetchAndCacheAssets(assetIds: List<Long>, includeDeleted: Boolean): PeraResult<Unit> {
        // Note: `includeDeleted` parameter is not used with MimirApi.getArc200Tokens.
        // The concept of "deleted" might be different for ARC-200 tokens.
        return try {
            withContext(coroutineDispatcher) {
                val uniqueAssetIds = assetIds.toSet()
                if (uniqueAssetIds.isEmpty()) return@withContext PeraResult.Success(Unit)

                val chunkedAssetIds = uniqueAssetIds.chunked(MAX_ASSET_FETCH_COUNT)
                val allMappedAssetDetails = mutableListOf<AssetDetail>()

                for (chunk in chunkedAssetIds) {
                    val mimirResponse = mimirApi.getArc200Tokens(contractIds = chunk.joinToString(","))
                    if (mimirResponse.isSuccessful && mimirResponse.body() != null) {
                        val tokenDetailsList = mimirResponse.body()?.tokens
                        if (!tokenDetailsList.isNullOrEmpty()) {
                            tokenDetailsList.forEach { tokenDetail ->
                                arc200DtoToEntityMapper.mapTokenDetailToAssetDetail(tokenDetail)?.let {
                                    allMappedAssetDetails.add(it)
                                }
                            }
                        } else {
                            // Log or handle cases where a chunk returns no tokens, if necessary.
                        }
                    } else {
                        return@withContext PeraResult.Error(
                            Exception("Failed to fetch assets for caching from Mimir: ${mimirResponse.code()} ${mimirResponse.message()} for IDs: ${chunk.joinToString(",")}")
                        )
                    }
                }

                // Call the new overloaded cacheAssetDetails method
                assetDetailCacheHelper.cacheAssetDomainDetails(allMappedAssetDetails)

                PeraResult.Success(Unit)
            }
        } catch (exception: Exception) {
            PeraResult.Error(exception)
        }
    }

    override suspend fun getCollectiblesDetail(collectibleIds: List<Long>): List<CollectibleDetail> {
        return withContext(coroutineDispatcher) {
            assetDetailCacheHelper.getCollectibleDetails(collectibleIds)
        }
    }

    override suspend fun getAssetDetail(assetId: Long): AssetDetail? {
        return if (assetId == ALGO_ID) {
            algoAssetDetailMapper()
        } else {
            assetDetailCacheHelper.getAssetDetail(assetId)
        }
    }

    override suspend fun getCollectibleDetail(collectibleId: Long): CollectibleDetail? {
        return assetDetailCacheHelper.getCollectibleDetail(collectibleId)
    }

    override suspend fun getAsset(assetId: Long): Asset? {
        return if (assetId == ALGO_ID) {
            algoAssetDetailMapper()
        } else {
            assetDetailCacheHelper.getAsset(assetId)
        }
    }

    override suspend fun fetchCollectibleDetail(collectibleAssetId: Long): PeraResult<CollectibleDetail> {
        return request { assetDetailApi.getAssetDetail(collectibleAssetId) }.use(
            onSuccess = {
                val collectibleDetail = collectibleDetailMapper(it)
                if (collectibleDetail == null) {
                    PeraResult.Error(Exception("CollectibleDetail is null"))
                } else {
                    PeraResult.Success(collectibleDetail)
                }
            },
            onFailed = { exception, code ->
                PeraResult.Error(exception, code)
            }
        )
    }

    override suspend fun clearCache() {
        withContext(coroutineDispatcher) {
            awaitAll(
                async { assetDetailDao.clearAll() },
                async { collectibleDao.clearAll() },
                async { collectibleMediaDao.clearAll() },
                async { collectibleTraitDao.clearAll() }
            )
        }
    }

    override suspend fun getCachedAssetIds(): List<Long> {
        return withContext(coroutineDispatcher) {
            assetDetailDao.getAllIds()
        }
    }

    override suspend fun isCollectibleExist(collectibleId: Long): Boolean {
        return assetDetailCacheHelper.isCollectibleExist(collectibleId)
    }

    private fun mapAssetDetailResponseToResult(assetResponse: AssetResponse): PeraResult<Asset> {
        val assetDetail = assetMapper(assetResponse)
        return if (assetDetail == null) {
            PeraResult.Error(Exception("Failed to map asset detail"))
        } else {
            PeraResult.Success(assetDetail)
        }
    }

    companion object {
        private const val MAX_ASSET_FETCH_COUNT = 100
    }
}

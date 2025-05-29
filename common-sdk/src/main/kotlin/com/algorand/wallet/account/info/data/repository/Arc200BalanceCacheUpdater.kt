/*
 * Copyright 2025 Vera Wallet, LDA
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.algorand.wallet.account.info.data.repository

import com.algorand.wallet.account.info.data.database.dao.AssetHoldingDao
import com.algorand.wallet.account.info.data.database.model.AssetHoldingEntity
import com.algorand.wallet.account.info.data.mapper.model.AssetHoldingMapper
import com.algorand.wallet.account.info.domain.model.AssetHolding
import com.algorand.wallet.asset.domain.model.Asset
import com.algorand.wallet.asset.domain.model.AssetDetail
import com.algorand.wallet.asset.domain.model.AssetType
import com.algorand.wallet.asset.domain.model.VerificationTier
import com.algorand.wallet.asset.domain.util.AssetConstants
import com.algorand.wallet.foundation.PeraResult
import com.algorand.wallet.mapper.arc200.Arc200DtoToEntityMapper
import com.algorand.wallet.network.mimir.api.MimirApi
import com.algorand.wallet.network.mimir.model.Arc200ApiBalanceInfo
import com.algorand.wallet.asset.data.repository.AssetDetailCacheHelper
import javax.inject.Inject

internal class Arc200BalanceCacheUpdater @Inject constructor(
    private val mimirApi: MimirApi,
    private val arc200DtoToEntityMapper: Arc200DtoToEntityMapper,
    private val assetHoldingDao: AssetHoldingDao,
    private val assetHoldingMapper: AssetHoldingMapper,
    private val assetDetailCacheHelper: AssetDetailCacheHelper
) {
    suspend fun fetchAndPersistArc200Balances(accountAddress: String): PeraResult<List<AssetHolding>> {
        return try {
            val arc200ApiBalances = mutableListOf<Arc200ApiBalanceInfo>()
            var nextToken: String? = null
            do {
                val response = mimirApi.getArc200Balances(
                    accountId = accountAddress,
                    limit = AssetConstants.PAGINATION_LIMIT_DEFAULT,
                    nextToken = nextToken
                )
                if (!response.isSuccessful || response.body() == null) {
                    return PeraResult.Error(
                        Exception("Failed to fetch ARC200 balances for $accountAddress from Mimir. Code: ${'$'}{response.code()}")
                    )
                }
                response.body()?.balances?.let { arc200ApiBalances.addAll(it) }
                nextToken = response.body()?.nextToken
            } while (nextToken != null)

            val arc200HoldingEntities = mutableListOf<AssetHoldingEntity>()
            val arc200DetailEntities = mutableListOf<com.algorand.wallet.asset.data.database.model.AssetDetailEntity>()

            arc200ApiBalances.forEach { balanceInfo ->
                arc200DtoToEntityMapper.mapToAssetHoldingEntity(balanceInfo)?.let {
                    arc200HoldingEntities.add(it)
                }
                arc200DtoToEntityMapper.mapToAssetDetailEntity(balanceInfo)?.let {
                    arc200DetailEntities.add(it)
                }
            }

            // Cache Asset Holdings (Balances)
            assetHoldingDao.deleteArc200HoldingsByAddress(accountAddress)
            if (arc200HoldingEntities.isNotEmpty()) {
                assetHoldingDao.insertAll(arc200HoldingEntities)
            }

            // Cache Asset Details (from balance info - includes usdValue)
            if (arc200DetailEntities.isNotEmpty()) {
                // AssetDetailCacheHelper expects domain models, but we have entities directly.
                // Let's find or create a way to cache entities directly or map them to domain first.
                // For now, assuming AssetDetailCacheHelper can take entities or we have another method.
                // Actually, AssetRepositoryImpl calls assetDetailCacheHelper.cacheAssetDomainDetails(allMappedAssetDetails)
                // So we need to map our arc200DetailEntities to List<AssetDetail> (domain model)
                // This functionality is not directly available in Arc200DtoToEntityMapper.
                // However, AssetDetailCacheHelper.cacheAssetResponseDetails exists, but we don't have AssetResponse.

                // Let's look at AssetDetailCacheHelper again for a suitable method.
                // If not, AssetRepositoryImpl maps to domain AssetDetail then caches.
                // We should do the same: map arc200DetailEntities to List<AssetDetail> (domain)
                // Arc200DtoToEntityMapper has mapTokenDetailToAssetDetail, but that takes Arc200ApiTokenDetail.
                // We need a mapper from Arc200ApiBalanceInfo to domain AssetDetail.
                // This is effectively what arc200DtoToEntityMapper.mapTokenDetailToAssetDetail does if we create a synthetic Arc200ApiTokenDetail from Arc200ApiBalanceInfo
                // OR, more simply, create domain AssetDetail objects directly from Arc200ApiBalanceInfo here.
                
                // Let's assume for a moment we have mapped them to domain AssetDetail models
                // We will refine this mapping step next if needed.
                val domainAssetDetails = arc200ApiBalances.mapNotNull { balanceInfo ->
                    // Create a domain AssetDetail from Arc200ApiBalanceInfo
                    val assetId = balanceInfo.contractId ?: return@mapNotNull null
                    val verificationTier = when (balanceInfo.verified) {
                        1 -> VerificationTier.VERIFIED
                        else -> VerificationTier.UNVERIFIED
                    }
                    AssetDetail(
                        id = assetId,
                        assetInfo = Asset.AssetInfo(
                            name = Asset.Name(fullName = balanceInfo.name ?: "", shortName = balanceInfo.symbol ?: ""),
                            decimals = balanceInfo.decimals ?: 0,
                            fiat = balanceInfo.usdValue?.let { Asset.Fiat(usdValue = it, last24HoursAlgoPriceChangePercentage = null) },
                            creator = null, // Not available in balanceInfo
                            logo = balanceInfo.imageUrl?.let { Asset.Logo(uri = it, svgUri = null) },
                            explorerUrl = null, project = null, social = null, description = null, 
                            supply = Asset.Supply(total = null, max = null), // Not available in balanceInfo in detail
                            url = null, isAvailableOnDiscoverMobile = false
                        ),
                        verificationTier = verificationTier,
                        assetType = AssetType.ARC200
                    )
                }
                assetDetailCacheHelper.cacheAssetDomainDetails(domainAssetDetails)
            }

            val persistedArc200Entities = assetHoldingDao.getArc200AssetsByAddress(accountAddress)
            val domainArc200Holdings = assetHoldingMapper(persistedArc200Entities)
            
            PeraResult.Success(domainArc200Holdings)
        } catch (e: Exception) {
            PeraResult.Error(e)
        }
    }
} 
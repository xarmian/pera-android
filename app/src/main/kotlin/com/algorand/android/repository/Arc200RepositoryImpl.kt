package com.algorand.android.repository

import com.algorand.wallet.mapper.arc200.Arc200DtoToEntityMapper
import com.algorand.wallet.network.mimir.api.MimirApi
import com.algorand.wallet.network.mimir.model.Arc200ApiBalanceInfo
import com.algorand.wallet.account.info.data.database.dao.AssetHoldingDao
import com.algorand.wallet.account.info.data.database.model.AssetHoldingEntity
import com.algorand.wallet.asset.data.database.dao.AssetDetailDao
import com.algorand.wallet.asset.data.database.model.AssetDetailEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.algorand.android.network.request
import javax.inject.Inject
import com.algorand.android.models.Result
import com.algorand.wallet.account.info.domain.model.AssetHolding
import com.algorand.wallet.asset.domain.model.AssetDetail

class Arc200RepositoryImpl @Inject constructor(
    private val mimirApi: MimirApi,
    private val assetHoldingDao: AssetHoldingDao,
    private val assetDetailDao: AssetDetailDao,
    private val arc200DtoToEntityMapper: Arc200DtoToEntityMapper
) : Arc200Repository {

    override suspend fun refreshArc200CacheForAccount(accountId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Fetch all Balances using manual pagination
                val allBalances = mutableListOf<Arc200ApiBalanceInfo>()
                var nextToken: String? = null
                do {
                    val response = mimirApi.getArc200Balances(accountId, BALANCE_PAGINATION_LIMIT, nextToken)
                    if (!response.isSuccessful || response.body() == null) {
                        val errorMsg = "Failed to fetch ARC-200 balances for $accountId. Code: ${response.code()}"
                        throw Exception(errorMsg)
                    }
                    val responseBody = response.body()!!
                    allBalances.addAll(responseBody.balances.orEmpty())
                    nextToken = responseBody.nextToken
                } while (nextToken != null)

                // 2. Map DTOs to Entities directly from balance info
                val assetHoldingEntities = mutableListOf<AssetHoldingEntity>()
                val assetDetailEntities = mutableListOf<AssetDetailEntity>()
                val uniqueDetailIds = mutableSetOf<Long>()

                allBalances.forEach { balanceInfo ->
                    val contractId = balanceInfo.contractId ?: return@forEach
                    arc200DtoToEntityMapper.mapToAssetHoldingEntity(balanceInfo)?.let {
                        assetHoldingEntities.add(it)
                    }
                    if (uniqueDetailIds.add(contractId)) {
                        arc200DtoToEntityMapper.mapToAssetDetailEntity(balanceInfo)?.let {
                            assetDetailEntities.add(it)
                        }
                    }
                }

                // 3. Upsert Entities into DAOs
                assetDetailDao.insertAll(assetDetailEntities)
                assetHoldingDao.insertAll(assetHoldingEntities)

                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }

    override suspend fun getArc200AssetHolding(accountId: String, assetId: Long): Result<AssetHolding> {
        return withContext(Dispatchers.IO) {
            request { mimirApi.getArc200Balances(accountId, BALANCE_PAGINATION_LIMIT, null) }.run {
                when (this) {
                    is Result.Success -> {
                        val assetHoldingDto = data.balances?.firstOrNull { it.contractId == assetId }
                        if (assetHoldingDto == null) {
                            Result.Error(Exception("ARC-200 asset holding DTO not found in Mimir response for account: $accountId, asset ID: $assetId"))
                        } else {
                            val assetHoldingDomain = arc200DtoToEntityMapper.mapBalanceInfoToAssetHoldingDomain(assetHoldingDto)
                            if (assetHoldingDomain == null) {
                                Result.Error(Exception("Failed to map ARC-200 DTO to AssetHolding domain for asset ID: $assetId"))
                            } else {
                                Result.Success(assetHoldingDomain)
                            }
                        }
                    }
                    is Result.Error -> {
                        // Propagate the error
                        Result.Error(exception)
                    }
                }
            }
        }
    }

    override suspend fun getArc200AssetDetail(assetId: Long): Result<AssetDetail> {
        return withContext(Dispatchers.IO) {
            val result = request { mimirApi.getArc200Tokens(contractIds = assetId.toString()) }.run {
                when (this) {
                    is Result.Success -> {
                        val tokenDetail = data.tokens?.firstOrNull { it.contractId == assetId }
                        if (tokenDetail == null) {
                            Result.Error(Exception("ARC-200 asset detail not found in Mimir response for ID: $assetId"))
                        } else {
                            val assetDetail = arc200DtoToEntityMapper.mapTokenDetailToAssetDetail(tokenDetail)
                            if (assetDetail == null) {
                                Result.Error(Exception("Failed to map ARC-200 asset detail for ID: $assetId"))
                            } else {
                                Result.Success(assetDetail)
                            }
                        }
                    }
                    is Result.Error -> {
                        this
                    }
                }
            }
            result
        }
    }

    companion object {
        private const val BALANCE_PAGINATION_LIMIT = 100
    }
}

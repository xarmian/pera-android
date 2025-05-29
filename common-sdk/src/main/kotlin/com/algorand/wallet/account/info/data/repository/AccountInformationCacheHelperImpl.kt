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

package com.algorand.wallet.account.info.data.repository

import com.algorand.wallet.account.custom.domain.model.CustomAccountInfo
import com.algorand.wallet.account.custom.domain.usecase.GetAccountCustomInfoOrNull
import com.algorand.wallet.account.custom.domain.usecase.SetAccountCustomInfo
import com.algorand.wallet.account.info.data.cache.AccountInformationErrorCache
import com.algorand.wallet.account.info.data.database.dao.AccountInformationDao
import com.algorand.wallet.account.info.data.database.model.AccountInformationEntity
import com.algorand.wallet.account.info.data.mapper.entity.AccountInformationEntityMapper
import com.algorand.wallet.account.info.data.mapper.model.AccountInformationMapper
import com.algorand.wallet.account.info.data.model.AccountInformationResponse
import com.algorand.wallet.account.info.domain.model.AccountInformation
import com.algorand.wallet.account.info.domain.model.AssetHolding
import com.algorand.wallet.foundation.PeraResult
import javax.inject.Inject

internal class AccountInformationCacheHelperImpl @Inject constructor(
    private val accountInformationEntityMapper: AccountInformationEntityMapper,
    private val accountInformationMapper: AccountInformationMapper,
    private val accountInformationDao: AccountInformationDao,
    private val assetHoldingCacheHelper: AssetHoldingCacheHelper,
    private val arc200BalanceCacheUpdater: Arc200BalanceCacheUpdater,
    private val accountInformationErrorCache: AccountInformationErrorCache,
    private val getAccountCustomInfoOrNull: GetAccountCustomInfoOrNull,
    private val setAccountCustomInfo: SetAccountCustomInfo
) : AccountInformationCacheHelper {

    override suspend fun cacheAccountInformation(
        address: String,
        response: AccountInformationResponse
    ): AccountInformation? {
        val accountEntity = accountInformationEntityMapper(response)
        if (accountEntity == null) {
            if (!accountInformationDao.isAddressExists(address)) {
                accountInformationErrorCache.put(address)
            }
            return null
        }

        val asaDomainHoldings = assetHoldingCacheHelper.cacheAssetHolding(
            address,
            response.accountInformation?.allAssetHoldingList.orEmpty()
        )

        val arc200FetchResult = arc200BalanceCacheUpdater.fetchAndPersistArc200Balances(address)
        val arc200DomainHoldings: List<AssetHolding>

        if (arc200FetchResult is PeraResult.Success) {
            arc200DomainHoldings = arc200FetchResult.data
        } else {
            accountInformationErrorCache.put(address)
            return null
        }

        accountInformationErrorCache.remove(address)

        val combinedDomainHoldings = asaDomainHoldings + arc200DomainHoldings

        return cacheAccountInformation(accountEntity, combinedDomainHoldings)
    }

    private suspend fun cacheAccountInformation(
        entity: AccountInformationEntity,
        assetHoldings: List<AssetHolding>
    ): AccountInformation {
        accountInformationDao.insert(entity)

        ensureCustomAccountInfoExists(entity.algoAddress)

        return accountInformationMapper(entity, assetHoldings)
    }

    private suspend fun ensureCustomAccountInfoExists(address: String) {
        val existingCustomInfo = getAccountCustomInfoOrNull(address)
        if (existingCustomInfo == null) {
            val defaultCustomInfo = CustomAccountInfo(
                address = address,
                customName = null,
                orderIndex = 0,
                isBackedUp = false
            )
            setAccountCustomInfo(defaultCustomInfo)
        }
    }
}

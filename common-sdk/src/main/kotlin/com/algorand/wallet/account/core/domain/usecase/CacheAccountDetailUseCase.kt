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

package com.algorand.wallet.account.core.domain.usecase

import com.algorand.wallet.account.info.domain.model.AccountInformation
import com.algorand.wallet.account.info.domain.usecase.FetchAndCacheAccountInformation
import com.algorand.wallet.asset.domain.usecase.FetchAndCacheAssets
import com.algorand.wallet.foundation.PeraResult
import javax.inject.Inject

internal class CacheAccountDetailUseCase @Inject constructor(
    private val fetchAndCacheAccountInformation: FetchAndCacheAccountInformation,
    private val fetchAndCacheAssets: FetchAndCacheAssets
) : CacheAccountDetail {

    override suspend fun invoke(address: String): PeraResult<AccountInformation> {
        val accountInformationMap = fetchAndCacheAccountInformation(listOf(address))
        val accountInformation = accountInformationMap[address]
            ?: return PeraResult.Error(Exception("Failed to fetch account information for $address"))
        
        // Filter out ARC200 assets before calling fetchAndCacheAssets
        val nonArc200AssetHoldingIds = accountInformation.assetHoldings
            .filterNot { it.isArc200() }
            .map { it.assetId }

        if (nonArc200AssetHoldingIds.isNotEmpty()) {
            fetchAndCacheAssets(nonArc200AssetHoldingIds, false)
        }
        
        // The AccountInformation object already contains ARC200 holdings and their details (including price if available)
        // because Arc200BalanceCacheUpdater (called by FetchAndCacheAccountInformation) now handles them.
        return PeraResult.Success(accountInformation)
    }
}

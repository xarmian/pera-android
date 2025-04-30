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

package com.algorand.android.nft.domain.usecase

import com.algorand.android.modules.collectibles.filter.domain.usecase.ClearCollectibleFiltersPreferencesUseCase
import com.algorand.android.modules.collectibles.filter.domain.usecase.ShouldDisplayOptedInNFTPreferenceUseCase
import com.algorand.android.modules.collectibles.listingviewtype.domain.usecase.AddOnListingViewTypeChangeListenerUseCase
import com.algorand.android.modules.collectibles.listingviewtype.domain.usecase.GetNFTListingViewTypePreferenceUseCase
import com.algorand.android.modules.collectibles.listingviewtype.domain.usecase.RemoveOnListingViewTypeChangeListenerUseCase
import com.algorand.android.modules.collectibles.listingviewtype.domain.usecase.SaveNFTListingViewTypePreferenceUseCase
import com.algorand.android.modules.sorting.nftsorting.ui.usecase.CollectibleItemSortUseCase
import com.algorand.android.nft.mapper.CollectibleListingItemMapper
import com.algorand.android.nft.ui.model.BaseCollectibleListData
import com.algorand.android.nft.ui.model.BaseCollectibleListItem
import com.algorand.android.nft.ui.model.CollectiblesListingPreview
import com.algorand.android.repository.FailedAssetRepository
import com.algorand.wallet.account.core.domain.usecase.GetAccountDetailFlow
import com.algorand.wallet.account.detail.domain.model.AccountDetail
import com.algorand.wallet.account.detail.domain.model.AccountType
import com.algorand.wallet.account.info.domain.usecase.IsAssetOwnedByAccount
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import com.algorand.android.repository.NftRepository
import com.algorand.android.mapper.MimirNftItemMapper
import com.algorand.android.models.Result
import com.algorand.android.nft.mapper.NftDomainToListItemMapper
import com.algorand.android.models.ui.nft.NftDomainItem

// Data class to hold NFT fetch result including pagination info
data class NftFetchResult(
    val items: List<NftDomainItem>,
    val nextToken: String?,
    val totalCount: Long?
)

@SuppressWarnings("LongParameterList")
class AccountCollectiblesListingPreviewUseCase @Inject constructor(
    private val collectibleListingItemMapper: CollectibleListingItemMapper,
    private val failedAssetRepository: FailedAssetRepository,
    private val collectibleItemSortUseCase: CollectibleItemSortUseCase,
    private val getNFTListingViewTypePreferenceUseCase: GetNFTListingViewTypePreferenceUseCase,
    private val getAccountDetailFlow: GetAccountDetailFlow,
    private val nftRepository: NftRepository,
    private val mimirNftItemMapper: MimirNftItemMapper,
    private val nftDomainToListItemMapper: NftDomainToListItemMapper,
    isAssetOwnedByAccount: IsAssetOwnedByAccount,
    clearCollectibleFiltersPreferencesUseCase: ClearCollectibleFiltersPreferencesUseCase,
    shouldDisplayOptedInNFTPreferenceUseCase: ShouldDisplayOptedInNFTPreferenceUseCase,
    addOnListingViewTypeChangeListenerUseCase: AddOnListingViewTypeChangeListenerUseCase,
    removeOnListingViewTypeChangeListenerUseCase: RemoveOnListingViewTypeChangeListenerUseCase,
    saveNFTListingViewTypePreferenceUseCase: SaveNFTListingViewTypePreferenceUseCase
) : BaseCollectiblesListingPreviewUseCase(
    collectibleListingItemMapper,
    saveNFTListingViewTypePreferenceUseCase,
    addOnListingViewTypeChangeListenerUseCase,
    removeOnListingViewTypeChangeListenerUseCase,
    shouldDisplayOptedInNFTPreferenceUseCase,
    clearCollectibleFiltersPreferencesUseCase,
    isAssetOwnedByAccount
) {

    fun getCollectiblesListingPreviewFlow(searchKeyword: String, publicKey: String): Flow<CollectiblesListingPreview> {
        val limit = INITIAL_NFT_FETCH_LIMIT
        // Update Flow type to emit NftFetchResult
        val nftDataFlow: Flow<NftFetchResult> = flow {
            when (val result = nftRepository.getAccountNftsFromMimir(publicKey, limit, null)) {
                is Result.Success -> {
                    val domainItems = result.data.items.mapNotNull { mimirNftItemMapper.mapToDomainModel(it) }
                    val fetchResult = NftFetchResult(
                        items = domainItems.filter { !it.imageUrl.isNullOrBlank() },
                        nextToken = result.data.nextToken,
                        totalCount = result.data.totalCount
                    )
                    emit(fetchResult)
                }
                is Result.Error -> {
                    // Emit empty result on error, maybe handle error state better later
                    emit(NftFetchResult(emptyList(), null, null))
                }
            }
        }

        return combine(
            getAccountDetailFlow(publicKey),
            failedAssetRepository.getFailedAssetCacheFlow(),
            nftDataFlow,
            // Fetch listing type preference once and combine it
            flow { emit(getNFTListingViewTypePreferenceUseCase()) }
        ) { accountDetail, failedAssets, nftFetchResult, nftListingType ->
            // Now combine produces all needed data directly
            // Use safe calls or elvis operators for nullable accountDetail
            val hasAccountAuthority = accountDetail?.canSignTransaction() ?: false
            val accountType = accountDetail?.accountType ?: AccountType.Algo25

            val mappedItemList = nftFetchResult.items.mapNotNull { domainItem ->
                nftDomainToListItemMapper.mapToOwnedCollectibleListItem(
                    domain = domainItem,
                    accountAddress = publicKey,
                    accountType = accountType,
                    nftListingViewType = nftListingType // Use listing type from combine
                )
            }

            val filteredList = mappedItemList
            val displayedCollectibleCount = nftFetchResult.totalCount?.toInt() ?: filteredList.size
            val filteredOutCollectibleCount = 0 // Keep simplified or adjust if needed

            val collectibleListData = BaseCollectibleListData(
                baseCollectibleItemList = collectibleItemSortUseCase.sortCollectibles(filteredList),
                displayedCollectibleCount = displayedCollectibleCount,
                filteredOutCollectibleCount = filteredOutCollectibleCount
            )

            val isAllCollectiblesFilteredOut = isAllCollectiblesFilteredOut(collectibleListData, searchKeyword)
            val isEmptyStateVisible = nftFetchResult.items.isEmpty() || isAllCollectiblesFilteredOut

            val itemList = mutableListOf<BaseCollectibleListItem>().apply {
                if (!isEmptyStateVisible) {
                    add(createSearchViewItem(query = searchKeyword, nftListingType = nftListingType))
                    add(
                        ACCOUNT_COLLECTIBLES_LIST_CONFIGURATION_HEADER_ITEM_INDEX,
                        createInfoViewItem(
                            displayedCollectibleCount = collectibleListData.displayedCollectibleCount,
                            isAddButtonVisible = hasAccountAuthority
                        )
                    )
                }
                addAll(collectibleListData.baseCollectibleItemList)
            }

            collectibleListingItemMapper.mapToPreviewItem(
                isLoadingVisible = false, // Should be handled by ViewModel state
                isEmptyStateVisible = isEmptyStateVisible,
                isErrorVisible = failedAssets.isNotEmpty(), // Error state handled by ViewModel
                itemList = itemList,
                isReceiveButtonVisible = isEmptyStateVisible && hasAccountAuthority,
                filteredCollectibleCount = collectibleListData.filteredOutCollectibleCount,
                isClearFilterButtonVisible = isAllCollectiblesFilteredOut,
                isAccountFabVisible = hasAccountAuthority,
                isAddCollectibleFloatingActionButtonVisible = hasAccountAuthority,
                nextToken = nftFetchResult.nextToken,
                totalCount = nftFetchResult.totalCount
            )
        }
    }

    private fun hasAccountAuthority(accountDetail: AccountDetail?): Boolean {
        return accountDetail?.canSignTransaction() ?: false
    }

    companion object {
        const val ACCOUNT_COLLECTIBLES_LIST_CONFIGURATION_HEADER_ITEM_INDEX = 0
        private const val INITIAL_NFT_FETCH_LIMIT = 50
    }
}

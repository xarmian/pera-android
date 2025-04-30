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

package com.algorand.android.modules.accountdetail.collectibles.ui

import androidx.lifecycle.SavedStateHandle
import com.algorand.android.modules.accountdetail.collectibles.ui.AccountCollectiblesFragment.Companion.PUBLIC_KEY
import com.algorand.android.modules.tracking.nft.CollectibleEventTracker
import com.algorand.android.nft.domain.usecase.AccountCollectiblesListingPreviewUseCase
import com.algorand.android.nft.ui.base.BaseCollectibleListingViewModel
import com.algorand.android.nft.ui.model.CollectiblesListingPreview
import com.algorand.android.sharedpref.SharedPrefLocalSource
import com.algorand.android.utils.getOrThrow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.algorand.android.nft.domain.usecase.NftFetchResult
import com.algorand.android.repository.NftRepository
import com.algorand.android.mapper.MimirNftItemMapper
import com.algorand.android.models.Result
import com.algorand.android.nft.ui.model.BaseCollectibleListItem
import com.algorand.android.nft.mapper.NftDomainToListItemMapper
import com.algorand.wallet.account.core.domain.usecase.GetAccountDetailFlow
import com.algorand.wallet.account.detail.domain.model.AccountType
import com.algorand.android.modules.sorting.nftsorting.ui.usecase.CollectibleItemSortUseCase
import com.algorand.android.modules.collectibles.listingviewtype.domain.usecase.GetNFTListingViewTypePreferenceUseCase
import com.algorand.android.nft.ui.model.BaseCollectibleListData
import com.algorand.android.nft.mapper.CollectibleListingItemMapper
import androidx.lifecycle.viewModelScope
import com.algorand.android.models.ui.nft.NftDomainItem
import com.algorand.android.repository.FailedAssetRepository
import kotlinx.coroutines.flow.combine

@HiltViewModel
class AccountCollectiblesViewModel @Inject constructor(
    private val collectiblesPreviewUseCase: AccountCollectiblesListingPreviewUseCase,
    private val nftRepository: NftRepository,
    private val mimirNftItemMapper: MimirNftItemMapper,
    private val nftDomainToListItemMapper: NftDomainToListItemMapper,
    private val getAccountDetailFlow: GetAccountDetailFlow,
    private val collectibleItemSortUseCase: CollectibleItemSortUseCase,
    private val getNFTListingViewTypePreferenceUseCase: GetNFTListingViewTypePreferenceUseCase,
    private val collectibleListingItemMapper: CollectibleListingItemMapper,
    private val failedAssetRepository: FailedAssetRepository,
    savedStateHandle: SavedStateHandle,
    collectibleEventTracker: CollectibleEventTracker
) : BaseCollectibleListingViewModel(collectiblesPreviewUseCase, collectibleEventTracker) {

    companion object {
        private const val NFT_FETCH_LIMIT = 50
        const val ACCOUNT_COLLECTIBLES_LIST_CONFIGURATION_HEADER_ITEM_INDEX = 0
    }

    private val accountPublicKey: String = savedStateHandle.getOrThrow(PUBLIC_KEY)

    private val _collectiblesState = MutableStateFlow(CollectiblesUiState())
    val collectiblesState: StateFlow<CollectiblesUiState> = _collectiblesState

    private var currentSearchKeyword = ""
    private var isFetching = false

    // Define the listener before the init block
    override val nftListingViewTypeChangeListener =
        SharedPrefLocalSource.OnChangeListener<Int> {
            fetchInitialNfts(currentSearchKeyword)
        }

    init {
        // Register the listener for view type changes
        collectiblesPreviewUseCase.addOnListingViewTypeChangeListener(nftListingViewTypeChangeListener)
        fetchInitialNfts()
    }

    private fun fetchInitialNfts(searchKeyword: String = "") {
        currentSearchKeyword = searchKeyword
        _collectiblesState.value = CollectiblesUiState(isLoading = true)
        fetchNfts(nextToken = null, isInitialFetch = true)
    }

    fun loadMoreNfts() {
        val currentState = _collectiblesState.value
        if (!isFetching && currentState.nextToken != null && !currentState.isLoadingMore) {
            _collectiblesState.update { it.copy(isLoadingMore = true) }
            fetchNfts(nextToken = currentState.nextToken, isInitialFetch = false)
        }
    }

    private fun fetchNfts(nextToken: String?, isInitialFetch: Boolean) {
        if (isFetching) return
        isFetching = true

        viewModelScope.launch {
            val fetchResult = when (val result = nftRepository.getAccountNftsFromMimir(
                accountPublicKey,
                NFT_FETCH_LIMIT,
                nextToken
            )) {
                is Result.Success -> {
                    val domainItems =
                        result.data.items.mapNotNull { mimirNftItemMapper.mapToDomainModel(it) }
                    NftFetchResult(
                        items = domainItems.filter { !it.imageUrl.isNullOrBlank() },
                        nextToken = result.data.nextToken,
                        totalCount = result.data.totalCount
                    )
                }

                is Result.Error -> {
                    _collectiblesState.update {
                        it.copy(
                            isError = true,
                            isLoading = false,
                            isLoadingMore = false
                        )
                    }
                    isFetching = false
                    return@launch
                }
            }

            combine(
                getAccountDetailFlow(accountPublicKey),
                failedAssetRepository.getFailedAssetCacheFlow()
            ) { accountDetail, failedAssets -> Pair(accountDetail, failedAssets) }
                .collect { (accountDetail, failedAssets) ->

                    val currentItems =
                        if (isInitialFetch) emptyList() else _collectiblesState.value.listItems
                    val newItems = mapToUiItems(
                        fetchResult.items,
                        accountPublicKey,
                        accountDetail?.accountType ?: AccountType.Algo25
                    )
                    val combinedItems = currentItems + newItems

                    val hasAccountAuthority = accountDetail?.canSignTransaction() ?: false
                    val nftListingType = getNFTListingViewTypePreferenceUseCase()
                    val collectibleListData = BaseCollectibleListData(
                        baseCollectibleItemList = collectibleItemSortUseCase.sortCollectibles(
                            combinedItems
                        ),
                        displayedCollectibleCount = fetchResult.totalCount?.toInt()
                            ?: combinedItems.size,
                        filteredOutCollectibleCount = 0
                    )

                    val isAllCollectiblesFilteredOut = false
                    val isEmptyStateVisible = combinedItems.isEmpty() && isInitialFetch

                    val finalItemList = mutableListOf<BaseCollectibleListItem>().apply {
                        if (!isEmptyStateVisible) {
                            if (isInitialFetch || currentItems.none { it is BaseCollectibleListItem.SearchViewItem }) {
                                add(
                                    collectiblesPreviewUseCase.createSearchViewItem(
                                        query = currentSearchKeyword,
                                        nftListingType = nftListingType
                                    )
                                )
                                add(
                                    ACCOUNT_COLLECTIBLES_LIST_CONFIGURATION_HEADER_ITEM_INDEX,
                                    collectiblesPreviewUseCase.createInfoViewItem(
                                        displayedCollectibleCount = collectibleListData.displayedCollectibleCount,
                                        isAddButtonVisible = hasAccountAuthority
                                    )
                                )
                            }
                        }
                        addAll(collectibleListData.baseCollectibleItemList)
                    }

                    _collectiblesState.update {
                        it.copy(
                            listItems = finalItemList,
                            nextToken = fetchResult.nextToken,
                            totalCount = fetchResult.totalCount,
                            isLoading = false,
                            isLoadingMore = false,
                            isError = false,
                            isEmpty = isEmptyStateVisible,
                            preview = collectibleListingItemMapper.mapToPreviewItem(
                                isLoadingVisible = false,
                                isEmptyStateVisible = isEmptyStateVisible,
                                isErrorVisible = failedAssets.isNotEmpty() || it.isError,
                                itemList = finalItemList,
                                isReceiveButtonVisible = isEmptyStateVisible && hasAccountAuthority,
                                filteredCollectibleCount = collectibleListData.filteredOutCollectibleCount,
                                isClearFilterButtonVisible = isAllCollectiblesFilteredOut,
                                isAccountFabVisible = hasAccountAuthority,
                                isAddCollectibleFloatingActionButtonVisible = hasAccountAuthority,
                                nextToken = fetchResult.nextToken,
                                totalCount = fetchResult.totalCount
                            )
                        )
                    }
                    isFetching = false
                }
        }
    }

    private suspend fun mapToUiItems(
        domainItems: List<NftDomainItem>,
        publicKey: String,
        accountType: AccountType
    ): List<BaseCollectibleListItem.BaseCollectibleItem> {
        val nftListingType = getNFTListingViewTypePreferenceUseCase()
        return domainItems.map {
            nftDomainToListItemMapper.mapToOwnedCollectibleListItem(
                domain = it,
                accountAddress = publicKey,
                accountType = accountType,
                nftListingViewType = nftListingType
            )
        }
    }

    override suspend fun initCollectiblesListingPreviewFlow(searchKeyword: String): Flow<CollectiblesListingPreview> {
        return kotlinx.coroutines.flow.emptyFlow()
    }
}

data class CollectiblesUiState(
    val listItems: List<BaseCollectibleListItem> = emptyList(),
    val nextToken: String? = null,
    val totalCount: Long? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isError: Boolean = false,
    val isEmpty: Boolean = false,
    val preview: CollectiblesListingPreview? = null
)

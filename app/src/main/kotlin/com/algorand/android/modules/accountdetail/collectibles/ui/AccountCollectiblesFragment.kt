/*
 * Copyright 2025 Pera Wallet, LDA
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License
 *
 */

package com.algorand.android.modules.accountdetail.collectibles.ui

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.algorand.android.models.FragmentConfiguration
import com.algorand.android.nft.domain.usecase.AccountCollectiblesListingPreviewUseCase.Companion.ACCOUNT_COLLECTIBLES_LIST_CONFIGURATION_HEADER_ITEM_INDEX
import com.algorand.android.nft.ui.nftlisting.BaseCollectiblesListingFragment
import com.algorand.android.utils.addItemVisibilityChangeListener
import dagger.hilt.android.AndroidEntryPoint
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.View
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import com.algorand.android.models.ui.nft.NftDomainItem

@AndroidEntryPoint
class AccountCollectiblesFragment : BaseCollectiblesListingFragment() {

    override val fragmentConfiguration = FragmentConfiguration()

    override val baseCollectibleListingViewModel: AccountCollectiblesViewModel by viewModels()

    private var listener: Listener? = null

    // Scroll listener for pagination
    private val paginationScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            if (dy > 0) { // Check if scrolling down
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount
                val currentState = baseCollectibleListingViewModel.collectiblesState.value

                // Load more items when near the end of the list
                // Threshold: Load when last item is visible (or adjust as needed)
                if (!currentState.isLoadingMore && currentState.nextToken != null &&
                    lastVisibleItemPosition == totalItemCount - 1) {
                    baseCollectibleListingViewModel.loadMoreNfts()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.collectiblesRecyclerView.addOnScrollListener(paginationScrollListener)
    }

    override fun onStop() {
        // Remove listener in onStop() where the view and binding are still valid
        // Check if binding is initialized before accessing (though it should be here)
        if (view != null) { // Check if view is not null, binding relies on view
            binding.collectiblesRecyclerView.removeOnScrollListener(paginationScrollListener)
        }
        super.onStop()
    }

    override fun onDestroyView() {
        // No need to remove listener here anymore
        super.onDestroyView()
    }

    override fun onOwnedNFTItemClick(collectibleAssetId: Long, tokenId: String) {
        listener?.onCollectibleClick(collectibleAssetId, tokenId)
    }

    override fun onReceiveCollectibleClick() {
        super.onReceiveCollectibleClick()
        listener?.onReceiveCollectibleClick()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as? Listener
    }

    override fun initCollectiblesListingPreviewCollector() {
        // Collect the new state flow
        viewLifecycleOwner.lifecycleScope.launch {
            baseCollectibleListingViewModel.collectiblesState.flowWithLifecycle(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.STARTED
            ).collect { state ->
                // Use the correct adapter name from the base class
                collectibleListAdapter.submitList(state.listItems)
                // Update UI based on state - use binding
                binding.progressBar.root.isVisible = state.isLoading
                binding.emptyStateScrollView.isVisible = state.isEmpty
                // TODO: Add handling for isLoadingMore (e.g., show a footer spinner in adapter)
                // TODO: Add handling for isError (e.g., show an error message/view)

                // FAB logic - Set visibility directly using binding
                // Use the isAccountFabVisible from the state.preview for now
                val isFabVisible = state.preview?.isAccountFabVisible ?: false
                binding.addCollectibleFloatingActionButton.isVisible = isFabVisible
                // We might need to call addItemVisibilityChangeListenerToRecyclerView again if the FAB logic
                // in the base class relied on the old flow structure.
                // For now, let's just set visibility.
            }
        }
    }

    override fun addItemVisibilityChangeListenerToRecyclerView(recyclerView: RecyclerView) {
        recyclerView.addItemVisibilityChangeListener(
            ACCOUNT_COLLECTIBLES_LIST_CONFIGURATION_HEADER_ITEM_INDEX
        ) { isVisible -> onListItemConfigurationHeaderItemVisibilityChange(isVisible) }
    }

    override fun onAddCollectibleFloatingActionButtonClicked() {
        listener?.onReceiveCollectibleClick()
    }

    override fun onManageCollectiblesClick() {
        listener?.onManageCollectiblesClick()
    }

    interface Listener {
        fun onSendNFTClick(nftId: Long, nftDomain: NftDomainItem)
        fun onCollectiblesFilterClick()
        fun onCollectibleClick(collectibleAssetId: Long, tokenId: String)
        fun onReceiveNFTsClick()
        fun onBuySellClick()
        fun onImageItemClick(nftAssetId: Long)
        fun onVideoItemClick(nftAssetId: Long)
        fun onSoundItemClick(nftAssetId: Long)
        fun onGifItemClick(nftAssetId: Long)
        fun onNotSupportedItemClick(nftAssetId: Long)
        fun onMixedItemClick(nftAssetId: Long)
        fun onReceiveCollectibleClick()
        fun onManageCollectiblesClick()
    }

    companion object {
        const val PUBLIC_KEY = "public_key"
        fun newInstance(publicKey: String): AccountCollectiblesFragment {
            return AccountCollectiblesFragment().apply {
                arguments = Bundle().apply { putString(PUBLIC_KEY, publicKey) }
            }
        }
    }
}

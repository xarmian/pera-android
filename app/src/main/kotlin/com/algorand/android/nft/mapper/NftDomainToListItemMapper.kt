package com.algorand.android.nft.mapper

import com.algorand.android.models.ui.nft.NftDomainItem
import com.algorand.android.modules.collectibles.listingviewtype.domain.model.NFTListingViewType
import com.algorand.android.nft.ui.model.BaseCollectibleListItem
import com.algorand.android.utils.AssetName
import com.algorand.android.decider.AssetDrawableProviderDecider
import com.algorand.android.nft.domain.decider.BaseCollectibleListItemItemTypeDecider
import com.algorand.android.modules.accountdetail.assets.ui.decider.NFTIndicatorDrawableDecider
import com.algorand.wallet.account.detail.domain.model.AccountType
import javax.inject.Inject

class NftDomainToListItemMapper @Inject constructor(
    private val assetDrawableProviderDecider: AssetDrawableProviderDecider,
    private val nftIndicatorDrawableDecider: NFTIndicatorDrawableDecider,
    private val baseCollectibleListItemItemTypeDecider: BaseCollectibleListItemItemTypeDecider
    // TODO: Inject NFTAmountFormatDecider if more complex amount formatting is needed later
) {

    // Added suspend keyword
    suspend fun mapToOwnedCollectibleListItem(
        domain: NftDomainItem,
        accountAddress: String,
        accountType: AccountType,
        nftListingViewType: NFTListingViewType
    ): BaseCollectibleListItem.BaseCollectibleItem.BaseOwnedNFTItem.SimpleNFTItem {

        // Assuming amount=1, decimals=0 for typical NFTs for now
        val formattedAmount = "1"

        // Handle IPFS URLs
        val displayImageUrl = when {
            domain.imageUrl?.startsWith(IPFS_PREFIX) == true -> {
                IPFS_GATEWAY + domain.imageUrl.removePrefix(IPFS_PREFIX)
            }
            else -> domain.imageUrl
        }

        return BaseCollectibleListItem.BaseCollectibleItem.BaseOwnedNFTItem.SimpleNFTItem(
            collectibleId = domain.asaId,
            tokenId = domain.tokenId,
            collectibleName = AssetName.create(domain.name),
            collectionName = domain.collectionName,
            optedInAccountAddress = accountAddress,
            optedInAtRound = null, // Not available from Mimir NFT indexer
            isAmountVisible = false,
            formattedCollectibleAmount = formattedAmount,
            // Corrected call to use suspend function and correct parameters
            baseAssetDrawableProvider = assetDrawableProviderDecider.getAssetDrawableProvider(
                assetId = domain.asaId,
                assetName = AssetName.create(domain.name),
                logoUri = displayImageUrl
            ),
            nftIndicatorDrawable = nftIndicatorDrawableDecider.decideNFTIndicatorDrawable(
                isOwned = domain.isOwned,
                isHoldingByWatchAccount = (accountType == AccountType.NoAuth),
                nftListingViewType = nftListingViewType
            ),
            shouldDecreaseOpacity = false, // Standard display
            // Corrected call to use existing function name
            itemType = baseCollectibleListItemItemTypeDecider.decideSimpleNFTViewType(nftListingViewType)
        )
    }

    companion object {
        private const val IPFS_PREFIX = "ipfs://"
        private const val IPFS_GATEWAY = "https://ipfs.io/ipfs/" // Use a public gateway
    }
}

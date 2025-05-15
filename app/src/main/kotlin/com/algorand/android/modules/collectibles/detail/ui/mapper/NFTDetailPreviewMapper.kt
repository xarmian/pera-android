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

package com.algorand.android.modules.collectibles.detail.ui.mapper

import androidx.core.net.toUri
import com.algorand.android.modules.accountcore.ui.usecase.GetAccountDisplayName
import com.algorand.android.modules.accountcore.ui.model.AccountDisplayName
import com.algorand.android.modules.accountcore.ui.usecase.GetAccountIconDrawablePreview
import com.algorand.android.utils.AssetName as UtilsAssetName
import com.algorand.android.modules.assets.core.ui.domain.usecase.GetAssetName
import com.algorand.android.modules.collectibles.detail.base.ui.mapper.CollectibleMediaItemMapper
import com.algorand.android.modules.collectibles.detail.base.ui.mapper.CollectibleTraitItemMapper
import com.algorand.android.modules.collectibles.detail.base.ui.model.BaseCollectibleMediaItem
import com.algorand.android.modules.collectibles.detail.base.ui.model.CollectibleTraitItem
import com.algorand.android.modules.collectibles.detail.ui.model.NFTDetailPreview
import com.algorand.android.modules.collectibles.util.deciders.NFTAmountFormatDecider
import com.algorand.wallet.network.mimir.model.MimirNftItemDto
import com.algorand.wallet.network.mimir.model.MimirNftMetadataDto
import com.algorand.wallet.account.detail.domain.usecase.GetAccountDetail
import java.math.BigDecimal
import javax.inject.Inject
import java.io.File
import com.algorand.android.utils.assetdrawable.CollectibleDrawableProvider

class NFTDetailPreviewMapper @Inject constructor(
    private val collectibleMediaItemMapper: CollectibleMediaItemMapper,
    private val collectibleTraitItemMapper: CollectibleTraitItemMapper,
    private val nftAmountFormatDecider: NFTAmountFormatDecider,
    private val getAccountDisplayName: GetAccountDisplayName,
    private val getAccountDetail: GetAccountDetail,
    private val getAccountIconDrawablePreview: GetAccountIconDrawablePreview,
    private val getAssetName: GetAssetName
) {

    suspend fun mapToPreview(
        nftItemDto: MimirNftItemDto,
        metadataDto: MimirNftMetadataDto?,
        accountPublicKey: String
    ): NFTDetailPreview? {

        val contractId = nftItemDto.contractId ?: return null
        val tokenId = nftItemDto.tokenId ?: return null
        val ownerAddress = nftItemDto.owner

        val viewingAccountIconPreview = getAccountIconDrawablePreview.invoke(accountPublicKey)
        val viewingAccountTypeResId = viewingAccountIconPreview.iconResId
        val viewingAccountDisplayName = getAccountDisplayName.invoke(accountPublicKey)

        val creatorDisplayName = if (ownerAddress != null) {
            // getAccountDisplayName.invoke(ownerAddress)
            AccountDisplayName(
                accountAddress = "",
                primaryDisplayName = "-",
                secondaryDisplayName = null
            )
        } else {
            AccountDisplayName(
                accountAddress = "",
                primaryDisplayName = "-",
                secondaryDisplayName = null
            )
        }

        val isOwnedByViewingAccount = ownerAddress == accountPublicKey
        val isOwnerActionsGroupVisible = isOwnedByViewingAccount
        val isOptOutButtonVisible = isOwnedByViewingAccount

        val nameToUse = metadataDto?.name ?: contractId.toString()
        val utilsNftName = UtilsAssetName.create(nameToUse)

        val mediaList = mapToMediaList(nftItemDto, metadataDto, utilsNftName)

        val traitList = mapToTraitList(metadataDto)

        val formattedAmount = nftAmountFormatDecider.decideNFTAmountFormat(
            nftAmount = BigDecimal.ONE,
            fractionalDecimal = 0
        )
        val formattedTotalSupply = formattedAmount

        val domainNftName = getAssetName.invoke(nameToUse)

        val collectionName = nftItemDto.collectionName
        val description = metadataDto?.description
        val explorerUrl = "https://nftnavigator.xyz/collection/$contractId/token/$tokenId"
        val isPure = true

        val primaryWarningResId: Int? = null
        val secondaryWarningResId: Int? = null

        val isCopyEnabled = mediaList.firstOrNull()?.itemType == BaseCollectibleMediaItem.ItemType.IMAGE && isOwnerActionsGroupVisible

        return NFTDetailPreview(
            nftId = contractId,
            tokenId = tokenId,
            nftName = domainNftName,
            collectionNameOfNFT = collectionName,
            optedInAccountTypeDrawableResId = viewingAccountTypeResId,
            optedInAccountDisplayName = viewingAccountDisplayName,
            formattedNFTAmount = formattedAmount,
            mediaListOfNFT = mediaList,
            traitListOfNFT = traitList,
            nftDescription = description,
            creatorAccountAddressOfNFT = creatorDisplayName,
            formattedTotalSupply = formattedTotalSupply,
            peraExplorerUrl = explorerUrl,
            isPureNFT = isPure,
            primaryWarningResId = primaryWarningResId,
            secondaryWarningResId = secondaryWarningResId,
            isOwnerActionsGroupVisible = isOwnerActionsGroupVisible,
            isOptOutButtonVisible = isOptOutButtonVisible,
            isCopyEnabled = isCopyEnabled,
            globalErrorEvent = null,
            nftSendEvent = null
        )
    }

    private fun mapToTraitList(metadataDto: MimirNftMetadataDto?): List<CollectibleTraitItem> {
        return metadataDto?.properties?.mapNotNull { entry ->
            val description = entry.value ?: ""
            CollectibleTraitItem(title = entry.key, description = description)
        } ?: emptyList()
    }

    private fun mapToMediaList(
        nftItemDto: MimirNftItemDto,
        metadataDto: MimirNftMetadataDto?,
        nftName: UtilsAssetName
    ): List<BaseCollectibleMediaItem> {
        val imageUrl = metadataDto?.image ?: return emptyList()
        val contractId = nftItemDto.contractId ?: return emptyList()

        val drawableProvider = CollectibleDrawableProvider(
            assetName = nftName,
            logoUri = imageUrl
        )

        val fileExtension = try {
            val path = imageUrl.toUri().path
            if (path != null) File(path).extension else null
        } catch (e: Exception) {
            null
        }
        val mediaExtension = fileExtension?.let { ".$it" }

        return listOf(
            BaseCollectibleMediaItem.ImageCollectibleMediaItem(
                collectibleId = contractId,
                previewUrl = imageUrl,
                downloadUrl = imageUrl,
                shouldDecreaseOpacity = false,
                baseAssetDrawableProvider = drawableProvider,
                mediaExtension = mediaExtension,
                has3dSupport = false,
                hasFullScreenSupport = false,
                showPlayButton = false
            )
        )
    }
}

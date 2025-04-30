package com.algorand.android.mapper

import com.algorand.android.models.ui.nft.NftDomainItem // Placeholder for domain model
import com.algorand.android.network.dto.MimirNftItemDto
import com.algorand.android.network.dto.MimirNftMetadataDto
import com.algorand.android.utils.fromJson
import com.google.gson.Gson
import javax.inject.Inject

class MimirNftItemMapper @Inject constructor(
    private val gson: Gson
) {

    fun mapToDomainModel(dto: MimirNftItemDto): NftDomainItem? {
        // Ensure essential fields like contractId are present
        val asaId = dto.contractId ?: return null

        val metadata: MimirNftMetadataDto? = dto.metadataJsonString?.let { jsonString ->
            gson.fromJson<MimirNftMetadataDto>(jsonString)
        }

        return NftDomainItem(
            asaId = asaId,
            name = metadata?.name ?: dto.collectionName ?: asaId.toString(),
            collectionName = dto.collectionName,
            description = metadata?.description,
            imageUrl = metadata?.image,
            ownerAddress = dto.owner,
            tokenId = dto.tokenId,
            isOwned = true, // Assume owned as it's fetched by owner address
            isVerified = dto.verified != 0, // Assuming 0 means not verified
            isBurned = dto.isBurned ?: false,
            isBlacklisted = dto.blacklisted ?: false,
            metadataUri = dto.metadataURI,
            lastUpdated = dto.lastUpdated
            // Add other relevant fields as needed
        )
    }
}

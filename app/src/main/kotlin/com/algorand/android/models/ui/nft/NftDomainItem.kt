package com.algorand.android.models.ui.nft

/**
 * Simple domain model representing core NFT information.
 */
data class NftDomainItem(
    val asaId: Long,
    val name: String,
    val collectionName: String?,
    val description: String?,
    val imageUrl: String?,
    val ownerAddress: String?,
    val tokenId: String?, // NFT ID within the contract
    val isOwned: Boolean,
    val isVerified: Boolean,
    val isBurned: Boolean,
    val isBlacklisted: Boolean,
    val metadataUri: String?,
    val lastUpdated: String?
    // Add other fields derived from DTO or needed for UI
)

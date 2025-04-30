package com.algorand.android.network.dto

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * Represents the top-level response structure for the Mimir API /nft-indexer/v1/tokens endpoint.
 */
data class MimirNftListResponse(
    @SerializedName("tokens")
    val tokens: List<MimirNftItemDto>?,
    @SerializedName("next-token")
    val nextToken: String?,
    @SerializedName("total-count")
    val totalCount: Long?,
    @SerializedName("current-round")
    val currentRound: Long?
)

/**
 * Represents a single NFT item returned by the Mimir API /nft-indexer/v1/tokens endpoint.
 */
@Parcelize
data class MimirNftItemDto(
    @SerializedName("owner")
    val owner: String?,
    @SerializedName("tokenId")
    val tokenId: String?, // Note: This is the NFT ID within the contract, not the ASA ID
    @SerializedName("approved")
    val approved: String?,
    @SerializedName("isBurned")
    val isBurned: Boolean?,
    @SerializedName("metadata")
    val metadataJsonString: String?, // Keep as string for now, parse later
    @SerializedName("verified")
    val verified: Int?, // Assuming 0 means not verified, need confirmation
    @SerializedName("contractId")
    val contractId: Long?, // This is the ASA ID
    @SerializedName("mint-round")
    val mintRound: Long?,
    @SerializedName("blacklisted")
    val blacklisted: Boolean?,
    @SerializedName("lastUpdated")
    val lastUpdated: String?, // Consider mapping to a Date/Time object later if needed
    @SerializedName("metadataURI")
    val metadataURI: String?,
    @SerializedName("collectionName")
    val collectionName: String?
) : Parcelable

/**
 * Represents the parsed content of the metadata JSON string within MimirNftItemDto.
 */
@Parcelize
data class MimirNftMetadataDto(
    @SerializedName("name")
    val name: String?,
    @SerializedName("description")
    val description: String?,
    @SerializedName("image")
    val image: String?,
    @SerializedName("image_integrity")
    val imageIntegrity: String?,
    @SerializedName("image_mimetype")
    val imageMimeType: String?,
    // Using Map<String, String?> to handle potential null values in properties more robustly
    @SerializedName("properties")
    val properties: Map<String, String?>?,
    @SerializedName("royalties")
    val royalties: String?
) : Parcelable

package com.algorand.wallet.mapper.arc200

import com.algorand.wallet.network.mimir.model.Arc200ApiBalanceInfo
import com.algorand.wallet.network.mimir.model.Arc200ApiTokenDetail
import com.algorand.wallet.account.info.data.database.model.AssetHoldingEntity
import com.algorand.wallet.account.info.data.database.model.AssetStatusEntity
import com.algorand.wallet.asset.data.database.model.AssetDetailEntity
import com.algorand.wallet.asset.data.database.model.VerificationTierEntity
import com.algorand.wallet.asset.domain.model.Asset
import com.algorand.wallet.asset.domain.model.AssetCreator
import com.algorand.wallet.asset.domain.model.AssetDetail
import com.algorand.wallet.asset.domain.model.AssetType
import com.algorand.wallet.asset.domain.model.VerificationTier
import com.algorand.wallet.foundation.database.model.DbAssetType
import javax.inject.Inject
import java.math.BigInteger
import com.algorand.wallet.account.info.domain.model.AssetHolding
import com.algorand.wallet.account.info.domain.model.AssetStatus

class Arc200DtoToEntityMapper @Inject constructor() {

    /**
     * Maps ARC-200 API balance info to AssetHoldingEntity.
     */
    fun mapToAssetHoldingEntity(
        balanceInfo: Arc200ApiBalanceInfo
    ): AssetHoldingEntity? {
        val accountId = balanceInfo.accountId ?: return null
        val contractId = balanceInfo.contractId ?: return null
        val balance = balanceInfo.balance?.toBigIntegerOrNull() ?: return null
        val isDeleted = balanceInfo.verified == null && balance == BigInteger.ZERO

        return AssetHoldingEntity(
            algoAddress = accountId,
            assetId = contractId,
            amount = balance,
            isDeleted = isDeleted,
            isFrozen = false,
            optedInAtRound = null,
            optedOutAtRound = null,
            assetStatusEntity = AssetStatusEntity.OWNED_BY_ACCOUNT,
            assetType = DbAssetType.ARC200
        )
    }

    /**
     * Maps ARC-200 API balance info to AssetDetailEntity.
     */
    fun mapToAssetDetailEntity(balanceInfo: Arc200ApiBalanceInfo): AssetDetailEntity? {
        val contractId = balanceInfo.contractId ?: return null
        val decimals = balanceInfo.decimals ?: 0

        val verificationTier = when (balanceInfo.verified) {
            1 -> VerificationTierEntity.VERIFIED
            else -> VerificationTierEntity.UNVERIFIED
        }

        return AssetDetailEntity(
            assetId = contractId,
            name = balanceInfo.name,
            unitName = balanceInfo.symbol,
            decimals = decimals,
            usdValue = balanceInfo.usdValue,
            maxSupply = "0",
            explorerUrl = null,
            projectUrl = null,
            projectName = null,
            logoSvgUrl = null,
            logoUrl = balanceInfo.imageUrl,
            discordUrl = null,
            telegramUrl = null,
            twitterUsername = null,
            description = null,
            url = null,
            totalSupply = null,
            last24HoursAlgoPriceChangePercentage = null,
            availableOnDiscoverMobile = false,
            assetCreatorId = null,
            assetCreatorAddress = null,
            isVerifiedAssetCreator = null,
            verificationTier = verificationTier,
            assetType = DbAssetType.ARC200
        )
    }

    /**
     * Maps ARC-200 API token detail to the domain model AssetDetail.
     */
    fun mapTokenDetailToAssetDetail(tokenDetail: Arc200ApiTokenDetail): AssetDetail? {
        val contractId = tokenDetail.contractId ?: return null
        val decimals = tokenDetail.decimals ?: 0
        val name = tokenDetail.name
        val unitName = tokenDetail.symbol

        val verificationTier = when (tokenDetail.verified) {
            1 -> VerificationTier.VERIFIED
            else -> VerificationTier.UNVERIFIED
        }

        val assetInfo = Asset.AssetInfo(
            name = Asset.Name(fullName = name ?: "", shortName = unitName ?: ""),
            decimals = decimals,
            fiat = null,
            creator = tokenDetail.creator?.let { AssetCreator(id = null, publicKey = it, isVerifiedAssetCreator = null) },
            logo = tokenDetail.imageUrl?.let { Asset.Logo(uri = it, svgUri = null) },
            explorerUrl = null,
            project = null,
            social = null,
            description = null,
            supply = Asset.Supply(
                total = tokenDetail.totalSupply?.toBigDecimalOrNull(),
                max = null
            ),
            url = null,
            isAvailableOnDiscoverMobile = false
        )

        return AssetDetail(
            id = contractId,
            assetInfo = assetInfo,
            verificationTier = verificationTier,
            assetType = AssetType.ARC200
        )
    }

    /**
     * Maps ARC-200 API balance info to the domain model AssetHolding.
     * This is for direct use when an entity isn't needed.
     */
    fun mapBalanceInfoToAssetHoldingDomain(balanceInfo: Arc200ApiBalanceInfo): AssetHolding? {
        val contractId = balanceInfo.contractId ?: return null
        val balance = balanceInfo.balance?.toBigIntegerOrNull() ?: return null

        return AssetHolding(
            assetId = contractId,
            amount = balance,
            isDeleted = false,
            isFrozen = false, // ARC-200 tokens are generally not "frozen" in the ASA sense via API balance info
            optedInAtRound = null, // Not applicable/available for ARC-200 from this DTO
            optedOutAtRound = null, // Not applicable/available for ARC-200 from this DTO
            status = AssetStatus.OWNED_BY_ACCOUNT,
            assetType = AssetType.ARC200
        )
    }

    /**
     * Maps a domain AssetDetail (known to be ARC200) to AssetDetailEntity for caching.
     */
    fun mapDomainArc200AssetDetailToEntity(assetDetail: AssetDetail): AssetDetailEntity? {
        if (assetDetail.assetType != AssetType.ARC200) return null // Should not happen if called correctly

        val assetInfo = assetDetail.assetInfo ?: return null // Basic info must exist

        val dbVerificationTier = when (assetDetail.verificationTier) {
            VerificationTier.VERIFIED -> VerificationTierEntity.VERIFIED
            VerificationTier.TRUSTED -> VerificationTierEntity.TRUSTED // Or map to VERIFIED/UNVERIFIED as per DB enum
            VerificationTier.SUSPICIOUS -> VerificationTierEntity.SUSPICIOUS
            VerificationTier.UNVERIFIED -> VerificationTierEntity.UNVERIFIED
            VerificationTier.UNKNOWN -> VerificationTierEntity.UNKNOWN
        }

        return AssetDetailEntity(
            assetId = assetDetail.id,
            name = assetInfo.name.fullName,
            unitName = assetInfo.name.shortName,
            decimals = assetInfo.decimals,
            usdValue = assetInfo.fiat?.usdValue, // Will be null based on current DTO->Domain mapping
            maxSupply = assetInfo.supply?.max?.toPlainString() ?: "0", // Often "0" or not applicable for ARC200 like ASA max
            explorerUrl = assetInfo.explorerUrl,
            projectUrl = assetInfo.project?.url,
            projectName = assetInfo.project?.name,
            logoSvgUrl = assetInfo.logo?.svgUri,
            logoUrl = assetInfo.logo?.uri,
            discordUrl = assetInfo.social?.discordUrl,
            telegramUrl = assetInfo.social?.telegramUrl,
            twitterUsername = assetInfo.social?.twitterUsername,
            description = assetInfo.description,
            url = assetInfo.url,
            totalSupply = assetInfo.supply?.total?.toPlainString(),
            last24HoursAlgoPriceChangePercentage = assetInfo.fiat?.last24HoursAlgoPriceChangePercentage,
            availableOnDiscoverMobile = assetInfo.isAvailableOnDiscoverMobile ?: false,
            assetCreatorId = null, // assetInfo.creator.id is not typically stored in AssetDetailEntity
            assetCreatorAddress = assetInfo.creator?.publicKey,
            isVerifiedAssetCreator = assetInfo.creator?.isVerifiedAssetCreator,
            verificationTier = dbVerificationTier,
            assetType = DbAssetType.ARC200
        )
    }
}

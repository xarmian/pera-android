package com.algorand.android.mapper

import com.algorand.android.network.dto.Arc200ApiBalanceInfo
import com.algorand.android.network.dto.Arc200ApiTokenDetail
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
}

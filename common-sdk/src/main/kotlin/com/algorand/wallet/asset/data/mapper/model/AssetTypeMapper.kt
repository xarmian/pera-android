package com.algorand.wallet.asset.data.mapper.model

import com.algorand.wallet.asset.domain.model.AssetType
import com.algorand.wallet.foundation.database.model.DbAssetType
import javax.inject.Inject

/**
 * Maps between database [DbAssetType] and domain [AssetType].
 */
internal class AssetTypeMapper @Inject constructor() {
    operator fun invoke(dbAssetType: DbAssetType): AssetType {
        return when (dbAssetType) {
            DbAssetType.ASA -> AssetType.ASA
            DbAssetType.ARC200 -> AssetType.ARC200
        }
    }

    // Optional: Add reverse mapping if needed later
    // operator fun invoke(assetType: AssetType): DbAssetType { ... }
} 
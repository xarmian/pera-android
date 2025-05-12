package com.algorand.android.domain.bridge.usecase

import com.algorand.android.modules.bridge.data.service.ExternalAlgorandIndexerApi
import com.algorand.android.modules.bridge.domain.model.BridgeConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.math.BigInteger
import javax.inject.Inject

class GetAVoiBalanceUseCase @Inject constructor(
    private val externalAlgorandIndexerApi: ExternalAlgorandIndexerApi
) {

    suspend operator fun invoke(accountAddress: String): Flow<BigInteger> {
        return try {
            val response = externalAlgorandIndexerApi.getAccountDetail(accountAddress)
            if (response.isSuccessful) {
                val accountDetail = response.body()?.account
                val aVoiHolding = accountDetail?.assets?.find { it.assetId == BridgeConstants.AVOI_ASSET_ID }
                flowOf(aVoiHolding?.amount ?: BigInteger.ZERO)
            } else {
                // Handle error case, e.g., log error, return zero or emit an error state if preferred
                // For now, returning ZERO on error or if not found
                println("Error fetching aVOI balance for $accountAddress: ${response.errorBody()?.string()}")
                flowOf(BigInteger.ZERO)
            }
        } catch (e: Exception) {
            // Handle exception, e.g., log error, return zero or emit an error state
            println("Exception fetching aVOI balance for $accountAddress: ${e.message}")
            flowOf(BigInteger.ZERO)
        }
    }
}

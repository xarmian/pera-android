package com.algorand.android.domain.bridge.usecase

import com.algorand.android.modules.bridge.data.service.ExternalAlgorandIndexerApi
import com.algorand.android.modules.bridge.domain.model.BridgeConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class IsAccountOptedInToAVoiUseCase @Inject constructor(
    private val externalAlgorandIndexerApi: ExternalAlgorandIndexerApi
) {

    suspend operator fun invoke(accountAddress: String): Flow<Boolean> {
        return try {
            val response = externalAlgorandIndexerApi.getAccountDetail(accountAddress)
            if (response.isSuccessful) {
                val accountDetail = response.body()?.account
                val isOptedIn = accountDetail?.assets?.any { it.assetId == BridgeConstants.AVOI_ASSET_ID } ?: false
                flowOf(isOptedIn)
            } else {
                // Handle error case, e.g., log error, return false
                println("Error checking aVOI opt-in for $accountAddress: ${response.errorBody()?.string()}")
                flowOf(false)
            }
        } catch (e: Exception) {
            // Handle exception, e.g., log error, return false
            println("Exception checking aVOI opt-in for $accountAddress: ${e.message}")
            flowOf(false)
        }
    }
}

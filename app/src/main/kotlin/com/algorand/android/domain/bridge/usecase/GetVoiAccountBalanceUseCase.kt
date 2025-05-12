package com.algorand.android.domain.bridge.usecase

import com.algorand.wallet.account.info.domain.repository.AccountInformationRepository // Changed import
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.math.BigInteger
import javax.inject.Inject

class GetVoiAccountBalanceUseCase @Inject constructor(
    private val accountInformationRepository: AccountInformationRepository // Changed repository type
) {

    // TODO: Ensure accountInformationRepository is configured for Voi network when this is called.
    suspend operator fun invoke(accountAddress: String): Flow<BigInteger> {
        // Placeholder: returning 0 for now, actual call to be implemented
        // Actual implementation will call accountInformationRepository.getAccountAlgoBalance(accountAddress)
        // and handle the network configuration for Voi.
        val balance = accountInformationRepository.getAccountAlgoBalance(accountAddress)
        return flowOf(balance ?: BigInteger.ZERO)
    }
}

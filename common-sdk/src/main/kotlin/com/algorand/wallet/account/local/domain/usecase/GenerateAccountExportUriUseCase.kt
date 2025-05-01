package com.algorand.wallet.account.local.domain.usecase

import android.util.Base64
import android.util.Log
import com.algorand.algosdk.sdk.Sdk
import com.algorand.wallet.account.local.domain.model.AccountMnemonic
import com.algorand.wallet.foundation.PeraResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class GenerateAccountExportUriUseCase @Inject constructor(
    private val getLocalAccount: GetLocalAccount,
    private val getAccountMnemonic: GetAccountMnemonic
) {

    // ExportError now extends Exception
    sealed class ExportError(override val cause: Throwable? = null) : Exception() {
        data class ExportUnavailableForAccountType(val accountType: AccountMnemonic.AccountType) : ExportError()
        data class SdkError(override val cause: Throwable) : ExportError(cause = cause)
        data class EncodingError(override val cause: Throwable) : ExportError(cause = cause)
        data object AccountNotFound : ExportError()
        data object MnemonicNotFound : ExportError()
    }

    suspend operator fun invoke(accountAddress: String): PeraResult<String> = withContext(Dispatchers.IO) {
        getLocalAccount(accountAddress)
            ?: return@withContext PeraResult.Error(ExportError.AccountNotFound)

        val accountMnemonicResult = getAccountMnemonic(accountAddress)
        val accountMnemonic = when (accountMnemonicResult) {
            is PeraResult.Success -> accountMnemonicResult.data
            is PeraResult.Error -> return@withContext PeraResult.Error(
                ExportError.MnemonicNotFound
            )
        }

        if (accountMnemonic.type != AccountMnemonic.AccountType.Algo25) {
            return@withContext PeraResult.Error(ExportError.ExportUnavailableForAccountType(accountMnemonic.type))
        }

        try {
            val mnemonicString = accountMnemonic.words.joinToString(" ")
            val fullPrivateKeyBytes = Sdk.mnemonicToPrivateKey(mnemonicString)

            // Handle error: private key generation failed or result is too short
            if (fullPrivateKeyBytes == null || fullPrivateKeyBytes.size < 32) {
                 return@withContext PeraResult.Error(ExportError.SdkError(IllegalArgumentException("Invalid private key generated")))
            }

            // Extract the first 32 bytes (seed)
            val seedBytes = fullPrivateKeyBytes.copyOfRange(0, 32)

            // Encode only the 32-byte seed
            val base64Key = Base64.encodeToString(seedBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

            // Construct URI
            val uriString = "avm://account/import?privatekey=$base64Key"

            PeraResult.Success(uriString)
        } catch (e: Exception) {
            when (e) {
                is IllegalArgumentException -> PeraResult.Error(ExportError.SdkError(e))
                else -> PeraResult.Error(ExportError.EncodingError(e))
            }
        }
    }
}

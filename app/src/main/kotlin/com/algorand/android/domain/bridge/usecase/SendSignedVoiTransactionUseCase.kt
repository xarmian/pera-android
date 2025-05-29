package com.algorand.android.domain.bridge.usecase

import com.algorand.android.models.Result
import com.algorand.android.network.AlgodApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

class SendSignedVoiTransactionUseCase @Inject constructor(
    private val algodApi: AlgodApi // App's AlgodApi, should be Voi-configured
) {
    private val rawTransactionMediaType = "application/x-binary".toMediaType()

    suspend operator fun invoke(signedTxnByteArray: ByteArray): Flow<Result<String>> = flow {
        try {
            val requestBody = signedTxnByteArray.toRequestBody(rawTransactionMediaType)
            val response = algodApi.sendSignedTransaction(requestBody)
            if (response.isSuccessful && response.body()?.txnId != null) {
                emit(Result.Success(response.body()!!.txnId!!))
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error sending transaction."
                println("Failed to send Voi transaction: $errorBody (Code: ${response.code()})")
                emit(Result.Error(Exception("Failed to send Voi transaction: $errorBody")))
            }
        } catch (e: Exception) {
            println("Exception sending Voi transaction: ${e.message}")
            emit(Result.Error(e))
        }
    }
}

package com.algorand.android.domain.bridge.usecase

import com.algorand.android.modules.bridge.domain.model.BridgeConstants
import com.algorand.android.network.AlgodApi
import com.algorand.android.utils.makeAlgoTx
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.math.BigInteger
import javax.inject.Inject

const val HASH_NUMBER = 31

data class UnsignedVoiToAramidTransaction(
    val unsignedTxnByteArray: ByteArray,
    val senderAddress: String,
    val noteString: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UnsignedVoiToAramidTransaction
        if (!unsignedTxnByteArray.contentEquals(other.unsignedTxnByteArray)) return false
        if (senderAddress != other.senderAddress) return false
        return true
    }

    override fun hashCode(): Int {
        var result = unsignedTxnByteArray.contentHashCode()
        result = HASH_NUMBER * result + senderAddress.hashCode()
        return result
    }
}

class PrepareVoiToAramidTransactionUseCase @Inject constructor(
    private val gson: Gson,
    private val algodApi: AlgodApi
) {

    private data class AramidVoiToAlgoNote(
        @SerializedName("destinationNetwork") val destinationNetwork: Int = BridgeConstants.ALGORAND_NETWORK_ID_FOR_NOTE,
        @SerializedName("destinationAddress") val destinationAddress: String,
        @SerializedName("destinationToken") val destinationToken: String = BridgeConstants.AVOI_ASSET_ID_FOR_NOTE,
        @SerializedName("feeAmount") val feeAmount: BigInteger,
        @SerializedName("destinationAmount") val destinationAmount: BigInteger,
        @SerializedName("note") val noteComment: String = "verawallet",
        @SerializedName("sourceAmount") val sourceAmount: BigInteger
    )

    suspend operator fun invoke(
        fromVoiAccountAddress: String,
        destinationAlgorandAddress: String,
        grossVoiAmount: BigInteger,
        feeAmount: BigInteger,
        netAmountToReceive: BigInteger
    ): UnsignedVoiToAramidTransaction? {
        return try {
            val aramidNotePayload = AramidVoiToAlgoNote(
                destinationAddress = destinationAlgorandAddress,
                feeAmount = feeAmount,
                destinationAmount = netAmountToReceive,
                sourceAmount = netAmountToReceive
            )
            val noteJson = gson.toJson(aramidNotePayload)
            val notePrefix = "aramid-transfer/v1:j"
            val fullNote = "$notePrefix$noteJson"
            val noteByteArray = fullNote.encodeToByteArray()

            val transactionParamsResponse = algodApi.getTransactionParams()
            if (!transactionParamsResponse.isSuccessful || transactionParamsResponse.body() == null) {
                println("Failed to fetch Voi transaction params: ${transactionParamsResponse.errorBody()?.string()}")
                return null
            }
            val voiTransactionParams = transactionParamsResponse.body()!!

            val unsignedTxnByteArray = voiTransactionParams.makeAlgoTx(
                senderAddress = fromVoiAccountAddress,
                receiverAddress = BridgeConstants.ARAMID_ADDRESS,
                amount = grossVoiAmount,
                isMax = false,
                noteInByteArray = noteByteArray
            )

            UnsignedVoiToAramidTransaction(
                unsignedTxnByteArray = unsignedTxnByteArray,
                senderAddress = fromVoiAccountAddress,
                noteString = fullNote
            )
        } catch (e: Exception) {
            println("Error preparing Voi to Aramid transaction: ${e.message}")
            null
        }
    }
}

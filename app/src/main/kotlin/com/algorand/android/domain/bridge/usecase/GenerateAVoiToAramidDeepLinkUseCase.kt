package com.algorand.android.domain.bridge.usecase

import com.algorand.android.modules.bridge.domain.model.BridgeConstants
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.math.BigInteger
import javax.inject.Inject

class GenerateAVoiToAramidDeepLinkUseCase @Inject constructor(
    private val gson: Gson // Assuming Gson is globally available via Hilt
) {

    // Data class for the structured note for Aramid transfers (Algorand -> Voi)
    private data class AramidAlgoToVoiNote(
        @SerializedName("destinationNetwork") val destinationNetwork: Int = BridgeConstants.VOI_NETWORK_ID_FOR_NOTE,
        @SerializedName("destinationAddress") val destinationAddress: String,
        @SerializedName("destinationToken") val destinationToken: String = BridgeConstants.NATIVE_VOI_ASSET_ID_FOR_NOTE,
        @SerializedName("feeAmount") val feeAmount: BigInteger,
        @SerializedName("destinationAmount") val destinationAmount: BigInteger, // Net amount
        @SerializedName("note") val noteComment: String = "verawallet",
        @SerializedName("sourceAmount") val sourceAmount: BigInteger // Net amount, same as destinationAmount
    )

    operator fun invoke(
        grossAVoiAmount: BigInteger,
        feeAmount: BigInteger,
        netAmountToReceive: BigInteger,
        destinationVoiAddress: String
    ): String {
        val aramidNotePayload = AramidAlgoToVoiNote(
            destinationAddress = destinationVoiAddress,
            feeAmount = feeAmount,
            destinationAmount = netAmountToReceive,
            sourceAmount = netAmountToReceive // As per plan, sourceAmount is also the net amount for this note
        )
        val noteJson = gson.toJson(aramidNotePayload)
        val notePrefix = "aramid-transfer/v1:j"
        val fullNote = "$notePrefix$noteJson"

        // val encodedNote = URLEncoder.encode(fullNote, "UTF-8")

        // perawallet://<ARAMID_ADDRESS>?amount=<ATOMIC_aVOI_AMOUNT>&asset=<aVOI_ASSET_ID>&xnote=<URL_ENCODED_ARAMID_NOTE>
        return "perawallet://${BridgeConstants.ARAMID_ADDRESS}"
            .plus("?amount=$grossAVoiAmount")
            .plus("&asset=${BridgeConstants.AVOI_ASSET_ID}")
            .plus("&xnote=$fullNote")
    }
}

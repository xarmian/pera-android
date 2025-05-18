package com.algorand.android.models

/**
 * UI Model for ARC-200 transfer review, supporting both single and grouped (MBR + ARC-200) flows.
 */
sealed class Arc200TransferReviewUiModel {
    data class Single(
        val preview: AssetTransferPreview,
        val fee: Long,
        val totalFee: Long
    ) : Arc200TransferReviewUiModel()

    data class WithMbr(
        val mbrPreview: MbrPaymentPreview,
        val arc200Preview: AssetTransferPreview,
        val mbrFee: Long,
        val arc200Fee: Long,
        val totalFee: Long,
        val mbrExplanation: String
    ) : Arc200TransferReviewUiModel()
}

data class MbrPaymentPreview(
    val senderAccountAddress: String,
    val contractAddress: String,
    val mbrAmount: Long,
    val fee: Long
)

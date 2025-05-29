/*
 * Copyright 2025 Pera Wallet, LDA
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License
 *
 */

package com.algorand.android.mapper

import com.algorand.android.R
import com.algorand.android.models.AnnotatedString
import com.algorand.android.models.AssetTransferPreview
import com.algorand.android.models.TransactionSignData
import com.algorand.android.modules.accounticon.ui.model.AccountIconDrawablePreview
import com.algorand.wallet.account.detail.domain.model.AccountDetail
import com.algorand.wallet.asset.domain.model.AssetType
import com.algorand.wallet.asset.domain.util.AssetConstants.ALGO_ID
import com.algorand.wallet.contract.arc200.Arc200Abi
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

class AssetTransferPreviewMapper @Inject constructor() {

    @Suppress("LongParameterList")
    fun mapToAssetTransferPreview(
        transactionData: TransactionSignData.Send,
        exchangePrice: BigDecimal,
        currencySymbol: String,
        assetId: Long,
        assetShortName: String,
        assetDecimals: Int,
        note: String?,
        isNoteEditable: Boolean,
        accountIconDrawablePreview: AccountIconDrawablePreview,
        fee: Long,
        targetAccountDetail: AccountDetail,
        mbrPaymentAmount: BigInteger? = null,
        simulationResponse: String? = null
    ): AssetTransferPreview {
        with(transactionData) {
            val transactionTypeLabelString: AnnotatedString
            var finalArc200ContractId: Long? = null
            var finalArc200MethodName: String? = null

            when (this.assetType) {
                AssetType.ARC200 -> {
                    transactionTypeLabelString = AnnotatedString(R.string.arc200_token_transfer)
                    finalArc200ContractId = this.assetId
                    finalArc200MethodName = Arc200Abi.arc200TransferMethod.name
                }
                else -> {
                    if (this.assetId == ALGO_ID) {
                        transactionTypeLabelString = AnnotatedString(R.string.payment)
                    } else {
                        transactionTypeLabelString = AnnotatedString(R.string.asset_transfer)
                    }
                }
            }

            return AssetTransferPreview(
                amount = amount,
                targetUser = targetUser,
                exchangePrice = exchangePrice,
                currencySymbol = currencySymbol,
                fee = fee,
                note = note,
                isNoteEditable = isNoteEditable,
                accountIconDrawablePreview = accountIconDrawablePreview,
                senderAccountName = senderAccountName,
                senderAccountAddress = senderAccountAddress,
                targetAccountDetail = targetAccountDetail,
                senderAssetAmount = this.senderSpecificAssetAmount ?: this.senderAlgoAmount,
                assetId = this.assetId,
                assetShortName = assetShortName,
                assetDecimals = assetDecimals,
                transactionTypeLabel = transactionTypeLabelString,
                arc200ContractId = finalArc200ContractId,
                arc200MethodName = finalArc200MethodName,
                mbrPaymentAmount = mbrPaymentAmount,
                simulationResponse = simulationResponse
            )
        }
    }
}

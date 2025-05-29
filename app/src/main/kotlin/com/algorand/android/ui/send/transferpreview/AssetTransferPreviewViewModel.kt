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

package com.algorand.android.ui.send.transferpreview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algorand.android.R
import com.algorand.android.core.transaction.TransactionSignManager
import com.algorand.android.models.AnnotatedString
import com.algorand.android.models.AssetTransferPreview
import com.algorand.android.models.SignedTransactionDetail
import com.algorand.android.models.TransactionSignData
import com.algorand.android.usecase.AssetTransferPreviewUseCase
import com.algorand.android.utils.DataResource
import com.algorand.android.utils.Event
import com.algorand.android.utils.Resource
import com.algorand.android.utils.Resource.Error.GlobalWarning
import com.algorand.android.utils.flatten
import com.algorand.android.utils.getOrThrow
import com.algorand.wallet.account.core.domain.usecase.GetTransactionSigner
import com.algorand.wallet.account.info.domain.usecase.GetAccountInformation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.algorand.android.models.Arc200TransferReviewUiModel
import java.math.BigInteger

@HiltViewModel
class AssetTransferPreviewViewModel @Inject constructor(
    private val assetTransferPreviewUserCase: AssetTransferPreviewUseCase,
    private val transactionSignManager: TransactionSignManager,
    private val getTransactionSigner: GetTransactionSigner,
    private val getAccountInformation: GetAccountInformation,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var sendAlgoJob: Job? = null
    private val transactionData = savedStateHandle.getOrThrow<TransactionSignData.Send>(TRANSACTION_DATA_KEY)

    private val _sendAlgoResponseFlow = MutableStateFlow<Event<Resource<String>>?>(null)
    val sendAlgoResponseFlow: StateFlow<Event<Resource<String>>?> = _sendAlgoResponseFlow

    private val _arc200TransferReviewUiModelFlow = MutableStateFlow<Arc200TransferReviewUiModel?>(null)
    val arc200TransferReviewUiModelFlow: StateFlow<Arc200TransferReviewUiModel?> = _arc200TransferReviewUiModelFlow

    // Retain old preview flow for non-ARC-200 flows
    private val _assetTransferPreviewFlow = MutableStateFlow<AssetTransferPreview?>(null)
    val assetTransferPreviewFlow: StateFlow<AssetTransferPreview?> = _assetTransferPreviewFlow

    private val _signArc59TransactionFlow = MutableStateFlow<TransactionSignData?>(null)
    val signArc59TransactionFlow: StateFlow<TransactionSignData?> = _signArc59TransactionFlow

    private val _signArc200TransactionFlow = MutableStateFlow<TransactionSignData?>(null)
    val signArc200TransactionFlow: StateFlow<TransactionSignData?> = _signArc200TransactionFlow

    private val _signArc200TransactionGroupFlow = MutableStateFlow<List<TransactionSignData>?>(null)
    val signArc200TransactionGroupFlow: StateFlow<List<TransactionSignData>?> = _signArc200TransactionGroupFlow

    private var unsignedArc59Transactions = listOf<TransactionSignData>()
    private var unsignedArc200Transactions = listOf<TransactionSignData>()

    private val signedArc59Transactions = mutableListOf<SignedTransactionDetail>()
    private val signedArc200Transactions = mutableListOf<SignedTransactionDetail>()

    init {
        // For ARC200, build preview only (do not emit to signing flows)
        if (transactionData.isArc200Transaction) {
            makeArc200Preview(transactionData)
        } else if (transactionData.isArc59Transaction) {
            makeArc59Transactions(transactionData)
        } else {
            getAssetTransferPreview(listOf(transactionData))
        }
    }

    private fun getAssetTransferPreview(
        transactionList: List<TransactionSignData>,
        receiverMinBalanceFee: Long? = null
    ) {
        viewModelScope.launch {
            val firstSend = transactionList.firstOrNull() as? TransactionSignData.Send
            var updatedTransactionList = transactionList // Default to original list
            var mbrAmountForPreview: BigInteger? = null

            if (firstSend != null && firstSend.assetType?.name == "ARC200") {
                mbrAmountForPreview = firstSend.mbrAmount ?: BigInteger.ZERO
                val preview = assetTransferPreviewUserCase.getAssetTransferPreview(
                    updatedTransactionList,
                    receiverMinBalanceFee,
                    mbrPaymentAmount = mbrAmountForPreview
                )
                _arc200TransferReviewUiModelFlow.emit(
                    Arc200TransferReviewUiModel.Single(
                        preview = preview,
                        fee = preview.fee,
                        totalFee = preview.fee
                    )
                )
            } else {
                val signedTransactionPreview = assetTransferPreviewUserCase.getAssetTransferPreview(
                    transactionList,
                    receiverMinBalanceFee
                )
                _assetTransferPreviewFlow.emit(signedTransactionPreview)
            }
        }
    }

    fun sendSignedTransaction(signedTransactionDetail: SignedTransactionDetail) {
        var signedTransactionDetailCopy: SignedTransactionDetail = signedTransactionDetail
        if (transactionData.isArc59Transaction) {
            signedArc59Transactions.add(signedTransactionDetail)
            if (signedArc59Transactions.size < unsignedArc59Transactions.size) {
                viewModelScope.launch {
                    _signArc59TransactionFlow.emit(unsignedArc59Transactions[signedArc59Transactions.size])
                }
                return
            }
            signedTransactionDetailCopy =
                (signedArc59Transactions.last() as SignedTransactionDetail.Send).copy(
                    signedTransactionData = signedArc59Transactions.map { it.signedTransactionData }
                        .flatten()
                )
        }
        if (sendAlgoJob?.isActive == true) {
            return
        }
        sendAlgoJob = viewModelScope.launch {
            assetTransferPreviewUserCase.sendSignedTransaction(signedTransactionDetailCopy)
                .collectLatest {
                    when (it) {
                        is DataResource.Loading -> _sendAlgoResponseFlow.emit(Event(Resource.Loading))
                        is DataResource.Error -> {
                            if (it.exception != null) {
                                _sendAlgoResponseFlow.emit(Event(Resource.Error.Api(it.exception!!)))
                            } else {
                                _sendAlgoResponseFlow.emit(
                                    Event(GlobalWarning(R.string.error, AnnotatedString(R.string.an_error_occured)))
                                )
                            }
                        }

                        is DataResource.Success -> _sendAlgoResponseFlow.emit(Event(Resource.Success(it.data)))
                    }
                }
        }
    }

    fun onNoteUpdate(newNote: String) {
        viewModelScope.launch {
            if (_assetTransferPreviewFlow.value?.isNoteEditable == true) {
                val newPreview = _assetTransferPreviewFlow.value?.copy(note = newNote)
                _assetTransferPreviewFlow.emit(newPreview)
            }
        }
    }

    private fun makeArc59Transactions(transactionData: TransactionSignData) {
        viewModelScope.launch {
            (transactionData as TransactionSignData.Send).let {
                val transactions = transactionSignManager.createArc59SendTransactionList(transactionData)
                val arc59Transactions = mutableListOf<TransactionSignData>()
                transactions?.forEach { transaction ->
                    if (transaction.accountAddress == transactionData.senderAccountAddress) {
                        arc59Transactions.add(
                            transactionData.copy(transactionByteArray = transaction.transactionByteArray)
                        )
                    } else {
                        val targetAccountInfo = getAccountInformation(transactionData.targetUser.publicKey)
                        arc59Transactions.add(
                            TransactionSignData.AddAsset(
                                senderAccountAddress = transactionData.targetUser.publicKey,
                                senderAuthAddress = targetAccountInfo?.rekeyAdminAddress,
                                assetId = transactionData.assetId,
                                transactionByteArray = transaction.transactionByteArray,
                                isArc59Transaction = transactionData.isArc59Transaction,
                                signer = getTransactionSigner(transactionData.targetUser.publicKey)
                            )
                        )
                    }
                }
                signedArc59Transactions.clear()
                unsignedArc59Transactions = arc59Transactions
                val receiverMinBalanceFee = transactionSignManager.getReceiverMinBalanceFee(transactionData)
                getAssetTransferPreview(unsignedArc59Transactions, receiverMinBalanceFee)
            }
        }
    }

    private fun makeArc200Preview(transactionData: TransactionSignData) {
        viewModelScope.launch {
            unsignedArc200Transactions = emptyList()
            signedArc200Transactions.clear()
            // Always use a fresh copy for preview
            val previewData = (transactionData as? TransactionSignData.Send)?.copy(transactionByteArray = null)
            previewData?.let { sendData ->
                val transactions = transactionSignManager.createArc200SendTransactionList(
                    sendData,
                    sendData.simulationResponse
                ) ?: return@launch

                unsignedArc200Transactions = transactions.map { transaction ->
                    sendData.copy(transactionByteArray = transaction.transactionByteArray)
                }

                val receiverMinBalanceFee = transactionSignManager.getReceiverMinBalanceFee(sendData)
                getAssetTransferPreview(unsignedArc200Transactions)
            }
        }
    }

    // Remove preview logic from makeArc200Transactions, use only for signing
    private fun makeArc200TransactionsForSigning(transactionData: TransactionSignData) {
        viewModelScope.launch {
            (transactionData as? TransactionSignData.Send)?.let { sendData ->
                val transactions = transactionSignManager.createArc200SendTransactionList(
                    sendData,
                    sendData.simulationResponse
                ) ?: return@launch

                unsignedArc200Transactions = transactions.map { transaction ->
                    sendData.copy(transactionByteArray = transaction.transactionByteArray)
                }

                signedArc200Transactions.clear()
                // Do NOT call getAssetTransferPreview here
            }
        }
    }

    fun sendArc59Transactions() {
        viewModelScope.launch {
            _signArc59TransactionFlow.emit(unsignedArc59Transactions.first())
        }
    }

    fun sendArc200Transactions() {
        viewModelScope.launch {
            unsignedArc200Transactions = emptyList()
            signedArc200Transactions.clear()
            // Get the current transaction data with updated notes
            val currentTransactionData = getTransactionData().copy(transactionByteArray = null)

            // Instead of generating sub-transactions, emit the parent transaction for signing
            _signArc200TransactionGroupFlow.emit(listOf(currentTransactionData))
        }
    }

    fun getTransactionData(): TransactionSignData.Send {
        val simResponse = transactionData.simulationResponse
        return if (_assetTransferPreviewFlow.value?.isNoteEditable == true) {
            transactionData.copy(
                note = _assetTransferPreviewFlow.value?.note,
                xnote = null,
                simulationResponse = simResponse
            )
        } else {
            transactionData.copy(
                note = null,
                xnote = _assetTransferPreviewFlow.value?.note,
                simulationResponse = simResponse
            )
        }
    }

    companion object {
        private const val TRANSACTION_DATA_KEY = "transactionData"
    }
}

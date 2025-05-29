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

package com.algorand.android.ui.send.receiveraccount

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algorand.android.models.AssetTransaction
import com.algorand.android.models.BaseAccountSelectionListItem
import com.algorand.android.models.Result
import com.algorand.android.models.Arc200TransferSimulationResult
import com.algorand.android.models.TargetUserWithSimulation
import com.algorand.android.models.TransactionSignData
import com.algorand.android.modules.accountasset.domain.model.AccountAssetDetail
import com.algorand.android.modules.assetinbox.expresssend.domain.usecase.Arc59ExpressSendUseCase
import com.algorand.android.usecase.ReceiverAccountSelectionUseCase
import com.algorand.android.utils.Event
import com.algorand.android.utils.Resource
import com.algorand.wallet.account.core.domain.usecase.GetAccountMinBalance
import com.algorand.wallet.account.core.domain.usecase.GetTransactionSigner
import com.algorand.wallet.account.custom.domain.usecase.GetAccountCustomName
import com.algorand.wallet.account.info.domain.usecase.GetAccountInformation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import com.algorand.wallet.asset.domain.usecase.GetAsset
import com.algorand.wallet.asset.domain.usecase.FetchAsset
import com.algorand.wallet.asset.domain.usecase.FetchAndCacheAssets
import com.algorand.android.modules.accountcore.domain.usecase.GetAccountBaseOwnedAssetData
import com.algorand.wallet.asset.domain.model.AssetType

@HiltViewModel
class ReceiverAccountSelectionViewModel @Inject constructor(
    private val receiverAccountSelectionUseCase: ReceiverAccountSelectionUseCase,
    private val arc59ExpressSendUseCase: Arc59ExpressSendUseCase,
    private val getTransactionSigner: GetTransactionSigner,
    private val getAccountInformation: GetAccountInformation,
    private val getAccountMinBalance: GetAccountMinBalance,
    private val getAccountCustomName: GetAccountCustomName,
    private val getAsset: GetAsset,
    private val fetchAsset: FetchAsset,
    private val fetchAndCacheAssets: FetchAndCacheAssets,
    private val getAccountBaseOwnedAssetData: GetAccountBaseOwnedAssetData,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val assetTransaction = savedStateHandle.get<AssetTransaction>(ASSET_TRANSACTION_KEY)!!

    private val _selectableAccountFlow = MutableStateFlow<List<BaseAccountSelectionListItem>?>(null)
    val selectableAccountFlow: StateFlow<List<BaseAccountSelectionListItem>?> = _selectableAccountFlow

    private val _toAccountAddressValidationFlow = MutableStateFlow<Event<Resource<String>>?>(null)
    val toAccountAddressValidationFlow: StateFlow<Event<Resource<String>>?> = _toAccountAddressValidationFlow

    private val _toAccountInformationFlow = MutableStateFlow<Event<Resource<AccountAssetDetail>>?>(null)
    val toAccountInformationFlow: StateFlow<Event<Resource<AccountAssetDetail>>?> = _toAccountInformationFlow

    private val _toAccountTransactionRequirementsFlow = MutableStateFlow<Event<Resource<TargetUserWithSimulation>>?>(null)
    val toAccountTransactionRequirementsFlow: StateFlow<Event<Resource<TargetUserWithSimulation>>?> = _toAccountTransactionRequirementsFlow

    private val _currentArc200SimulationResult = MutableStateFlow<Arc200TransferSimulationResult?>(null)

    private val _sendTransactionDataFlow: MutableStateFlow<Event<TransactionSignData.Send>?> = MutableStateFlow(null)
    val sendTransactionDataFlow: StateFlow<Event<TransactionSignData.Send>?>
        get() = _sendTransactionDataFlow.asStateFlow()

    private var nftDomainAddressServiceLogoPair: Pair<String, String?>? = null

    private val queryFlow = MutableStateFlow("")

    private val latestCopiedMessageFlow = MutableStateFlow<String?>(null)

    init {
        combineLatestCopiedMessageAndQueryFlow()
    }

    fun onSearchQueryUpdate(query: String) {
        viewModelScope.launch {
            queryFlow.emit(query)
        }
    }

    fun checkIsGivenAddressValid(toAccountPublicKey: String) {
        viewModelScope.launch {
            _toAccountAddressValidationFlow.emit(Event(Resource.Loading))
            when (val result = receiverAccountSelectionUseCase.isAccountAddressValid(toAccountPublicKey)) {
                is Result.Error -> _toAccountAddressValidationFlow.emit(Event(result.getAsResourceError()))
                is Result.Success -> _toAccountAddressValidationFlow.emit(Event(Resource.Success(result.data)))
            }
        }
    }

    fun fetchToAccountInformation(
        toAccountPublicKey: String,
        nftDomainAddress: String? = null,
        nftDomainServiceLogoUrl: String? = null
    ) {
        nftDomainAddressServiceLogoPair = nftDomainAddress?.run { Pair(this, nftDomainServiceLogoUrl) }
        viewModelScope.launch {
            _toAccountInformationFlow.emit(Event(Resource.Loading))
            val result = receiverAccountSelectionUseCase.fetchAccountInformationForAsset(
                toAccountPublicKey,
                assetTransaction.assetId
            )
            when (result) {
                is Result.Success -> _toAccountInformationFlow.emit(Event(Resource.Success(result.data)))
                is Result.Error -> _toAccountInformationFlow.emit(Event(result.getAsResourceError()))
            }
        }
    }

    fun checkToAccountTransactionRequirements(accountAssetDetail: AccountAssetDetail) {
        viewModelScope.launch {
            _toAccountTransactionRequirementsFlow.emit(Event(Resource.Loading))
            val result = receiverAccountSelectionUseCase.checkToAccountTransactionRequirements(
                accountAssetDetail = accountAssetDetail,
                assetId = assetTransaction.assetId,
                fromAccountAddress = assetTransaction.senderAddress,
                amount = assetTransaction.amount,
                nftDomainAddress = nftDomainAddressServiceLogoPair?.first,
                nftDomainServiceLogoUrl = nftDomainAddressServiceLogoPair?.second
            )
            when (result) {
                is Result.Success -> {
                    _toAccountTransactionRequirementsFlow.value = Event(Resource.Success(result.data))
                    _currentArc200SimulationResult.value = null // or set to result.data.simulationResponse if needed
                }

                is Result.Error -> {
                    _currentArc200SimulationResult.value = null
                    _toAccountTransactionRequirementsFlow.emit(Event(result.getAsResourceError()))
                }
            }
        }
    }

    private fun combineLatestCopiedMessageAndQueryFlow() {
        viewModelScope.launch {
            combine(
                latestCopiedMessageFlow,
                queryFlow.debounce(QUERY_DEBOUNCE)
            ) { latestCopiedMessage, query ->
                receiverAccountSelectionUseCase.getToAccountList(
                    query = query,
                    latestCopiedMessage = latestCopiedMessage
                ).collectLatest {
                    _selectableAccountFlow.emit(it)
                }
            }.collect()
        }
    }

    fun updateCopiedMessage(copiedMessage: String?) {
        viewModelScope.launch {
            latestCopiedMessageFlow.emit(copiedMessage)
        }
    }

    fun getSendTransactionData(targetUserWithSimulation: TargetUserWithSimulation) {
        val currentAssetTransaction = assetTransaction
        val note = currentAssetTransaction.xnote ?: currentAssetTransaction.note
        val assetId = currentAssetTransaction.assetId

        viewModelScope.launch {
            val isArc59Transaction = isArc59Transaction(targetUserWithSimulation.targetUser.publicKey, assetId)
            val isArc200Transaction = isArc200Transaction((targetUserWithSimulation.targetUser.publicKey), assetId)
            val accountInfo = getAccountInformation(currentAssetTransaction.senderAddress) ?: return@launch
            val minBalance = getAccountMinBalance(currentAssetTransaction.senderAddress)
            val accountName = getAccountCustomName(currentAssetTransaction.senderAddress)
            val resolvedAssetType = getAsset(assetId)?.assetType
            val specificAssetHolding = accountInfo.assetHoldings.firstOrNull { it.assetId == assetId }

            val transactionData = TransactionSignData.Send(
                senderAccountAddress = currentAssetTransaction.senderAddress,
                senderAuthAddress = accountInfo.rekeyAdminAddress,
                signer = getTransactionSigner(accountInfo.rekeyAdminAddress ?: currentAssetTransaction.senderAddress),
                amount = currentAssetTransaction.amount,
                targetUser = targetUserWithSimulation.targetUser,
                transactionByteArray = null,
                isArc59Transaction = isArc59Transaction,
                isArc200Transaction = isArc200Transaction,
                senderAlgoAmount = accountInfo.amount,
                minimumBalance = minBalance.toLong(),
                senderAccountName = accountName.orEmpty(),
                assetId = assetId,
                assetType = resolvedAssetType,
                note = note,
                xnote = null,
                isMax = false,
                projectedFee = com.algorand.android.utils.MIN_FEE,
                senderSpecificAssetAmount = specificAssetHolding?.amount,
                simulationResponse = targetUserWithSimulation.simulationResponse,
                isMbrPaymentActuallyRequired = targetUserWithSimulation.isMbrPaymentSimulated,
                mbrAmount = targetUserWithSimulation.mbrAmount
            )
            _sendTransactionDataFlow.emit(Event(transactionData))
        }
    }

    private suspend fun isArc59Transaction(targetUserAddress: String, assetId: Long): Boolean {
        return false

        // Rely on the assetType already determined for the current assetTransaction
        /*if (this.assetTransaction.assetType == AssetType.ARC200) {
            return false // ARC-200 tokens do not use the Arc59 opt-in/inbox flow
        }

        // Original logic for ASAs (and if assetType is somehow not ARC200):
        // if recipient hasn't opted in, it's an "Arc59 transaction" (needs inbox/causes API call)
        return getAccountInformation(targetUserAddress)?.hasAsset(assetId) == false*/
    }

    private suspend fun isArc200Transaction(targetUserAddress: String, assetId: Long): Boolean {
        return this.getAsset(assetId)?.assetType == AssetType.ARC200
    }

    fun isExpressSendWarningEnabled(isArc59Transaction: Boolean): Boolean {
        // return arc59ExpressSendUseCase.isExpressSendWarningEnabled(isArc59Transaction)
        return false // Force disable the express send/asset inbox flow from this ViewModel
    }

    companion object {
        private const val ASSET_TRANSACTION_KEY = "assetTransaction"
        private const val QUERY_DEBOUNCE = 300L
    }
}

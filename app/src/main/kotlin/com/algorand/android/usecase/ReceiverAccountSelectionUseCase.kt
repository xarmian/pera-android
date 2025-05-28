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

package com.algorand.android.usecase

import com.algorand.android.models.TargetUserWithSimulation
import com.algorand.android.R
import com.algorand.android.SendAlgoNavigationDirections
import com.algorand.android.models.AnnotatedString
import com.algorand.android.models.BaseAccountSelectionListItem
import com.algorand.android.models.Result
import com.algorand.android.models.TargetUser
import com.algorand.android.models.User
import com.algorand.android.modules.accountasset.GetAccountAssetUseCase
import com.algorand.android.modules.accountasset.domain.model.AccountAssetDetail
import com.algorand.android.modules.accountcore.domain.usecase.GetAccountBaseOwnedAssetData
import com.algorand.android.modules.accountcore.ui.accountselection.usecase.GetAccountSelectionAccountItems
import com.algorand.android.modules.accountcore.ui.accountselection.usecase.GetAccountSelectionContactItems
import com.algorand.android.modules.accountcore.ui.accountselection.usecase.GetAccountSelectionItemsFromAccountAddress
import com.algorand.android.modules.accountcore.ui.accountselection.usecase.GetAccountSelectionNameServiceItems
import com.algorand.android.modules.accountcore.ui.usecase.GetAccountIconDrawablePreview
import com.algorand.android.modules.assetinbox.send.summary.ui.model.Arc59SendSummaryNavArgs
import com.algorand.android.ui.send.receiveraccount.ReceiverAccountSelectionFragmentDirections
import com.algorand.android.utils.exceptions.GlobalException
import com.algorand.android.utils.exceptions.NavigationException
import com.algorand.android.utils.exceptions.WarningException
import com.algorand.android.utils.formatAsAlgoString
import com.algorand.android.utils.isValidAddress
import com.algorand.android.utils.validator.AccountTransactionValidator
import com.algorand.wallet.account.detail.domain.model.AccountType.Companion.canSignTransaction
import com.algorand.wallet.account.detail.domain.usecase.GetAccountState
import com.algorand.wallet.account.info.domain.usecase.GetAccountInformation
import com.algorand.wallet.asset.domain.model.AssetType
import com.algorand.android.models.Arc200TransferSimulator
import com.algorand.wallet.asset.domain.usecase.FetchAsset
import com.algorand.wallet.asset.domain.usecase.GetAsset
import com.algorand.wallet.asset.domain.util.AssetConstants.ALGO_ID
import com.algorand.wallet.foundation.PeraResult
import com.algorand.wallet.nameservice.domain.usecase.GetAccountNameService
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import com.algorand.android.repository.Arc200Repository

@Suppress("LongParameterList")
class ReceiverAccountSelectionUseCase @Inject constructor(
    private val contactUseCase: ContactUseCase,
    private val accountTransactionValidator: AccountTransactionValidator,
    private val getAccountBaseOwnedAssetData: GetAccountBaseOwnedAssetData,
    private val getAccountIconDrawablePreview: GetAccountIconDrawablePreview,
    private val getAccountSelectionContactItems: GetAccountSelectionContactItems,
    private val getAccountSelectionNameServiceItems: GetAccountSelectionNameServiceItems,
    private val getAccountSelectionItemsFromAccountAddress: GetAccountSelectionItemsFromAccountAddress,
    private val getAccountSelectionAccountItems: GetAccountSelectionAccountItems,
    private val getAccountInformation: GetAccountInformation,
    private val getAccountState: GetAccountState,
    private val getAccountNameService: GetAccountNameService,
    getAccountAssetUseCase: GetAccountAssetUseCase,
    private val getAsset: GetAsset,
    private val fetchAsset: FetchAsset,
    private val arc200TransferSimulator: Arc200TransferSimulator,
    private val arc200Repository: Arc200Repository
) : BaseSendAccountSelectionUseCase(getAccountAssetUseCase) {

    fun getToAccountList(
        query: String,
        latestCopiedMessage: String?
    ): Flow<List<BaseAccountSelectionListItem>> {
        val contactList = fetchContactList(query)
        val accountList = fetchAccountList(query)
        val nftDomainAccountList = fetchNftDomainAccountList(query)
        val queriedAddress = query.takeIf { it.isValidAddress() }
        return combine(
            accountList,
            contactList,
            nftDomainAccountList
        ) { accounts, contacts, nftDomainAccounts ->
            mutableListOf<BaseAccountSelectionListItem>().apply {
                createPasteItem(latestCopiedMessage)?.run { add(this) }
                createQueriedAccountItem(
                    accountAddresses = accounts.map { it.address },
                    contactAddresses = contacts.map { it.address },
                    queriedAddress = queriedAddress
                )?.run {
                    add(BaseAccountSelectionListItem.HeaderItem(R.string.account))
                    add(this)
                }
                if (nftDomainAccounts.isNotEmpty()) {
                    add(BaseAccountSelectionListItem.HeaderItem(R.string.matched_accounts))
                    addAll(nftDomainAccounts)
                }
                if (accounts.isNotEmpty()) {
                    add(BaseAccountSelectionListItem.HeaderItem(R.string.my_accounts))
                    addAll(accounts)
                }
                if (contacts.isNotEmpty()) {
                    add(BaseAccountSelectionListItem.HeaderItem(R.string.contacts))
                    addAll(contacts)
                }
            }
        }
    }

    private fun createPasteItem(latestCopiedMessage: String?): BaseAccountSelectionListItem.PasteItem? {
        return latestCopiedMessage.takeIf { it.isValidAddress() }?.let { copiedAccount ->
            BaseAccountSelectionListItem.PasteItem(copiedAccount)
        }
    }

    private suspend fun createQueriedAccountItem(
        queriedAddress: String?,
        accountAddresses: List<String>,
        contactAddresses: List<String>
    ): BaseAccountSelectionListItem.BaseAccountItem.AccountItem? {
        if (queriedAddress.isNullOrBlank()) return null
        val shouldInsertQueriedAccount = shouldInsertQueriedAccount(
            accountAddresses = accountAddresses,
            contactAddresses = contactAddresses,
            queriedAccount = queriedAddress
        )
        if (!shouldInsertQueriedAccount) return null
        return getAccountSelectionItemsFromAccountAddress(accountAddress = queriedAddress)
    }

    private fun shouldInsertQueriedAccount(
        accountAddresses: List<String>,
        contactAddresses: List<String>,
        queriedAccount: String?
    ): Boolean {
        return !accountAddresses.contains(queriedAccount) && !contactAddresses.contains(queriedAccount)
    }

    private fun fetchContactList(query: String) = flow {
        val contacts = getAccountSelectionContactItems().filter {
            it.displayName.contains(query, true) || it.address.contains(query, true)
        }
        emit(contacts)
    }

    private fun fetchNftDomainAccountList(query: String) = flow {
        val nftDomainAccounts = getAccountSelectionNameServiceItems(query)
        emit(nftDomainAccounts)
    }

    private fun fetchAccountList(query: String) = flow {
        val localAccounts = getAccountSelectionAccountItems(
            showHoldings = false,
            showFailedAccounts = false
        ).filter { it.displayName.contains(query, true) || it.address.contains(query, true) }
        emit(localAccounts)
    }

    fun isAccountAddressValid(toAccountPublicKey: String): Result<String> {
        return accountTransactionValidator.isAccountAddressValid(toAccountPublicKey)
    }

    @SuppressWarnings("ReturnCount", "LongMethod")
    suspend fun checkToAccountTransactionRequirements(
        accountAssetDetail: AccountAssetDetail,
        assetId: Long,
        fromAccountAddress: String,
        amount: BigInteger,
        nftDomainAddress: String?,
        nftDomainServiceLogoUrl: String?
    ): Result<TargetUserWithSimulation> {
        val isSelectedAssetValid = accountTransactionValidator.isSelectedAssetValid(fromAccountAddress, assetId)
        val receiverAccountType = getAccountState(accountAssetDetail.address).accountType
        val isReceiverAccountInMyWallet = receiverAccountType?.canSignTransaction() == true
        if (!isSelectedAssetValid) {
            // TODO: 18.03.2022 Find better exception message
            return Result.Error(Exception())
        }

        // Determine AssetType for the current assetId
        val cachedAsset = getAsset(assetId)
        var currentAssetType = cachedAsset?.assetType
        if (assetId != ALGO_ID && (currentAssetType == null || currentAssetType == AssetType.ASA)) {
            when (val fetchResult = fetchAsset(assetId)) {
                is PeraResult.Success -> {
                    if (fetchResult.data.assetType == AssetType.ARC200) {
                        currentAssetType = AssetType.ARC200
                        // Consider if fetchAndCacheAssets is needed here too
                    }
                }
                is PeraResult.Error -> {
                    com.algorand.android.utils.sendErrorLog(
                        "ReceiverAccountSelectionUseCase: Failed to fetch asset type for $assetId: ${fetchResult.exception.message}"
                    )
                    /* proceed with cached/default type */
                }
            }
        }
        if (currentAssetType == null && assetId != ALGO_ID) currentAssetType = AssetType.ASA

        // For ARC-200 assets, determine MBR need and simulate
        if (currentAssetType == AssetType.ARC200 && assetId != ALGO_ID) {
            // Determine if MBR payment is likely required for the receiver using ARC-200 specific balance check
            var isMbrPaymentActuallyRequired = true // Default to true, assume MBR needed if check fails or balance is zero
            when (val arc200HoldingResult = arc200Repository.getArc200AssetHolding(accountAssetDetail.address, assetId)) {
                is Result.Success -> {
                    isMbrPaymentActuallyRequired = arc200HoldingResult.data.amount == BigInteger.ZERO
                }
                is Result.Error -> {
                    com.algorand.android.utils.sendErrorLog(
                        "ReceiverAUC: Failed to get ARC-200 holding for ${accountAssetDetail.address}, asset $assetId. Error: ${arc200HoldingResult.exception.message}"
                    )
                    // Assuming MBR is required to be safe if balance check fails
                    isMbrPaymentActuallyRequired = true
                }
            }

            // Prepare minimal suggested params for the simulation call if needed, or pass null
            val minimalSuggestedParams = com.algorand.algosdk.v2.client.model.TransactionParametersResponse().apply {
                this.fee = Arc200TransferSimulator.DEFAULT_FEE // Use default from simulator
                // lastRound, genesisId, genesisHash can be left to be fetched by simulator if needed
            }

            try {
                // Get sender account info to check for rekey admin address
                val senderAccountInfo = getAccountInformation(fromAccountAddress)
                val senderAuthAddress = senderAccountInfo?.rekeyAdminAddress
                
                println(
                    "About to call arc200TransferSimulator.simulateArc200TransferWithMbrCheck with isMbrRequired=" +
                        isMbrPaymentActuallyRequired + ", senderAuthAddress=" + senderAuthAddress
                )
                val simulationResult = arc200TransferSimulator.simulateArc200TransferWithMbrCheck(
                    senderAddress = fromAccountAddress,
                    receiverAddress = accountAssetDetail.address,
                    arc200AppId = assetId,
                    amount = amount,
                    isMbrPaymentActuallyRequired = isMbrPaymentActuallyRequired,
                    providedSuggestedParams = minimalSuggestedParams,
                    senderAuthAddress = senderAuthAddress
                )

                println(
                    "ARC-200 simulation result: requiresMbrPaymentTransaction=${simulationResult.requiresMbrPaymentTransaction}, " +
                        "mbrAmount=${simulationResult.mbrAmount}, " +
                        "failureMessage=${simulationResult.failureMessage}, " +
                        "logs=${simulationResult.logs?.joinToString()}"
                )

                if (simulationResult.failureMessage != null) {
                    println("Simulation failed with message: ${simulationResult.failureMessage}")
                    // Depending on product requirements, might return error or allow proceeding
                    // For now, let's assume a simulation failure that's not just an MBR hint means we stop
                    // However, the plan is to use simulation for box discovery primarily.
                    // If failureMessage indicates a real problem beyond MBR, then it's an error.
                    // For now, we pass the simulationResponse anyway for box extraction if possible.
                }

                // The TargetUserWithSimulation now needs to carry the simulation response and if MBR was part of it
                return Result.Success(
                    TargetUserWithSimulation(
                        targetUser = TargetUser(
                            contact = getContactByAddressIfExists(accountAssetDetail.address),
                            publicKey = accountAssetDetail.address,
                            algoBalance = accountAssetDetail.algoAmount,
                            minBalance = accountAssetDetail.minBalanceRequired,
                            nftDomainAddress = nftDomainAddress,
                            nftDomainServiceLogoUrl = nftDomainServiceLogoUrl,
                            accountIconDrawablePreview = getAccountIconDrawablePreview(accountAssetDetail.address)
                        ),
                        simulationResponse = simulationResult.simulationResponse, // Critical for TransactionSignManager
                        isMbrPaymentSimulated = simulationResult.requiresMbrPaymentTransaction, // New flag
                        mbrAmount = simulationResult.mbrAmount?.toBigInteger()
                    )
                )
            } catch (e: Exception) {
                println("Exception during ARC-200 simulation call: ${e.message}")
                e.printStackTrace()
                // Log the error and return a generic error or a more specific one based on e
                return Result.Error(
                    GlobalException(
                        titleRes = R.string.error,
                        descriptionRes = 0
                    )
                )
            }
        }

        val shouldCheckOptInAndNavigateToInbox =
            currentAssetType != AssetType.ARC200 && // Only for non-ARC200
            accountAssetDetail.assetDetail == null && // Recipient doesn't hold the asset
            assetId != ALGO_ID && // Not ALGO
            !isReceiverAccountInMyWallet // Not one of my accounts

        if (shouldCheckOptInAndNavigateToInbox) {
            val nextDirection = ReceiverAccountSelectionFragmentDirections
                .actionReceiverAccountSelectionFragmentToArc59RequestOptInNavigation(
                    Arc59SendSummaryNavArgs(
                        fromAccountAddress,
                        accountAssetDetail.address,
                        assetId,
                        amount
                    )
                )
            return Result.Error(NavigationException(nextDirection))
        }

        if (assetId == ALGO_ID) {
            val minBalance = accountAssetDetail.minBalanceRequired
            val toAccountAlgoBalance = accountAssetDetail.algoAmount
            val isSendingAmountValid = accountTransactionValidator.isSendingAmountLesserThanMinimumBalance(
                toAccountAlgoBalance,
                amount,
                minBalance
            )
            if (isSendingAmountValid) {
                val warningBodyMessage = AnnotatedString(
                    R.string.you_re_trying_to_send,
                    listOf("amount" to (minBalance - toAccountAlgoBalance).formatAsAlgoString())
                )
                return Result.Error(WarningException(R.string.warning, warningBodyMessage))
            }
        }

        val ownedAssetData = getAccountBaseOwnedAssetData(fromAccountAddress, assetId)
        val isSendingMaxAmountToSameAccount = accountTransactionValidator.isSendingMaxAmountToTheSameAccount(
            fromAccount = fromAccountAddress,
            toAccount = accountAssetDetail.address,
            maxAmount = ownedAssetData?.amount ?: BigInteger.ZERO,
            amount = amount,
            isAlgo = ownedAssetData?.isAlgo ?: false
        )

        if (isSendingMaxAmountToSameAccount) {
            return Result.Error(GlobalException(descriptionRes = R.string.you_can_not_send_your))
        }

        val isCloseTransactionToSameAccount = accountTransactionValidator.isCloseTransactionToSameAccount(
            getAccountInformation(fromAccountAddress),
            accountAssetDetail.address,
            ownedAssetData,
            amount
        )

        if (isCloseTransactionToSameAccount) {
            return Result.Error(GlobalException(descriptionRes = R.string.you_can_not_send_your))
        }

        val isAccountNewlyOpenedAndBalanceInvalid = accountTransactionValidator.isAccountNewlyOpenedAndBalanceInvalid(
            accountAssetDetail,
            amount,
            assetId
        )
        if (isAccountNewlyOpenedAndBalanceInvalid) {
            // TODO: 18.02.2022 Move all navigation logic into the presentation layer
            return Result.Error(
                NavigationException(
                    SendAlgoNavigationDirections.actionGlobalSingleButtonBottomSheet(
                        titleAnnotatedString = AnnotatedString(R.string.minimum_amount_required),
                        descriptionAnnotatedString = AnnotatedString(R.string.this_is_the_first_transaction),
                        buttonStringResId = R.string.i_understand,
                        drawableResId = R.drawable.ic_info,
                        drawableTintResId = R.color.error_tint_color
                    )
                )
            )
        }

        val toAccountPublicKey = accountAssetDetail.address
        val existingContact = getContactByAddressIfExists(toAccountPublicKey)
        val nameService = getAccountNameService(toAccountPublicKey)

        val targetUserContact = when {
            // If we have name service info, use it primarily for the contact
            nameService != null -> User(
                name = nameService.nameServiceName ?: existingContact?.name ?: toAccountPublicKey,
                publicKey = toAccountPublicKey,
                imageUriAsString = nameService.nameServiceUri ?: existingContact?.imageUriAsString,
                contactDatabaseId = existingContact?.contactDatabaseId ?: 0
            )
            // If no name service, use existing contact if available
            existingContact != null -> existingContact
            // Otherwise, no contact info available
            else -> null
        }

        val targetUser = TargetUser(
            contact = targetUserContact,
            publicKey = toAccountPublicKey,
            algoBalance = accountAssetDetail.algoAmount,
            minBalance = accountAssetDetail.minBalanceRequired,
            nftDomainAddress = nftDomainAddress,
            nftDomainServiceLogoUrl = nftDomainServiceLogoUrl,
            accountIconDrawablePreview = getAccountIconDrawablePreview(toAccountPublicKey)
        )
        return Result.Success(TargetUserWithSimulation(targetUser, simulationResponse = null))
    }

    private suspend fun getContactByAddressIfExists(accountAddress: String): User? {
        return contactUseCase.getAllContacts().firstOrNull { it.publicKey == accountAddress }
    }
}

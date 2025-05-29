/*
 * Copyright 2025 Pera Wallet, LDA
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.algorand.android.core.transaction

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.coroutineScope
import com.algorand.algosdk.sdk.BytesArray
import com.algorand.android.R
import com.algorand.android.ledger.CustomScanCallback
import com.algorand.android.ledger.LedgerBleOperationManager
import com.algorand.android.ledger.LedgerBleSearchManager
import com.algorand.android.ledger.operations.TransactionSignOperation
import com.algorand.android.models.AnnotatedString
import com.algorand.android.models.Arc59TransactionData
import com.algorand.android.models.LedgerBleResult
import com.algorand.android.models.Result
import com.algorand.android.models.SignedTransactionDetail
import com.algorand.android.models.TransactionManagerResult
import com.algorand.android.models.TransactionManagerResult.Error.GlobalWarningError.Defined
import com.algorand.android.models.TransactionManagerResult.Error.GlobalWarningError.MinBalanceError
import com.algorand.android.models.TransactionParams
import com.algorand.android.models.TransactionSignData
import com.algorand.android.repository.TransactionsRepository
import com.algorand.android.utils.Event
import com.algorand.android.utils.LifecycleScopedCoroutineOwner
import com.algorand.android.utils.ListQueuingHelper
import com.algorand.android.utils.TransactionSignSigningHelper
import com.algorand.android.utils.assignGroupId
import com.algorand.android.utils.flatten
import com.algorand.android.utils.formatAsAlgoString
import com.algorand.android.utils.getReceiverMinBalanceFee
import com.algorand.android.utils.getTxFee
import com.algorand.android.utils.isLesserThan
import com.algorand.android.utils.makeAddAssetTx
import com.algorand.android.utils.makeArc59Txn
import com.algorand.android.utils.makeRekeyTx
import com.algorand.android.utils.makeRemoveAssetTx
import com.algorand.android.utils.makeSendAndRemoveAssetTx
import com.algorand.android.utils.makeTx
import com.algorand.android.utils.mapToNotNullableListOrNull
import com.algorand.android.utils.minBalancePerAssetAsBigInteger
import com.algorand.android.utils.sendErrorLog
import com.algorand.android.utils.signTx
import com.algorand.android.utils.toBytesArray
import com.algorand.wallet.account.core.domain.model.TransactionSigner
import com.algorand.wallet.account.core.domain.usecase.GetAccountMinBalance
import com.algorand.wallet.account.info.domain.usecase.GetAccountInformation
import com.algorand.wallet.account.local.domain.model.LocalAccount
import com.algorand.wallet.account.local.domain.usecase.GetAlgo25SecretKey
import com.algorand.wallet.account.local.domain.usecase.GetHdSeed
import com.algorand.wallet.account.local.domain.usecase.GetLocalAccount
import com.algorand.wallet.algosdk.transaction.sdk.SignHdKeyTransaction
import com.algorand.wallet.asset.domain.util.AssetConstants.ALGO_ID
import java.math.BigInteger
import java.net.ConnectException
import java.net.SocketException
import javax.inject.Inject
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import com.algorand.android.utils.makeApplicationCallTxnWithAbi
import com.algorand.android.utils.toSuggestedParams
import com.algorand.wallet.contract.arc200.Arc200Abi
import android.util.Log
import com.algorand.android.models.Arc200TransactionData
import org.json.JSONObject
import org.json.JSONArray
import com.algorand.wallet.account.core.domain.usecase.GetTransactionSigner

private data class SimulationBoxReference(
    val appId: Long,
    val nameBase64: String
)

private fun extractBoxReferencesFromSimulation(simulationResponse: String, transactionAppId: Long): List<SimulationBoxReference> {
    val boxList = mutableListOf<SimulationBoxReference>()
    try {
        val json = JSONObject(simulationResponse)
        val txnGroups = json.getJSONArray("txn-groups")
        if (txnGroups.length() == 0) return emptyList()

        val firstGroup = txnGroups.getJSONObject(0)

        // Try to extract boxes from the original format: unnamed-resources-accessed.boxes
        var boxes: JSONArray? = null
        try {
            val unnamedResources = firstGroup.getJSONObject("unnamed-resources-accessed")
            boxes = unnamedResources.getJSONArray("boxes")
        } catch (e: Exception) {
            Log.d("Simulation", "unnamed-resources-accessed format not found, trying alternative format")
        }

        // If not found in original format, try the alternative format: txn-results[0].txn-result.txn.txn.apbx
        if (boxes == null) {
            try {
                val txnResults = firstGroup.getJSONArray("txn-results")
                if (txnResults.length() > 0) {
                    val firstResult = txnResults.getJSONObject(0)
                    val txnResult = firstResult.getJSONObject("txn-result")
                    val outerTxn = txnResult.getJSONObject("txn")
                    val innerTxn = outerTxn.getJSONObject("txn")
                    boxes = innerTxn.getJSONArray("apbx")
                }
            } catch (e: Exception) {
                Log.d("Simulation", "apbx format not found either", e)
            }
        }

        // Process the boxes if found
        if (boxes != null) {
            for (i in 0 until boxes.length()) {
                val box = boxes.getJSONObject(i)
                val nameBase64 = when {
                    box.has("name") -> box.getString("name") // Original format
                    box.has("n") -> box.getString("n") // apbx format
                    else -> {
                        Log.w("Simulation", "Box at index $i has neither 'name' nor 'n' field")
                        continue
                    }
                }

                boxList.add(
                    SimulationBoxReference(
                        appId = transactionAppId,
                        nameBase64 = nameBase64
                    )
                )
            }
        } else {
            Log.d("Simulation", "No boxes found in either format")
        }
    } catch (e: Exception) {
        Log.e("Simulation", "Error extracting box references", e)
    }
    return boxList
}

@Suppress("LongParameterList")
class TransactionSignManager @Inject constructor(
    private val ledgerBleSearchManager: LedgerBleSearchManager,
    private val transactionsRepository: TransactionsRepository,
    private val ledgerBleOperationManager: LedgerBleOperationManager,
    private val signHelper: TransactionSignSigningHelper,
    private val getAccountInformation: GetAccountInformation,
    private val getAccountMinBalance: GetAccountMinBalance,
    private val getAlgo25SecretKey: GetAlgo25SecretKey,
    private val getHdSeed: GetHdSeed,
    private val getLocalAccount: GetLocalAccount,
    private val signHdKeyTransaction: SignHdKeyTransaction,
    private val getTransactionSigner: GetTransactionSigner
) : LifecycleScopedCoroutineOwner() {

    val transactionManagerResultLiveData = MutableLiveData<Event<TransactionManagerResult>?>()

    private var transactionParams: TransactionParams? = null
    var transactionDataList: List<TransactionSignData>? = null

    private fun logTransactionBoxesForDebug(tag: String, txnBytes: ByteArray) {
        try {
            // Decode the transaction from bytes using Encoder
            val txn = com.algorand.algosdk.util.Encoder.decodeFromMsgPack(txnBytes,
                com.algorand.algosdk.transaction.Transaction::class.java)

            // For ApplicationCall transactions, log box references
            if (txn.type == com.algorand.algosdk.transaction.Transaction.Type.ApplicationCall) {
                // Log boxes if they exist
                txn.boxReferences?.forEachIndexed { index, boxRef ->
                    val nameHex = boxRef.name?.joinToString("") { "%02x".format(it) } ?: "null"
                    val nameBase64 = boxRef.name?.let {
                        com.algorand.algosdk.util.Encoder.encodeToBase64(it)
                    } ?: "null"
                } ?: Log.d(tag, "No box references in transaction")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error logging transaction boxes: ${e.message}")
        }
    }

    private val scanCallback = object : CustomScanCallback() {
        override fun onLedgerScanned(
            device: BluetoothDevice,
            currentTransactionIndex: Int?,
            totalTransactionCount: Int?
        ) {
            ledgerBleSearchManager.stop()
            currentScope.launch {
                signHelper.currentItem?.run {
                    ledgerBleOperationManager.startLedgerOperation(
                        newOperation = TransactionSignOperation(device, this),
                        currentTransactionIndex = currentTransactionIndex,
                        totalTransactionCount = totalTransactionCount
                    )
                }
            }
        }

        override fun onScanError(errorMessageResId: Int, titleResId: Int) {
            setSignFailed(TransactionManagerResult.LedgerScanFailed)
        }
    }

    private val operationManagerCollectorAction: (suspend (Event<LedgerBleResult>?) -> Unit) = { ledgerBleResultEvent ->
        ledgerBleResultEvent?.consume()?.run {
            when (this) {
                is LedgerBleResult.LedgerWaitingForApproval -> {
                    postResult(TransactionManagerResult.LedgerWaitingForApproval(bluetoothName))
                }
                is LedgerBleResult.SignedTransactionResult -> checkAndCacheSignedTransaction(transactionByteArray)
                is LedgerBleResult.LedgerErrorResult -> {
                    setSignFailed(TransactionManagerResult.Error.GlobalWarningError.Api(errorMessage))
                }
                is LedgerBleResult.AppErrorResult -> setSignFailed(Defined(AnnotatedString(errorMessageId), titleResId))
                is LedgerBleResult.OperationCancelledResult -> setSignFailed(
                    Defined(AnnotatedString(R.string.error_cancelled_message), R.string.error_cancelled_title)
                )
                is LedgerBleResult.OnMissingBytes -> setSignFailed(
                    Defined(AnnotatedString(R.string.error_sending_message), R.string.error_bluetooth_title)
                )
                else -> sendErrorLog("Unhandled else case in operationManagerCollectorAction")
            }
        }
    }

    private val signHelperListener = object : ListQueuingHelper.Listener<TransactionSignData, ByteArray> {
        override fun onAllItemsDequeued(signedTransactions: List<ByteArray?>) {
            if (signedTransactions.isEmpty() || signedTransactions.any { it == null }) {
                setSignFailed(Defined(AnnotatedString(stringResId = R.string.an_error_occured)))
                return
            }
            if (signedTransactions.size == 1) {
                transactionDataList?.let { postTxnSignResult(signedTransactions.firstOrNull(), it.firstOrNull()) }
            } else {
                val safeSignedTransactions = signedTransactions.mapToNotNullableListOrNull { it }
                if (safeSignedTransactions == null) {
                    postResult(Defined(AnnotatedString(stringResId = R.string.an_error_occured)))
                    return
                }
                transactionDataList?.let { postGroupTxnSignResult(safeSignedTransactions, it) }
            }
        }

        override fun onNextItemToBeDequeued(
            transaction: TransactionSignData,
            currentItemIndex: Int,
            totalItemCount: Int
        ) {
            // TODO: add [currentItemIndex] and [totalItemCount] after merging this core swap screens
            currentScope.launch {
                transaction.signTxn()
            }
        }
    }

    private suspend fun checkAndCacheSignedTransaction(transactionByteArray: ByteArray?) {
        if (transactionByteArray == null) {
            setSignFailed(Defined(AnnotatedString(R.string.unknown_error)))
            return
        }
        signHelper.currentItem?.run {
            if (isArc59Transaction) {
                return@run
            }
            calculatedFee = transactionParams?.getTxFee(transactionByteArray)
            if (this is TransactionSignData.Send && projectedFee != calculatedFee) {
                currentScope.launch { resignCurrentTransaction() }
                return
            }

            if (isMinimumLimitViolated()) {
                return
            }
        }
        signHelper.cacheDequeuedItem(transactionByteArray)
    }

    private fun setSignFailed(transactionManagerResult: TransactionManagerResult) {
        postResult(transactionManagerResult)
        signHelper.clearCachedData()
    }

    private suspend fun resignCurrentTransaction() {
        signHelper.currentItem?.createTransaction()
        signHelper.requeueCurrentItem()
    }

    fun setup(lifecycle: Lifecycle) {
        assignToLifecycle(lifecycle)
        setupLedgerOperationManager(lifecycle)
        signHelper.initListener(signHelperListener)
    }

    private fun setupLedgerOperationManager(lifecycle: Lifecycle) {
        ledgerBleOperationManager.setup(lifecycle)
        lifecycle.coroutineScope.launch {
            ledgerBleOperationManager.ledgerBleResultFlow.collect {
                operationManagerCollectorAction.invoke(it)
            }
        }
    }

    fun initSigningTransactions(isGroupTransaction: Boolean, vararg transactionData: TransactionSignData) {
        currentScope.launch {
            postResult(TransactionManagerResult.Loading)
            transactionData.toList().ifEmpty {
                setSignFailed(Defined(AnnotatedString(stringResId = R.string.an_error_occured)))
                return@launch
            }.let { transactionList ->
                processTransactionDataList(transactionList, isGroupTransaction)?.let {
                    this@TransactionSignManager.transactionDataList = it
                    signHelper.initItemsToBeEnqueued(it)
                }
            }
        }
    }

    suspend fun createArc59SendTransactionList(transactionData: TransactionSignData): List<Arc59TransactionData>? {
        return transactionData.createArc59SendTransactions()
    }

    suspend fun createArc200SendTransactionList(transactionData: TransactionSignData, simulationResponse: String?): List<Arc200TransactionData>? {
        if (simulationResponse == null) return null
        return transactionData.createArc200SendTransactions(simulationResponse)
    }

    suspend fun getReceiverMinBalanceFee(transactionData: TransactionSignData): Long? {
        val transactionParams = getTransactionParams(transactionData) ?: return null
        this@TransactionSignManager.transactionParams = transactionParams

        val sendTransaction = transactionData as? TransactionSignData.Send ?: return null
        val receiverAlgoAmount = sendTransaction.targetUser.algoBalance ?: return null
        val receiverMinBalanceAmount = sendTransaction.targetUser.minBalance ?: return null

        return getReceiverMinBalanceFee(
            receiverAlgoAmount = receiverAlgoAmount,
            receiverMinBalanceAmount = receiverMinBalanceAmount
        )
    }

    private suspend fun TransactionSignData.signTxn() {
        when (signer) {
            is TransactionSigner.Algo25 -> {
                val secretKey = getAlgo25SecretKey(signer.address) ?: run {
                    Log.e("TransactionSignManager", "Failed to get secret key for address: ${signer.address}")
                    setSignFailed(Defined(AnnotatedString(stringResId = R.string.an_error_occured)))
                    return
                }
                val signedTx = transactionByteArray?.signTx(secretKey)
                checkAndCacheSignedTransaction(signedTx)
            }
            is TransactionSigner.HdKey -> {
                val transactionBytes = transactionByteArray ?: return handleSignError()
                val hdKey = getLocalAccount(signer.address) as? LocalAccount.HdKey ?: return handleSignError()
                val seed = getHdSeed(seedId = hdKey.seedId) ?: return handleSignError()
                val signedTransaction = signHdKeyTransaction.signTransaction(
                    transactionBytes, seed, hdKey.account, hdKey.change, hdKey.keyIndex
                ) ?: return handleSignError()
                checkAndCacheSignedTransaction(signedTransaction)
            }
            is TransactionSigner.LedgerBle -> sendTransactionWithLedger(signer as TransactionSigner.LedgerBle)
            is TransactionSigner.SignerNotFound -> {
                postResult(Defined(AnnotatedString(stringResId = R.string.the_signing_account_has)))
            }
        }
    }

    private fun handleSignError() {
        setSignFailed(Defined(AnnotatedString(stringResId = R.string.an_error_occured)))
    }

    private suspend fun TransactionSignData.createArc59SendTransactions(): List<Arc59TransactionData>? {
        val transactionParams = getTransactionParams(this) ?: return null
        this@TransactionSignManager.transactionParams = transactionParams
        val arc59TransactionData = mutableListOf<Arc59TransactionData>()
        (this as? TransactionSignData.Send)?.let {
            projectedFee = calculatedFee ?: transactionParams.getTxFee()
            // calculate isMax before calculating real amount because while isMax true fee will be deducted.
            isMax = isTransactionMax(amount, senderAccountAddress, assetId)
            amount = calculateAmount(
                projectedAmount = amount,
                isMax = isMax,
                isSenderRekeyedToAnotherAccount = isSenderRekeyed(),
                senderMinimumBalance = minimumBalance,
                assetId = assetId,
                fee = projectedFee
            ) ?: return null

            if (isSenderRekeyed()) {
                // if account is rekeyed to another account, min balance should be deducted from the amount.
                // after it'll be deducted, isMax will be false to not write closeToAddress.
                isMax = false
            }

            if (isCloseToSameAccount()) {
                return null
            }

            val transactions = transactionParams.makeArc59Txn(
                senderAddress = senderAccountAddress,
                receiverAddress = targetUser.publicKey,
                transactionAmount = amount,
                senderAlgoAmount = senderAlgoAmount,
                senderMinBalanceAmount = minimumBalance.toBigInteger(),
                receiverAlgoAmount = targetUser.algoBalance ?: BigInteger.ZERO,
                receiverMinBalanceAmount = targetUser.minBalance ?: BigInteger.ZERO,
                assetId = assetId,
                note = if (xnote.isNullOrBlank()) note else xnote
            )

            for (i in 0 until transactions.length()) {
                val txn = transactions.getTxn(i)
                val signer = transactions.getSigner(i)
                arc59TransactionData.add(Arc59TransactionData(txn, signer))
            }
        }
        return arc59TransactionData
    }

    private suspend fun TransactionSignData.createArc200SendTransactions(simulationResponse: String): List<Arc200TransactionData>? {
        val transactionParams = getTransactionParams(this) ?: return null
        this@TransactionSignManager.transactionParams = transactionParams
        val arc200TransactionData = mutableListOf<Arc200TransactionData>()

        (this as? TransactionSignData.Send)?.let {
            val boxes = try {
                extractBoxReferencesFromSimulation(simulationResponse, this.assetId)
            } catch (e: Exception) {
                Log.e(
                    "TransactionSignManager",
                    "Failed to extract box references from simulation",
                    e
                )
                return null
            }

            val boxesList = boxes.map { boxRef ->
                // Convert base64 to ByteArray for AppBoxReference
                val boxNameBytes =
                    android.util.Base64.decode(boxRef.nameBase64, android.util.Base64.NO_WRAP)
                com.algorand.algosdk.transaction.AppBoxReference(
                    this.assetId,
                    boxNameBytes
                )
            }

            val appCallTxn = makeApplicationCallTxnWithAbi(
                senderAddress = this.senderAccountAddress,
                appId = this.assetId,
                method = Arc200Abi.arc200TransferMethod,
                methodArgs = listOf(
                    com.algorand.algosdk.crypto.Address(this.targetUser.publicKey),
                    this.amount
                ),
                suggestedParams = transactionParams.toSuggestedParams()
                    .toTransactionParametersResponse(),
                note = note?.toByteArray(Charsets.UTF_8),
                foreignApps = listOf(this.assetId),
                accounts = listOf(this.senderAccountAddress),
                boxes = boxesList
            )

            val signerAddress = this.senderAuthAddress ?: this.senderAccountAddress
            val signer = getTransactionSigner(signerAddress)
            val additionalSigner = getTransactionSigner(signerAddress)

            if (isMbrPaymentActuallyRequired == true) {
                val escrowAddress = com.algorand.algosdk.crypto.Address.forApplication(this.assetId)
                val additionalPaymentTxn = com.algorand.algosdk.transaction.Transaction.PaymentTransactionBuilder()
                    .sender(this.senderAccountAddress)
                    .receiver(escrowAddress)
                    .amount(mbrAmount)
                    .suggestedParams(
                        transactionParams.toSuggestedParams().toTransactionParametersResponse()
                    )
                    .note("MBR transaction".toByteArray()) // Optional
                    .build()

                arc200TransactionData.add(
                    Arc200TransactionData(
                        additionalPaymentTxn.bytes(),
                        additionalSigner.address
                    )
                )
            }

            arc200TransactionData.add(Arc200TransactionData(appCallTxn, signer.address))
        }

        return arc200TransactionData
    }

    @SuppressWarnings("LongMethod")
    suspend fun TransactionSignData.createTransaction(): ByteArray? {
        val transactionParams = getTransactionParams(this) ?: return null
        this@TransactionSignManager.transactionParams = transactionParams

        val createdTransactionByteArray = when (this) {
            is TransactionSignData.Send -> {
                projectedFee = calculatedFee ?: transactionParams.getTxFee()
                // calculate isMax before calculating real amount because while isMax true fee will be deducted.
                isMax = isTransactionMax(amount, senderAccountAddress, assetId)
                // TODO: 10.08.2022 Get all those calculations from a single AmountTransactionValidationUseCase
                amount = calculateAmount(
                    projectedAmount = amount,
                    isMax = isMax,
                    isSenderRekeyedToAnotherAccount = isSenderRekeyed(),
                    senderMinimumBalance = minimumBalance,
                    assetId = assetId,
                    fee = projectedFee
                ) ?: return null

                if (isSenderRekeyed()) {
                    // if account is rekeyed to another account, min balance should be deducted from the amount.
                    // after it'll be deducted, isMax will be false to not write closeToAddress.
                    isMax = false
                }

                if (isCloseToSameAccount()) {
                    return null
                }

                transactionParams.makeTx(
                    senderAddress = senderAccountAddress,
                    receiverAddress = targetUser.publicKey,
                    amount = amount,
                    assetId = assetId,
                    isMax = isMax,
                    note = if (xnote.isNullOrBlank()) note else xnote
                )
            }
            is TransactionSignData.AddAsset -> {
                transactionParams.makeAddAssetTx(senderAccountAddress, assetId)
            }
            is TransactionSignData.RemoveAsset -> {
                if (shouldCreateAssetRemoveTransaction(senderAccountAddress, assetId)) {
                    transactionParams.makeRemoveAssetTx(
                        senderAddress = senderAccountAddress,
                        creatorPublicKey = creatorAddress,
                        assetId = assetId
                    )
                } else {
                    null
                }
            }
            is TransactionSignData.SendAndRemoveAsset -> {
                transactionParams.makeSendAndRemoveAssetTx(
                    senderAddress = senderAccountAddress,
                    receiverAddress = targetUser.publicKey,
                    assetId = assetId,
                    amount = amount
                )
            }
            is TransactionSignData.Rekey -> {
                transactionParams.makeRekeyTx(senderAccountAddress, rekeyAdminAddress)
            }
        }

        transactionByteArray = createdTransactionByteArray

        return createdTransactionByteArray
    }

    private suspend fun getTransactionParams(transactionData: TransactionSignData): TransactionParams? {
        when (val result = transactionsRepository.getTransactionParams()) {
            is Result.Success -> {
                transactionParams = result.data
            }
            is Result.Error -> {
                transactionParams = null
                when (result.exception.cause) {
                    is ConnectException, is SocketException -> {
                        postResult(Defined(AnnotatedString(R.string.the_internet_connection)))
                    }
                    else -> {
                        when (transactionData) {
                            is TransactionSignData.AddAsset -> {
                                postResult(
                                    TransactionManagerResult.Error.SnackbarError.Retry(
                                        titleResId = R.string.error_while_opting_to_the,
                                        descriptionResId = null,
                                        buttonTextResId = R.string.retry
                                    )
                                )
                            }
                            is TransactionSignData.Rekey,
                            is TransactionSignData.Send,
                            is TransactionSignData.SendAndRemoveAsset,
                            is TransactionSignData.RemoveAsset -> {
                                postResult(
                                    TransactionManagerResult.Error.GlobalWarningError.Api(
                                        result.exception.message.orEmpty()
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        return transactionParams
    }

    private fun sendCurrentTransaction(bluetoothDevice: BluetoothDevice) {
        signHelper.currentItem?.run {
            ledgerBleOperationManager.startLedgerOperation(TransactionSignOperation(bluetoothDevice, this))
        }
    }

    private fun calculateAmount(
        projectedAmount: BigInteger,
        isMax: Boolean,
        isSenderRekeyedToAnotherAccount: Boolean,
        senderMinimumBalance: Long,
        assetId: Long,
        fee: Long
    ): BigInteger? {
        val calculatedAmount = if (isMax && assetId == ALGO_ID) {
            if (isSenderRekeyedToAnotherAccount) {
                projectedAmount - fee.toBigInteger() - senderMinimumBalance.toBigInteger()
            } else {
                projectedAmount - fee.toBigInteger()
            }
        } else {
            projectedAmount
        }

        if (calculatedAmount isLesserThan BigInteger.ZERO) {
            if (isSenderRekeyedToAnotherAccount) {
                val errorMinBalance = AnnotatedString(
                    stringResId = R.string.the_transaction_cannot_be,
                    replacementList = listOf("min_balance" to senderMinimumBalance.formatAsAlgoString())
                )
                postResult(Defined(errorMinBalance))
            } else {
                postResult(Defined(AnnotatedString(R.string.transaction_amount_results)))
            }
            return null
        }

        return calculatedAmount
    }

    private suspend fun isTransactionMax(amount: BigInteger, publicKey: String, assetId: Long): Boolean {
        return if (assetId != ALGO_ID) {
            false
        } else {
            getAccountInformation(publicKey)?.amount == amount
        }
    }

    private suspend fun shouldCreateAssetRemoveTransaction(publicKey: String, assetId: Long): Boolean {
        val assetHolding = getAccountInformation(publicKey)?.assetHoldings?.firstOrNull { it.assetId == assetId }
        return assetHolding != null && assetHolding.amount == BigInteger.ZERO
    }

    private fun TransactionSignData.isCloseToSameAccount(): Boolean {
        if (this is TransactionSignData.Send && isMax && senderAccountAddress == targetUser.publicKey) {
            postResult(Defined(AnnotatedString(R.string.you_can_not_send_your)))
            return true
        }
        return false
    }

    private suspend fun TransactionSignData.isMinimumLimitViolated(): Boolean {
        if (this is TransactionSignData.Send && isMax) {
            return false
        }

        // every asset addition increases min balance by $MIN_BALANCE_PER_ASSET
        var minBalance = getAccountMinBalance(senderAccountAddress)
        when (this) {
            is TransactionSignData.AddAsset ->
                minBalance += minBalancePerAssetAsBigInteger
            is TransactionSignData.RemoveAsset -> {
                minBalance -= minBalancePerAssetAsBigInteger
            }
            else -> {
                sendErrorLog("Unhandled else case in isMinimumLimitViolated")
            }
        }

        val balance = getAccountInformation(senderAccountAddress)?.amount ?: run {
            setSignFailed(Defined(AnnotatedString(stringResId = R.string.minimum_balance_required)))
            return true
        }

        val fee = calculatedFee?.toBigInteger() ?: run {
            setSignFailed(Defined(AnnotatedString(stringResId = R.string.minimum_balance_required)))
            return true
        }

        // fee only drops from the algos.
        val balanceAfterTransaction =
            if (this is TransactionSignData.Send && assetId != ALGO_ID) {
                balance - fee
            } else {
                balance - fee - amount
            }

        if (balanceAfterTransaction < minBalance) {
            if (this is TransactionSignData.AddAsset) {
                postResult(MinBalanceError(minBalance + fee))
            } else {
                val description = AnnotatedString(
                    stringResId = R.string.transaction_amount,
                    replacementList = listOf("min_balance" to minBalance.formatAsAlgoString())
                )
                postResult(Defined(description))
            }
            return true
        }

        return false
    }

    private fun postResult(transactionManagerResult: TransactionManagerResult) {
        transactionManagerResultLiveData.postValue(Event(transactionManagerResult))
    }

    private fun sendTransactionWithLedger(ledgerDetail: TransactionSigner.LedgerBle) {
        val bluetoothAddress = ledgerDetail.bluetoothAddress
        val currentConnectedDevice = ledgerBleOperationManager.connectedBluetoothDevice
        if (currentConnectedDevice != null && currentConnectedDevice.address == bluetoothAddress) {
            sendCurrentTransaction(currentConnectedDevice)
        } else {
            searchForDevice(bluetoothAddress)
        }
    }

    private fun searchForDevice(ledgerAddress: String) {
        ledgerBleSearchManager.scan(
            newScanCallback = scanCallback,
            filteredAddress = ledgerAddress,
            coroutineScope = currentScope
        )
    }

    // this also stops LedgerBleOperationManager.
    fun manualStopAllResources() {
        this.stopAllResources()
        currentScope.coroutineContext.cancelChildren()
        ledgerBleOperationManager.manualStopAllProcess()
    }

    override fun stopAllResources() {
        ledgerBleSearchManager.stop()
        transactionManagerResultLiveData.value = null
        transactionDataList = null
    }

    private suspend fun processTransactionDataList(
        transactionDataList: List<TransactionSignData>,
        isGroupTransaction: Boolean
    ): List<TransactionSignData>? {
        val processedTransactionDataList = mutableListOf<TransactionSignData>()
        for (transactionData in transactionDataList) {
            if (transactionData is TransactionSignData.Send && transactionData.assetType?.name == "ARC200") {
                val simulationResponseForArc200 = transactionData.simulationResponse
                if (simulationResponseForArc200 == null) {
                    Log.e("TransactionSignManager", "Missing simulation response for ARC-200 transaction")
                    postResult(Defined(AnnotatedString(R.string.an_error_occured))) // Or a more specific error
                    return null
                }
                val arc200SubTransactions = transactionData.createArc200SendTransactions(simulationResponseForArc200)
                    ?: return null // Error should be logged/posted by createArc200SendTransactions

                for (arc200TxData in arc200SubTransactions) {
                    // For ARC200 sub-transactions, use the original transaction's auth address if available
                    val signerAddress = transactionData.senderAuthAddress ?: arc200TxData.accountAddress
                    val signer = getTransactionSigner(signerAddress)
                    if (signer is TransactionSigner.SignerNotFound) {
                        postResult(Defined(AnnotatedString(stringResId = R.string.the_signing_account_has)))
                        return null
                    }

                    val rawTxBytes = arc200TxData.transactionByteArray
                    val decodedTx = try {
                        com.algorand.algosdk.util.Encoder.decodeFromMsgPack(
                            rawTxBytes,
                            com.algorand.algosdk.transaction.Transaction::class.java
                        )
                    } catch (e: Exception) {
                        Log.e("TransactionSignManager", "Failed to decode ARC200 sub-transaction", e)
                        postResult(Defined(AnnotatedString(R.string.an_error_occured)))
                        return null
                    }

                    val calculatedFeeForSubTx = decodedTx.fee?.toLong() ?: kotlin.run {
                        Log.e("TransactionSignManager", "Failed to get fee for ARC200 sub-transaction")
                        postResult(Defined(AnnotatedString(R.string.an_error_occured)))
                        return null
                    }

                    val newSubTxData: TransactionSignData.Send = when (decodedTx.type) {
                        com.algorand.algosdk.transaction.Transaction.Type.Payment -> {
                            // This is likely the MBR payment
                            TransactionSignData.Send(
                                senderAccountAddress = transactionData.senderAccountAddress,
                                signer = signer,
                                amount = decodedTx.amount ?: BigInteger.ZERO,
                                targetUser = com.algorand.android.models.TargetUser(
                                    publicKey = decodedTx.receiver.toString(),
                                    // Potentially fetch/set other TargetUser fields if necessary and available
                                    minBalance = BigInteger.ZERO, // Default or fetch if critical
                                    algoBalance = BigInteger.ZERO, // Default or fetch if critical
                                    accountIconDrawablePreview = null
                                ),
                                assetId = ALGO_ID,
                                note = "ARC-200 MBR Payment", // Or derive from tx note if present
                                xnote = null,
                                isMax = false,
                                projectedFee = calculatedFeeForSubTx, // Set to actual fee
                                senderSpecificAssetAmount = null, // Not relevant for Algo send
                                senderAlgoAmount = transactionData.senderAlgoAmount, // Copied from original
                                minimumBalance = transactionData.minimumBalance, // Copied from original
                                assetType = null,
                                isArc59Transaction = false,
                                isArc200Transaction = false,
                                senderAuthAddress = transactionData.senderAuthAddress,
                                senderAccountName = transactionData.senderAccountName
                            )
                        }
                        com.algorand.algosdk.transaction.Transaction.Type.ApplicationCall -> {
                            // This is the ARC-200 transfer app call
                            transactionData.copy( // Copy original Send and override specific fields
                                signer = signer,
                                projectedFee = calculatedFeeForSubTx // Set to actual fee
                                // Amount, targetUser, assetId, etc., are from original ARC-200 Send op.
                            )
                        }
                        else -> {
                            Log.e(
                                "TransactionSignManager",
                                "Unexpected transaction type in ARC200 sub-transactions: ${decodedTx.type}"
                            )
                            postResult(Defined(AnnotatedString(R.string.an_error_occured)))
                            return null
                        }
                    }

                    newSubTxData.transactionByteArray = rawTxBytes
                    newSubTxData.calculatedFee = calculatedFeeForSubTx
                    // projectedFee is already set to calculatedFeeForSubTx to prevent resign attempts

                    processedTransactionDataList.add(newSubTxData)
                }
            } else {
                // Existing logic for non-ARC200 transactions or those already having byte array
                if (transactionData.transactionByteArray == null) {
                    // This will call the appropriate createTransaction on the specific TransactionSignData type
                    transactionData.createTransaction() ?: return null
                }
                processedTransactionDataList.add(transactionData)
            }
        }

        if (isGroupTransaction && processedTransactionDataList.size > 1) {
            createGroupedBytesArray(processedTransactionDataList)?.let { groupedBytesArray ->
                if (groupedBytesArray.length() == processedTransactionDataList.size.toLong()) {
                    for (index in processedTransactionDataList.indices) {
                        processedTransactionDataList[index].transactionByteArray = groupedBytesArray.get(index.toLong())
                        // The fee is part of the transaction bytes, assignGroupId doesn't change it.
                        // If it did, we might need to re-decode and update calculatedFee here.
                    }
                } else {
                    Log.e("TransactionSignManager", "Grouped transaction count mismatch after processing.")
                    postResult(Defined(AnnotatedString(R.string.an_error_occured)))
                    return null
                }
            } ?: return null // Error in grouping should be handled by createGroupedBytesArray or posted
        }
        return processedTransactionDataList
    }

    private fun postTxnSignResult(
        bytesArray: ByteArray?,
        transactionData: TransactionSignData?
    ) {
        if (bytesArray == null || transactionData == null) {
            postResult(Defined(AnnotatedString(stringResId = R.string.an_error_occured)))
        } else {
            postResult(TransactionManagerResult.Success(transactionData.getSignedTransactionDetail(bytesArray)))
        }
    }

    private fun postGroupTxnSignResult(
        groupedBytesArrayList: List<ByteArray>,
        transactionDataList: List<TransactionSignData>
    ) {
        val signedGroupTxnDetailList = createSignedTransactionDetailList(transactionDataList, groupedBytesArrayList)
        if (signedGroupTxnDetailList.isNotEmpty()) {
            postResult(
                TransactionManagerResult.Success(
                    SignedTransactionDetail.Group(
                        groupedBytesArrayList.flatten(),
                        signedGroupTxnDetailList
                    )
                )
            )
        } else {
            postResult(Defined(AnnotatedString(stringResId = R.string.an_error_occured)))
        }
    }

    private fun createSignedTransactionDetailList(
        transactionDataList: List<TransactionSignData>,
        signedBytesArrayList: List<ByteArray>
    ): List<SignedTransactionDetail> {
        return mutableListOf<SignedTransactionDetail>().apply {
            for (index in transactionDataList.indices) {
                val signedTxn = signedBytesArrayList[index]
                add(transactionDataList[index].getSignedTransactionDetail(signedTxn))
            }
        }
    }

    private fun createGroupedBytesArray(transactionDataList: List<TransactionSignData>): BytesArray? {
        return mutableListOf<ByteArray>().apply {
            transactionDataList.forEach {
                it.transactionByteArray?.let { transactionByteArray ->
                    add(transactionByteArray)
                } ?: return null
            }
        }.toBytesArray().assignGroupId()
    }
}

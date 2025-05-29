package com.algorand.android.models

import com.algorand.algosdk.crypto.Signature
import com.algorand.algosdk.transaction.AppBoxReference
import com.algorand.algosdk.transaction.SignedTransaction
import com.algorand.algosdk.transaction.Transaction
import com.algorand.algosdk.util.Encoder
import com.algorand.algosdk.v2.client.common.AlgodClient
import com.algorand.algosdk.v2.client.model.SimulateRequest
import com.algorand.algosdk.v2.client.model.SimulateResponse
import com.algorand.android.utils.makeApplicationCallTxnWithAbi
import com.algorand.wallet.contract.arc200.Arc200Abi
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.util.Log
import com.algorand.algosdk.crypto.Address
import com.algorand.algosdk.v2.client.model.SimulateRequestTransactionGroup
import com.algorand.algosdk.transaction.TxGroup
import com.algorand.wallet.account.core.domain.usecase.GetTransactionSigner

class Arc200TransferSimulator @Inject constructor(
    private val algodClient: AlgodClient?,
    private val getTransactionSigner: GetTransactionSigner
    // Potentially other dependencies like a general transaction parameter provider
) {

    suspend fun simulateArc200TransferWithMbrCheck(
        senderAddress: String,
        receiverAddress: String,
        arc200AppId: Long,
        amount: BigInteger,
        isMbrPaymentActuallyRequired: Boolean,
        // Allow providing suggestedParams, but fetch fresh if null or insufficient
        providedSuggestedParams: com.algorand.algosdk.v2.client.model.TransactionParametersResponse?,
        // For rekeyed accounts, pass the auth address
        senderAuthAddress: String? = null
    ): Arc200TransferSimulationResult {
        val currentSuggestedParams = if (providedSuggestedParams?.lastRound == null || providedSuggestedParams.lastRound == 0L) {
            withContext(Dispatchers.IO) {
                algodClient?.TransactionParams()?.execute()?.body()
            }
        } else {
            providedSuggestedParams
        }

        if (currentSuggestedParams == null) {
            return Arc200TransferSimulationResult(
                requiresMbrPaymentTransaction = isMbrPaymentActuallyRequired,
                mbrAmount = if (isMbrPaymentActuallyRequired) MBR_AMOUNT_PER_BOX else null,
                failureMessage = "Unable to fetch or convert suggested params from Algod.",
                appCallFee = providedSuggestedParams?.fee ?: DEFAULT_FEE,
                totalEstimatedFee = (providedSuggestedParams?.fee ?: DEFAULT_FEE) +
                    if (isMbrPaymentActuallyRequired) DEFAULT_FEE else 0L
            )
        }

        // Construct the ApplicationCallTxn for ARC-200 transfer
        val senderSdkAddress = com.algorand.algosdk.crypto.Address(senderAddress)
        val senderBoxName = byteArrayOf(0.toByte()) + senderSdkAddress.bytes
        val senderBoxReference = AppBoxReference(arc200AppId, senderBoxName)

        val receiverSdkAddress = com.algorand.algosdk.crypto.Address(receiverAddress)
        val receiverBoxName = byteArrayOf(0.toByte()) + receiverSdkAddress.bytes
        val receiverBoxReference = AppBoxReference(arc200AppId, receiverBoxName)

        val boxesListForAppCall = mutableListOf(senderBoxReference)
        if (senderAddress != receiverAddress) {
            boxesListForAppCall.add(receiverBoxReference)
        }

        val methodArgs = listOf(receiverSdkAddress, amount)

        // For rekeyed accounts, use the auth address for signing, otherwise use sender address
        val actualSignerAddress = senderAuthAddress ?: senderAddress
        val signerAddress = getTransactionSigner(actualSignerAddress)

        val appCallTxnBytes = makeApplicationCallTxnWithAbi(
            senderAddress = senderAddress,
            appId = arc200AppId,
            method = Arc200Abi.arc200TransferMethod,
            methodArgs = methodArgs,
            suggestedParams = currentSuggestedParams,
            boxes = boxesListForAppCall,
            foreignApps = listOf(arc200AppId),
            accounts = listOf(senderAddress)
        )

        if (appCallTxnBytes == null) {
            return Arc200TransferSimulationResult(
                requiresMbrPaymentTransaction = isMbrPaymentActuallyRequired,
                mbrAmount = if (isMbrPaymentActuallyRequired) MBR_AMOUNT_PER_BOX else null,
                failureMessage = "Failed to construct ARC-200 AppCall transaction.",
                appCallFee = currentSuggestedParams.fee ?: DEFAULT_FEE,
                totalEstimatedFee = (currentSuggestedParams.fee ?: DEFAULT_FEE) +
                    if (isMbrPaymentActuallyRequired) DEFAULT_FEE else 0L
            )
        }

        val decodedAppCallTxn = try {
            Encoder.decodeFromMsgPack(appCallTxnBytes, Transaction::class.java)
        } catch (e: Exception) {
            return Arc200TransferSimulationResult(
                requiresMbrPaymentTransaction = isMbrPaymentActuallyRequired,
                mbrAmount = if (isMbrPaymentActuallyRequired) MBR_AMOUNT_PER_BOX else null,
                failureMessage = "Failed to decode AppCall transaction: ${e.message}",
                appCallFee = currentSuggestedParams.fee ?: DEFAULT_FEE,
                totalEstimatedFee = (currentSuggestedParams.fee ?: DEFAULT_FEE) +
                    if (isMbrPaymentActuallyRequired) DEFAULT_FEE else 0L
            )
        }
        val appCallActualFee = decodedAppCallTxn.fee?.toLong() ?: currentSuggestedParams.fee ?: DEFAULT_FEE

        // For simulation, we need a dummy signature and a dummy txID
        val dummySignatureBytes = ByteArray(64) { 0.toByte() }
        val dummySignature = Signature(dummySignatureBytes)

        val transactionsToSimulate = mutableListOf<SignedTransaction>()
        var estimatedTotalFee = appCallActualFee

        if (isMbrPaymentActuallyRequired) {
            val appAddress = com.algorand.algosdk.crypto.Address.forApplication(arc200AppId)
            val mbrPaymentTxn = Transaction.PaymentTransactionBuilder()
                .sender(senderSdkAddress)
                .receiver(appAddress)
                .amount(MBR_AMOUNT_PER_BOX)
                .suggestedParams(currentSuggestedParams)
                .build()
            estimatedTotalFee += (mbrPaymentTxn.fee?.toLong() ?: currentSuggestedParams.fee ?: DEFAULT_FEE)

            // Grouping logic for simulation using Transaction objects directly
            // Pass transactions directly as varargs to avoid spread operator warning
            TxGroup.assignGroupID(mbrPaymentTxn, decodedAppCallTxn) // Assigns group ID in place

            // mbrPaymentTxn and decodedAppCallTxn now have their .group field populated by assignGroupID

            // Use the (now group-assigned) mbrPaymentTxn directly
            val signedMbrTxnForSim = SignedTransaction(mbrPaymentTxn, dummySignature, mbrPaymentTxn.txID())
            transactionsToSimulate.add(signedMbrTxnForSim)

            // Use the (now group-assigned) decodedAppCallTxn directly
            val signedAppCallTxnForSim = SignedTransaction(decodedAppCallTxn, dummySignature, decodedAppCallTxn.txID())
            transactionsToSimulate.add(signedAppCallTxnForSim)
        } else {
            val signedAppCallTxnForSim = SignedTransaction(decodedAppCallTxn, dummySignature, decodedAppCallTxn.txID())
            transactionsToSimulate.add(signedAppCallTxnForSim)
        }

        val simulateRequest = SimulateRequest().apply {
            txnGroups = listOf(SimulateRequestTransactionGroup().apply { txns = transactionsToSimulate })
            txnGroups.forEach { group ->
                group.txns.forEach { txn ->
                    txn.authAddr = Address(signerAddress.address)
                }
            }
            allowUnnamedResources = true
            allowEmptySignatures = true
        }

        val simulationExecutionResult: com.algorand.algosdk.v2.client.common.Response<SimulateResponse>? = try {
            if (algodClient == null) throw IllegalStateException("AlgodClient is null")
            withContext(Dispatchers.IO) {
                algodClient.SimulateTransaction().request(simulateRequest).execute()
            }
        } catch (e: Exception) {
            return Arc200TransferSimulationResult(
                requiresMbrPaymentTransaction = isMbrPaymentActuallyRequired,
                mbrAmount = if (isMbrPaymentActuallyRequired) MBR_AMOUNT_PER_BOX else null,
                failureMessage = "Exception during simulation: ${e.message}",
                simulatedSingleTransactionBytes = appCallTxnBytes, // Still useful to return the app call
                appCallFee = appCallActualFee,
                totalEstimatedFee = estimatedTotalFee
            )
        }

        val rawBody = simulationExecutionResult?.body()?.toString()

        val typedResponseBody: SimulateResponse? = simulationExecutionResult?.body()
        val firstGroupResult = typedResponseBody?.txnGroups?.firstOrNull()
        val simulationJsonString = typedResponseBody?.let { response ->
            try {
                Encoder.encodeToJson(response)
            } catch (e: Exception) {
                Log.e("Arc200Simulator", "Failed to serialize simulation response to JSON", e)
                null
            }
        }

        val failureMsgFromSimulation = if (simulationExecutionResult?.isSuccessful != true) {
            simulationExecutionResult?.message() ?: "HTTP request for simulation failed."
        } else {
            firstGroupResult?.failureMessage // This could be blank if successful
        }

        if (!failureMsgFromSimulation.isNullOrBlank()) {
            return Arc200TransferSimulationResult(
                requiresMbrPaymentTransaction = isMbrPaymentActuallyRequired,
                mbrAmount = if (isMbrPaymentActuallyRequired) MBR_AMOUNT_PER_BOX else null,
                failureMessage = failureMsgFromSimulation,
                simulationResponse = simulationJsonString, // Return even on failure
                simulatedSingleTransactionBytes = appCallTxnBytes,
                appCallFee = appCallActualFee,
                totalEstimatedFee = estimatedTotalFee,
                logs = emptyList()
            )
        }

        return Arc200TransferSimulationResult(
            requiresMbrPaymentTransaction = isMbrPaymentActuallyRequired,
            mbrAmount = if (isMbrPaymentActuallyRequired) MBR_AMOUNT_PER_BOX else null,
            simulationResponse = simulationJsonString,
            simulatedSingleTransactionBytes = appCallTxnBytes,
            appCallFee = appCallActualFee,
            totalEstimatedFee = estimatedTotalFee,
            logs = emptyList()
        )
    }

    companion object {
        const val MBR_AMOUNT_PER_BOX = 28500L // microAlgos - Ensure this is Long
        const val DEFAULT_FEE = 1000L // microAlgos
    }
}

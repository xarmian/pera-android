package com.algorand.android.models

import com.algorand.algosdk.crypto.Signature
import com.algorand.algosdk.transaction.AppBoxReference
import com.algorand.algosdk.transaction.SignedTransaction
import com.algorand.algosdk.transaction.Transaction
import com.algorand.algosdk.util.Encoder
import com.algorand.algosdk.v2.client.common.AlgodClient
import com.algorand.algosdk.v2.client.model.SimulateRequest
import com.algorand.algosdk.v2.client.model.SimulateResponse
import com.algorand.algosdk.v2.client.algod.SimulateTransaction
import com.algorand.android.utils.makeApplicationCallTxnWithAbi
import com.algorand.wallet.contract.arc200.Arc200Abi
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

import android.util.Log

class Arc200TransferSimulator @Inject constructor(
    private val algodClient: AlgodClient?
    // Potentially other dependencies like a general transaction parameter provider
) {

    suspend fun simulateArc200TransferWithMbrCheck(
        senderAddress: String,
        receiverAddress: String,
        arc200AppId: Long,
        amount: BigInteger,
        suggestedParams: com.algorand.algosdk.v2.client.model.TransactionParametersResponse?
    ): Arc200TransferSimulationResult {
        Log.d("VOI_MBR_DEBUG", "[simulateArc200TransferWithMbrCheck] START sender=$senderAddress receiver=$receiverAddress appId=$arc200AppId amount=$amount")

        // Fetch fresh SuggestedParams from Algod
        val paramsResponse = withContext(Dispatchers.IO) {
            algodClient?.TransactionParams()?.execute()
        }
        val paramsResponseBody = paramsResponse?.body()
        val freshSuggestedParams = paramsResponseBody
        if (freshSuggestedParams == null) {
            Log.d("VOI_MBR_DEBUG", "[simulateArc200TransferWithMbrCheck] Returning result: requiresMbrPaymentTransaction=false, mbrAmount=null (params unavailable)")
            return Arc200TransferSimulationResult(
                requiresMbrPaymentTransaction = false,
                failureMessage = "Unable to fetch or convert suggested params from Algod.",
                appCallFee = suggestedParams?.fee ?: 0L,
                totalEstimatedFee = suggestedParams?.fee ?: 0L
            )
        }

        // Step 1: Simulate ApplicationCallTxn alone
        val senderSdkAddress = com.algorand.algosdk.crypto.Address(senderAddress)
        val senderBoxName = byteArrayOf(0.toByte()) + senderSdkAddress.bytes
        val senderBoxReference = AppBoxReference(arc200AppId, senderBoxName)

        val receiverSdkAddress = com.algorand.algosdk.crypto.Address(receiverAddress)
        val receiverBoxName = byteArrayOf(0.toByte()) + receiverSdkAddress.bytes
        val receiverBoxReference = AppBoxReference(arc200AppId, receiverBoxName)

        val boxesList = mutableListOf(senderBoxReference)
        if (senderAddress != receiverAddress) {
            boxesList.add(receiverBoxReference)
        }

        val methodArgs = listOf(receiverSdkAddress, amount)

        val appCallTxnBytesStep1 = makeApplicationCallTxnWithAbi(
            senderAddress = senderAddress,
            appId = arc200AppId,
            method = Arc200Abi.arc200TransferMethod,
            methodArgs = methodArgs,
            suggestedParams = freshSuggestedParams,
            boxes = boxesList,
            foreignApps = listOf(arc200AppId),
            accounts = listOf(senderAddress)
        )

        if (appCallTxnBytesStep1 == null) {
            Log.d("VOI_MBR_DEBUG", "[simulateArc200TransferWithMbrCheck] Returning result: requiresMbrPaymentTransaction=false, mbrAmount=null (failed to construct Step 1)")
            return Arc200TransferSimulationResult(
                requiresMbrPaymentTransaction = false,
                failureMessage = "Failed to construct Step 1 AppCall transaction.",
                appCallFee = suggestedParams?.fee ?: 0L,
                totalEstimatedFee = suggestedParams?.fee ?: 0L
            )
        }

        val decodedTxnStep1 = try {
            Encoder.decodeFromMsgPack(appCallTxnBytesStep1, Transaction::class.java)
        } catch (e: Exception) {
            Log.d("VOI_MBR_DEBUG", "[simulateArc200TransferWithMbrCheck] Returning result: requiresMbrPaymentTransaction=false, mbrAmount=null (failed to decode Step 1)")
            return Arc200TransferSimulationResult(
                requiresMbrPaymentTransaction = false,
                failureMessage = "Failed to decode Step 1 AppCall transaction: ${e.message}",
                appCallFee = suggestedParams?.fee ?: 0L,
                totalEstimatedFee = suggestedParams?.fee ?: 0L
            )
        }
        // For simulation, we need a dummy signature and a dummy txID since the SDK requires both
        // Creating a dummy signature with 64 bytes of zeros
        val dummySignatureBytes = ByteArray(64) { 0.toByte() }
        val dummySignature = Signature(dummySignatureBytes)
        // Creating a dummy txID (just a placeholder string, not a real txID)
        val dummyTxID = "SIMULATIONDUMMYTXID000000000000000000000000000000"

        val signedTxnForSimStep1 = SignedTransaction(decodedTxnStep1, dummySignature, dummyTxID)

        val simulateRequestStep1 = SimulateRequest()
        val transactionGroupStep1 = com.algorand.algosdk.v2.client.model.SimulateRequestTransactionGroup()
        transactionGroupStep1.txns = listOf(signedTxnForSimStep1)
        simulateRequestStep1.txnGroups = listOf(transactionGroupStep1)
        simulateRequestStep1.allowUnnamedResources = true
        simulateRequestStep1.allowEmptySignatures = true

        val simulationExecutionResultStep1: com.algorand.algosdk.v2.client.common.Response<SimulateResponse>? = try {
            if (algodClient == null) {
                throw IllegalStateException("AlgodClient is null")
            }
            withContext(Dispatchers.IO) {
                val requestForSimulate: SimulateRequest = simulateRequestStep1
                val simulateTransactionBuilder: SimulateTransaction = algodClient.SimulateTransaction()
                val configuredBuilder: SimulateTransaction = simulateTransactionBuilder.request(requestForSimulate)
                configuredBuilder.execute()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d("VOI_MBR_DEBUG", "[simulateArc200TransferWithMbrCheck] Returning result: requiresMbrPaymentTransaction=false, mbrAmount=null (exception during Step 1 simulation: ${e.message})")
            return Arc200TransferSimulationResult(
                requiresMbrPaymentTransaction = false,
                failureMessage = "Exception during Step 1 simulation: ${e.message}\nStackTrace: ${e.stackTraceToString()}",
                appCallFee = decodedTxnStep1.fee?.toLong() ?: suggestedParams?.fee?.toLong() ?: 0L,
                totalEstimatedFee = decodedTxnStep1.fee?.toLong() ?: suggestedParams?.fee?.toLong() ?: 0L
            )
        }

        // Print the raw response body (even if null)
        val rawBody = simulationExecutionResultStep1?.body()?.toString()
        Log.d("VOI_MBR_DEBUG", "[simulateArc200TransferWithMbrCheck] Step 1 simulation raw response: $rawBody")

        val typedResponseBody: SimulateResponse? = simulationExecutionResultStep1?.body()
        val firstGroupResultStep1 = typedResponseBody?.txnGroups?.firstOrNull()

        val requiresMbrPaymentTransaction = false
        val step1AppCallActualFee = decodedTxnStep1.fee?.toLong() ?: suggestedParams?.fee?.toLong() ?: 0L

        val step1ErrorMessage: String? = firstGroupResultStep1?.failureMessage

        val step1Logs: List<String> = emptyList()

        // Check failureMessage from the first transaction group if HTTP call was successful
        if (simulationExecutionResultStep1?.isSuccessful == true && firstGroupResultStep1?.failureMessage.isNullOrBlank()) {
            // Step 1 simulation successful - check MBR requirement
            Log.d("VOI_MBR_DEBUG", "[simulateArc200TransferWithMbrCheck] MBR required: $requiresMbrPaymentTransaction")
            // Convert simulation response to JSON string
            val simulationResponse = typedResponseBody?.let { response ->
                try {
                    Encoder.encodeToJson(response)
                } catch (e: Exception) {
                    Log.e("Arc200Simulator", "Failed to serialize simulation response", e)
                    null
                }
            }

            return Arc200TransferSimulationResult(
                requiresMbrPaymentTransaction = requiresMbrPaymentTransaction,
                mbrAmount = if (requiresMbrPaymentTransaction) MBR_AMOUNT_PER_BOX else null,
                simulationResponse = simulationResponse,
                simulatedSingleTransactionBytes = appCallTxnBytesStep1,
                appCallFee = step1AppCallActualFee,
                totalEstimatedFee = if (requiresMbrPaymentTransaction) step1AppCallActualFee + MBR_AMOUNT_PER_BOX else step1AppCallActualFee,
                logs = step1Logs
            )
        } else {
            // Step 1 simulation failed. Detect if it's due to a missing box for MBR.
            var isMbrRequiredDueToMissingBox = false
            // Determine the actual failure message based on HTTP success and simulation content
            val actualFailureMessage = if (simulationExecutionResultStep1?.isSuccessful != true) {
                simulationExecutionResultStep1?.message() ?: "HTTP request for simulation failed."
            } else {
                firstGroupResultStep1?.failureMessage
            }

            if (actualFailureMessage?.contains("box not found", ignoreCase = true) == true) {
                isMbrRequiredDueToMissingBox = true
            } else if (simulationExecutionResultStep1!!.isSuccessful) {
                // If HTTP was successful and group message wasn't 'box not found', check trace errors
                // Check step1ErrorMessage for "box not found"
                val traceFoundBoxNotFound = step1ErrorMessage?.contains("box not found", ignoreCase = true) == true
                if (traceFoundBoxNotFound) {
                    isMbrRequiredDueToMissingBox = true
                }
            }

            if (isMbrRequiredDueToMissingBox) {
                Log.d("VOI_MBR_DEBUG", "[simulateArc200TransferWithMbrCheck] MBR required for receiver, proceeding to Step 2 simulation.")
                // MBR is required for the receiver's box
                // Step 2: simulate MBR payment + AppCall as a group
                // Build payment txn (sender -> app address, 28500 microAlgos)
                val appAddress = com.algorand.algosdk.crypto.Address.forApplication(arc200AppId)
                val mbrPaymentTxn = Transaction.PaymentTransactionBuilder()
                    .sender(com.algorand.algosdk.crypto.Address(senderAddress))
                    .receiver(appAddress)
                    .amount(MBR_AMOUNT_PER_BOX)
                    .suggestedParams(paramsResponseBody)
                    .build()

                val dummySignatureBytes2 = ByteArray(64) { 0.toByte() }
                val dummySignature2 = Signature(dummySignatureBytes2)
                val dummyTxID2 = "SIMULATIONDUMMYTXID000000000000000000000000000001"
                val signedMbrTxn = SignedTransaction(mbrPaymentTxn, dummySignature2, dummyTxID2)

                // Group payment + app call
                val simulateRequestStep2 = SimulateRequest()
                val transactionGroupStep2 = com.algorand.algosdk.v2.client.model.SimulateRequestTransactionGroup()
                transactionGroupStep2.txns = listOf(signedMbrTxn, signedTxnForSimStep1)
                simulateRequestStep2.txnGroups = listOf(transactionGroupStep2)
                simulateRequestStep2.allowUnnamedResources = true
                simulateRequestStep2.allowEmptySignatures = true

                val simulationExecutionResultStep2: com.algorand.algosdk.v2.client.common.Response<SimulateResponse>? = try {
                    if (algodClient == null) {
                        throw IllegalStateException("AlgodClient is null")
                    }
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val requestForSimulate = simulateRequestStep2
                        val simulateTransactionBuilder = algodClient.SimulateTransaction()
                        val configuredBuilder = simulateTransactionBuilder.request(requestForSimulate)
                        configuredBuilder.execute()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.d("VOI_MBR_DEBUG", "[simulateArc200TransferWithMbrCheck] Returning result: requiresMbrPaymentTransaction=true, mbrAmount=$MBR_AMOUNT_PER_BOX (exception during Step 2 simulation: ${e.message})")

                    // In case of exception, we don't have a valid simulation response
                    val simulationResponse = null

                    return Arc200TransferSimulationResult(
                        requiresMbrPaymentTransaction = true,
                        mbrAmount = MBR_AMOUNT_PER_BOX,
                        failureMessage = "Exception during Step 2 simulation: ${e.message}\nStackTrace: ${e.stackTraceToString()}",
                        simulationResponse = simulationResponse,
                        simulatedSingleTransactionBytes = appCallTxnBytesStep1,
                        appCallFee = step1AppCallActualFee,
                        totalEstimatedFee = step1AppCallActualFee + MBR_AMOUNT_PER_BOX,
                        logs = step1Logs
                    )
                }

                val typedResponseBodyStep2: SimulateResponse? = simulationExecutionResultStep2?.body()
                val firstGroupResultStep2 = typedResponseBodyStep2?.txnGroups?.firstOrNull()
                val firstTxnResultInGroupStep2 = firstGroupResultStep2?.txnResults?.getOrNull(1) // index 1 is the AppCall
                val step2AppCallActualFee = decodedTxnStep1.fee?.toLong() ?: suggestedParams?.fee?.toLong() ?: 0L
                val step2Logs: List<String> = emptyList()
                val step2ErrorMessage: String? = firstGroupResultStep2?.failureMessage

                if (simulationExecutionResultStep2?.isSuccessful == true && firstGroupResultStep2?.failureMessage.isNullOrBlank()) {
                    // Step 2 simulation successful
                    Log.d("VOI_MBR_DEBUG", "[simulateArc200TransferWithMbrCheck] Returning result: requiresMbrPaymentTransaction=true, mbrAmount=$MBR_AMOUNT_PER_BOX")

                    // Convert simulation response to JSON string
                    val simulationResponse = typedResponseBodyStep2?.let { response ->
                        try {
                            com.algorand.algosdk.util.Encoder.encodeToJson(response)
                        } catch (e: Exception) {
                            Log.e("Arc200Simulator", "Failed to serialize simulation response", e)
                            null
                        }
                    }

                    return Arc200TransferSimulationResult(
                        requiresMbrPaymentTransaction = true,
                        mbrAmount = MBR_AMOUNT_PER_BOX,
                        simulationResponse = simulationResponse,
                        simulatedSingleTransactionBytes = appCallTxnBytesStep1,
                        appCallFee = step2AppCallActualFee,
                        totalEstimatedFee = step2AppCallActualFee + MBR_AMOUNT_PER_BOX,
                        logs = step2Logs
                    )
                } else {
                    val actualFailureMessage2 = if (simulationExecutionResultStep2?.isSuccessful != true) {
                        simulationExecutionResultStep2?.message() ?: "HTTP request for simulation failed."
                    } else {
                        firstGroupResultStep2?.failureMessage
                    }
                    Log.d("VOI_MBR_DEBUG", "[simulateArc200TransferWithMbrCheck] Returning result: requiresMbrPaymentTransaction=true, mbrAmount=$MBR_AMOUNT_PER_BOX (Step 2 simulation failed: $actualFailureMessage2)")

                    // Convert simulation response to JSON string even for failures
                    val simulationResponse = simulationExecutionResultStep2?.body()?.let { response ->
                        try {
                            com.algorand.algosdk.util.Encoder.encodeToJson(response)
                        } catch (e: Exception) {
                            Log.e("Arc200Simulator", "Failed to serialize simulation response", e)
                            null
                        }
                    }

                    return Arc200TransferSimulationResult(
                        requiresMbrPaymentTransaction = true,
                        mbrAmount = MBR_AMOUNT_PER_BOX,
                        failureMessage = actualFailureMessage2 ?: step2ErrorMessage ?: "Step 2 simulation failed: Unknown reason.",
                        simulationResponse = simulationResponse,
                        simulatedSingleTransactionBytes = appCallTxnBytesStep1,
                        appCallFee = step2AppCallActualFee,
                        totalEstimatedFee = step2AppCallActualFee + MBR_AMOUNT_PER_BOX,
                        logs = step2Logs
                    )
                }
            } else {
                // Step 1 failed for other reasons
                val failureMessage = actualFailureMessage ?: "Step 1 simulation failed: Unknown reason."
                Log.d("VOI_MBR_DEBUG", "[simulateArc200TransferWithMbrCheck] Returning result: requiresMbrPaymentTransaction=false, mbrAmount=null (Step 1 simulation failed, not MBR)")

                // Convert simulation response to JSON string even for failures
                val simulationResponse = simulationExecutionResultStep1?.body()?.let { response ->
                    try {
                        Encoder.encodeToJson(response)
                    } catch (e: Exception) {
                        Log.e("Arc200Simulator", "Failed to serialize simulation response", e)
                        null
                    }
                }

                return Arc200TransferSimulationResult(
                    requiresMbrPaymentTransaction = false,
                    failureMessage = "Step 1 Failed: $failureMessage",
                    simulationResponse = simulationResponse,
                    appCallFee = step1AppCallActualFee,
                    totalEstimatedFee = step1AppCallActualFee,
                    logs = step1Logs
                )
            }
        }
    }

    companion object {
        const val MBR_AMOUNT_PER_BOX = 28500 // microAlgos
    }
}

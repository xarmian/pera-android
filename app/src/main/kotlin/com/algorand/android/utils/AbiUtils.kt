package com.algorand.android.utils

import com.algorand.algosdk.abi.Method
import com.algorand.algosdk.crypto.Address
import com.algorand.algosdk.transaction.Transaction
import com.algorand.algosdk.util.Encoder
import android.util.Log
import com.algorand.algosdk.transaction.AppBoxReference
import com.algorand.algosdk.v2.client.model.TransactionParametersResponse
import java.security.MessageDigest
import java.math.BigInteger

const val SIZE = 32

// Helper extension function to convert ByteArray to Hex String for logging
fun ByteArray.toHexLogString(): String = joinToString("") { "%02x".format(it) }

/**
 * Creates an Application Call transaction for an ABI method.
 *
 * @param senderAddress The Algorand address of the sender.
 * @param appId The ID of the application to call.
 * @param method The ABI method definition.
 * @param methodArgs The list of arguments for the ABI method.
 * @param suggestedParams The suggested transaction parameters.
 * @param note Optional note for the transaction.
 * @param accounts Optional list of accounts for the transaction.
 * @param foreignApps Optional list of foreign apps for the transaction.
 * @param foreignAssets Optional list of foreign assets for the transaction.
 * @param rekeyTo Optional address to rekey to.
 * @param lease Optional lease value.
 * @param boxes Optional list of box references for the transaction.
 * @return A byte array representing the created transaction.
 */
fun makeApplicationCallTxnWithAbi(
    senderAddress: String,
    appId: Long,
    method: Method,
    methodArgs: List<Any>,
    suggestedParams: TransactionParametersResponse?,
    note: ByteArray? = null,
    accounts: List<String>? = null,
    foreignApps: List<Long>? = null,
    foreignAssets: List<Long>? = null,
    rekeyTo: String? = null,
    lease: ByteArray? = null,
    boxes: List<AppBoxReference>? = null
): ByteArray {
    // Generate method signature
    val methodSignature = "${method.name}(${method.args.joinToString(",") { it.type }})${method.returns.type}"
    Log.d("makeApplicationCallTxnWithAbi", "Method signature for selector: $methodSignature")

    // Create application arguments
    val appArgs = mutableListOf<ByteArray>()
    val selector = getMethodSelector(methodSignature)
    appArgs.add(selector)
    Log.d("makeApplicationCallTxnWithAbi", "Method selector (bytes): ${selector.toHexLogString()}")

    // Add method arguments with proper encoding for each type
    method.args.forEachIndexed { index, arg ->
        val argValue = methodArgs.getOrNull(index)
            ?: throw IllegalArgumentException("Missing argument at index $index for method ${method.name}")

        Log.d("makeApplicationCallTxnWithAbi", "Processing arg ${arg.name} (${arg.type}) with value: $argValue")

        val encodedArg = when {
            arg.type.equals("address", ignoreCase = true) && argValue is Address -> {
                argValue.bytes
            }
            arg.type.equals("address", ignoreCase = true) && argValue is String -> {
                Address(argValue).bytes
            }
            arg.type.startsWith("uint", ignoreCase = true) -> {
                val bigIntValue = if (argValue is BigInteger) argValue else BigInteger.valueOf((argValue as Number).toLong())
                val bytes = bigIntValue.toByteArray()
                // Ensure the byte array is properly formatted for uint types, especially uint256
                if (arg.type.equals("uint256", ignoreCase = true)) {
                    if (bytes.size > SIZE) {
                        // If the BigInteger's byte array starts with a 0x00 for sign bit and is 33 bytes,
                        // and we need 32 bytes, it's okay to take the last 32 bytes.
                        if (bytes.size == SIZE + 1 && bytes[0].toInt() == 0) {
                            bytes.copyOfRange(1, bytes.size)
                        } else {
                            throw IllegalArgumentException("BigInteger $bigIntValue (hex: ${bytes.toHexLogString()}) too large for uint256 (max $SIZE bytes)")
                        }
                    } else {
                        // Pad to 32 bytes if needed
                        val paddedBytes = ByteArray(SIZE)
                        val srcOffset = if (bytes[0].toInt() == 0 && bytes.size > 1) 1 else 0 // Handle leading zero from toByteArray if it's just for sign
                        val lengthToCopy = bytes.size - srcOffset
                        val destOffset = SIZE - lengthToCopy
                        System.arraycopy(bytes, srcOffset, paddedBytes, destOffset, lengthToCopy)
                        paddedBytes
                    }
                } else {
                    // For other uintN, we generally don't pad unless the ABI type implies a fixed size not handled here.
                    // The SDK's ABIEncoder usually handles various uintN sizes by taking the minimal byte representation.
                    // However, if it's a uintN from a BigInteger, toByteArray() can include a sign byte.
                    if (bytes.size > 1 && bytes[0].toInt() == 0 && !bigIntValue.equals(BigInteger.ZERO)) {
                        bytes.copyOfRange(1, bytes.size)
                    } else {
                        bytes
                    }
                }
            }
            arg.type.equals("string", ignoreCase = true) && argValue is String -> {
                argValue.toByteArray(Charsets.UTF_8)
            }
            argValue is ByteArray -> {
                argValue
            }
            else -> {
                throw IllegalArgumentException(
                    "Unsupported argument type for ${arg.type}: ${'$'}{argValue::class.java.simpleName}"
                )
            }
        }
        appArgs.add(encodedArg)
        Log.d("makeApplicationCallTxnWithAbi", "Arg ${arg.name} (${arg.type}) encoded (bytes): ${encodedArg.toHexLogString()}")
    }

    // Convert collections to appropriate types
    val appArgsList = appArgs.toList()
    val accountsList = accounts?.map { Address(it) } ?: emptyList()
    val foreignAppsList = foreignApps ?: emptyList()
    val foreignAssetsList = foreignAssets ?: emptyList()
    val rekeyToAddress = rekeyTo?.let { Address(it) }

    // Create application call transaction
    val builder = Transaction.ApplicationCallTransactionBuilder()
    builder.sender(Address(senderAddress))
    // Directly set transaction parameters from suggestedParams
    builder.fee(suggestedParams?.fee)
    builder.firstValid(suggestedParams?.lastRound ?: 0L)
    builder.lastValid((suggestedParams?.lastRound ?: 0L) + 1000L) // Standard 1000 round validity window
    builder.genesisID(suggestedParams?.genesisId)
    builder.genesisHash(suggestedParams?.genesisHash) // Assuming suggestedParams.genesisHash is ByteArray

    builder.applicationId(appId)
    builder.args(appArgsList)
    builder.accounts(accountsList)
    builder.foreignApps(foreignAppsList)
    builder.foreignAssets(foreignAssetsList)
    builder.note(note)
    builder.rekey(rekeyToAddress)
    builder.lease(lease)
    builder.boxReferences(boxes ?: emptyList())

    val transaction = builder.build()

    // Set the OnCompletion type to NoOp (it's set to NoOp by default in the builder)

    // Encode transaction to message pack format
    return Encoder.encodeToMsgPack(transaction)
}

private fun getMethodSelector(methodSignature: String): ByteArray {
    val digest = MessageDigest.getInstance("SHA-512/256")
    val hash = digest.digest(methodSignature.toByteArray(Charsets.UTF_8))
    return hash.copyOfRange(0, 4)
}

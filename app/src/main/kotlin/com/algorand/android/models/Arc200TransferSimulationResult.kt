package com.algorand.android.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents the result of an ARC-200 transfer simulation, including MBR check.
 *
 * @param requiresMbrPaymentTransaction True if the grouped MBR payment transaction is needed and its simulation was successful.
 * @param mbrAmount The MBR amount required (e.g., 28500 microAlgos). Only populated if `requiresMbrPaymentTransaction` is true.
 * @param failureMessage Populated with an error message if any simulation step fails. Null if all relevant simulations are successful.
 *                     This can capture general simulation errors or specific errors if MBR-related failure occurs in Step 2.
 * @param simulatedSingleTransactionBytes The raw bytes of the simulated single ApplicationCallTxn.
 *                                       Populated if Step 1 simulation is successful and MBR is not needed.
 * @param simulatedGroupedTransactionBytes The raw bytes of the simulated grouped transaction (PaymentTxn + ApplicationCallTxn).
 *                                        Populated if Step 2 simulation is successful.
 * @param appCallFee Estimated fee for the ARC-200 app call, derived from simulation.
 * @param mbrPaymentFee Estimated fee for the MBR payment, derived from simulation, if applicable.
 * @param totalEstimatedFee Total fee for the transaction(s) to be sent, derived from simulation.
 * @param logs Decoded logs from the relevant simulation for debugging/inspection.
 */
@Parcelize
data class Arc200TransferSimulationResult(
    val requiresMbrPaymentTransaction: Boolean,
    val mbrAmount: Long? = null,
    val failureMessage: String? = null,
    val simulationResponse: String? = null,
    val simulatedSingleTransactionBytes: ByteArray? = null,
    val simulatedGroupedTransactionBytes: ByteArray? = null,
    val appCallFee: Long,
    val mbrPaymentFee: Long? = null,
    val totalEstimatedFee: Long,
    val logs: List<String>? = null
) : Parcelable {
    // Custom equals and hashCode for ByteArray properties if needed, 
    // though Parcelize should handle them reasonably well for basic cases.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Arc200TransferSimulationResult

        if (requiresMbrPaymentTransaction != other.requiresMbrPaymentTransaction) return false
        if (mbrAmount != other.mbrAmount) return false
        if (failureMessage != other.failureMessage) return false
        if (simulatedSingleTransactionBytes != null) {
            if (other.simulatedSingleTransactionBytes == null) return false
            if (!simulatedSingleTransactionBytes.contentEquals(other.simulatedSingleTransactionBytes)) return false
        } else if (other.simulatedSingleTransactionBytes != null) return false
        if (simulatedGroupedTransactionBytes != null) {
            if (other.simulatedGroupedTransactionBytes == null) return false
            if (!simulatedGroupedTransactionBytes.contentEquals(other.simulatedGroupedTransactionBytes)) return false
        } else if (other.simulatedGroupedTransactionBytes != null) return false
        if (appCallFee != other.appCallFee) return false
        if (mbrPaymentFee != other.mbrPaymentFee) return false
        if (totalEstimatedFee != other.totalEstimatedFee) return false
        if (logs != other.logs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = requiresMbrPaymentTransaction.hashCode()
        result = 31 * result + (mbrAmount?.hashCode() ?: 0)
        result = 31 * result + (failureMessage?.hashCode() ?: 0)
        result = 31 * result + (simulatedSingleTransactionBytes?.contentHashCode() ?: 0)
        result = 31 * result + (simulatedGroupedTransactionBytes?.contentHashCode() ?: 0)
        result = 31 * result + appCallFee.hashCode()
        result = 31 * result + (mbrPaymentFee?.hashCode() ?: 0)
        result = 31 * result + totalEstimatedFee.hashCode()
        result = 31 * result + (logs?.hashCode() ?: 0)
        return result
    }
}

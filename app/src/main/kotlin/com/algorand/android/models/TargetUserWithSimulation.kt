package com.algorand.android.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TargetUserWithSimulation(
    val targetUser: TargetUser,
    val simulationResponse: String?, // JSON string of SimulateResponse
    val isMbrPaymentSimulated: Boolean? = null, // True if MBR payment was part of the simulation
    val mbrAmount: java.math.BigInteger? = null // The actual MBR amount if simulated
) : Parcelable

package com.algorand.wallet.contract.arc200

import com.algorand.algosdk.abi.Method

object Arc200Abi {

    // Using const String for type names as suggested by constructor errors for Method.Arg/Returns
    private const val ARC200_TRANSFER_ARG_TO_TYPE_STRING = "address"
    private const val ARC200_TRANSFER_ARG_VALUE_TYPE_STRING = "uint256"
    private const val ARC200_TRANSFER_RETURN_TYPE_STRING = "bool"

    /**
     * Represents the ARC-200 `arc200_transfer` ABI method.
     * Method signature: `arc200_transfer(address,uint256)bool`
     */
    val arc200TransferMethod = Method(
        "arc200_transfer",
        "Transfers ARC-200 tokens to a specified address.",
        listOf(
            Method.Arg(
                "to",
                ARC200_TRANSFER_ARG_TO_TYPE_STRING,
                "The destination address of the transfer"
            ),
            Method.Arg(
                "value",
                ARC200_TRANSFER_ARG_VALUE_TYPE_STRING,
                "The amount of tokens to transfer"
            )
        ),
        Method.Returns(
            ARC200_TRANSFER_RETURN_TYPE_STRING,
            "A boolean indicating if the transfer was successful."
        )
    )

    // Other ARC-200 ABI methods can be added here as needed, for example:
    // val arc200BalanceOfMethod = Method(...)
    // val arc200ApproveMethod = Method(...)
}

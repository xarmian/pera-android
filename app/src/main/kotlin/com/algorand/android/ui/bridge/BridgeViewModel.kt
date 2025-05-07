package com.algorand.android.ui.bridge

import androidx.lifecycle.viewModelScope
import com.algorand.android.core.BaseViewModel
import com.algorand.android.utils.isValidAddress
import com.algorand.wallet.account.core.domain.usecase.GetAccountsDetailsFlow
import com.algorand.wallet.account.detail.domain.model.AccountDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.floor
import java.net.URLEncoder

// TODO: Define these in a more appropriate location if they are reused e.g. constants file
const val AVOI_ASSET_ID = 2320775407L
const val BRIDGE_FEE_PERCENTAGE = 0.001 // 0.1%
const val VOI_DECIMALS = 6 // Assuming Voi has 6 decimals like Algo
const val ARAMID_ADDRESS = "ARAMIDFJYV2TOFB5MRNZJIXBSAVZCVAUDAPFGKR5PNX4MTILGAZABBTXQQ"
const val VOI_NETWORK_ID_FOR_NOTE = 416101
const val ALGORAND_NETWORK_ID_FOR_NOTE = 416001
const val NATIVE_VOI_ASSET_ID_STRING_FOR_NOTE = "0"
const val AVOI_ASSET_ID_STRING_FOR_NOTE = "2320775407"

enum class TransactionStatus {
    IDLE,
    LOADING,
    SUCCESS, // Might hold transaction data later
    ERROR
}

enum class OptInStatus {
    CHECKING,
    OPTED_IN,
    NOT_OPTED_IN,
    INVALID_ADDRESS,
    UNKNOWN_ERROR
}

@HiltViewModel
class BridgeViewModel @Inject constructor(
    private val getAccountsDetailsFlow: GetAccountsDetailsFlow
    // TODO: Inject GetVoiAccountBalanceUseCase
    // TODO: Inject CheckAvoiOptInStatusUseCase
    // TODO: Inject GetAvoiBalanceUseCase
) : BaseViewModel() {

    sealed class BridgeUiEvent {
        data class ShowToast(val message: String) : BridgeUiEvent()
        data class ShowAvoiOptInHelp(val deepLinkUrl: String) : BridgeUiEvent()
        data class ShowAlgoToVoiDeepLink(val deepLinkUrl: String) : BridgeUiEvent()
        object InvalidToAlgorandAddressError : BridgeUiEvent()
        object InvalidFromAlgorandAddressError : BridgeUiEvent()
    }

    private val _bridgeUiEventFlow = MutableSharedFlow<BridgeUiEvent>()
    val bridgeUiEventFlow: SharedFlow<BridgeUiEvent> = _bridgeUiEventFlow

    private val _isVoiToAlgoFlow = MutableStateFlow(true) // Default to Voi to Algo
    val isVoiToAlgoFlow: StateFlow<Boolean> = _isVoiToAlgoFlow

    private val _fromAccountAddressFlow = MutableStateFlow<String?>("") // Source Algo address for Algo->Voi
    val fromAccountAddressFlow: StateFlow<String?> = _fromAccountAddressFlow

    private val _toAccountAddressFlow = MutableStateFlow<String?>("") // Dest Algo address for Voi->Algo
    val toAccountAddressFlow: StateFlow<String?> = _toAccountAddressFlow

    private val _amountInputFlow = MutableStateFlow<String?>("")
    val amountInputFlow: StateFlow<String?> = _amountInputFlow

    private val _userVoiAccountsFlow = MutableStateFlow<List<AccountDetail>>(emptyList())
    val userVoiAccountsFlow: StateFlow<List<AccountDetail>> = _userVoiAccountsFlow

    // Voi to Algorand specific states
    private val _selectedFromVoiAccountFlow = MutableStateFlow<AccountDetail?>(null)
    val selectedFromVoiAccountFlow: StateFlow<AccountDetail?> = _selectedFromVoiAccountFlow

    private val _selectedFromVoiAccountBalanceFlow = MutableStateFlow<BigInteger?>(null)
    val selectedFromVoiAccountBalanceFlow: StateFlow<BigInteger?> = _selectedFromVoiAccountBalanceFlow

    // aVOI Opt-in status for the destination Algorand address (Voi -> Algo flow)
    private val _destinationAvoiOptInStatusFlow = MutableStateFlow<OptInStatus>(OptInStatus.INVALID_ADDRESS)
    val destinationAvoiOptInStatusFlow: StateFlow<OptInStatus> = _destinationAvoiOptInStatusFlow

    // Algorand to Voi specific states
    private val _sourceAlgorandAccountAvoiBalanceFlow = MutableStateFlow<BigInteger?>(null)
    val sourceAlgorandAccountAvoiBalanceFlow: StateFlow<BigInteger?> = _sourceAlgorandAccountAvoiBalanceFlow

    private val _selectedToVoiAccountFlow = MutableStateFlow<AccountDetail?>(null)
    val selectedToVoiAccountFlow: StateFlow<AccountDetail?> = _selectedToVoiAccountFlow

    // Calculated fee and net amount for display
    private val _calculatedFeeAmountFlow = MutableStateFlow<BigInteger?>(null)
    val calculatedFeeAmountFlow: StateFlow<BigInteger?> = _calculatedFeeAmountFlow

    private val _netAmountToReceiveFlow = MutableStateFlow<BigInteger?>(null)
    val netAmountToReceiveFlow: StateFlow<BigInteger?> = _netAmountToReceiveFlow

    // Transaction construction state
    private val _transactionStatusFlow = MutableStateFlow<TransactionStatus>(TransactionStatus.IDLE)
    val transactionStatusFlow: StateFlow<TransactionStatus> = _transactionStatusFlow

    // TODO: Add other StateFlows for Voi accounts list, balances, opt-in status, transaction state, UI events, etc.

    init {
        fetchUserAccounts()
    }

    private fun fetchUserAccounts() {
        getAccountsDetailsFlow.invoke()
            .onEach { accounts ->
                // TODO: Filter for Voi network accounts if possible, or clarify if all accounts are shown
                // For now, assume all local accounts are potential Voi accounts for selection
                _userVoiAccountsFlow.value = accounts
            }
            .launchIn(viewModelScope)
    }

    fun onFromVoiAccountSelected(account: AccountDetail) {
        _selectedFromVoiAccountFlow.value = account
        fetchVoiAccountBalance(account.address)
    }

    private fun fetchVoiAccountBalance(accountAddress: String) {
        viewModelScope.launch {
            _selectedFromVoiAccountBalanceFlow.value = null // Clear previous balance
            // TODO: val result = getVoiAccountBalanceUseCase(accountAddress, voiNodeAlgodApi)
            // TODO: handle result and update _selectedFromVoiAccountBalanceFlow.value
        }
    }

    fun onBridgeModeSelected(isVoiToAlgo: Boolean) {
        if (_isVoiToAlgoFlow.value == isVoiToAlgo) return // No change

        _isVoiToAlgoFlow.value = isVoiToAlgo
        _amountInputFlow.value = "" // Clear amount on any mode switch

        if (isVoiToAlgo) {
            // Switched to Voi -> Algo mode
            _fromAccountAddressFlow.value = "" // Clear the Algo source address input
            _destinationAvoiOptInStatusFlow.value = OptInStatus.INVALID_ADDRESS // Reset opt-in status
            _calculatedFeeAmountFlow.value = null // Clear fee
            _netAmountToReceiveFlow.value = null // Clear net amount
            _selectedToVoiAccountFlow.value = null // Clear selected To Voi account for Algo->Voi
        } else {
            // Switched to Algo -> Voi mode
            _toAccountAddressFlow.value = "" // Clear the Algo destination address input
            _selectedFromVoiAccountFlow.value = null // Clear selected Voi account
            _selectedFromVoiAccountBalanceFlow.value = null // Clear its balance
            _destinationAvoiOptInStatusFlow.value = OptInStatus.INVALID_ADDRESS // Reset opt-in status for safety
            _calculatedFeeAmountFlow.value = null // Clear fee
            _netAmountToReceiveFlow.value = null // Clear net amount
            _sourceAlgorandAccountAvoiBalanceFlow.value = null // Clear source aVOI balance
        }
        // TODO: Reset other relevant states like selected accounts, balances, opt-in status etc.
    }

    fun onFromAlgorandAddressChanged(address: String) {
        _fromAccountAddressFlow.value = address
        viewModelScope.launch {
            if (address.isEmpty()) {
                _sourceAlgorandAccountAvoiBalanceFlow.value = null
                // Potentially clear error if any was shown for this input
                return@launch
            }
            if (!address.isValidAddress()) {
                _bridgeUiEventFlow.emit(BridgeUiEvent.InvalidFromAlgorandAddressError)
                _sourceAlgorandAccountAvoiBalanceFlow.value = null
            } else {
                // Only fetch if in Algo -> Voi mode
                if (!_isVoiToAlgoFlow.value) {
                    _sourceAlgorandAccountAvoiBalanceFlow.value = null // Clear previous balance
                    // TODO: val balance = getAvoiBalanceUseCase(address, algorandNodeAlgodApi)
                    // TODO: _sourceAlgorandAccountAvoiBalanceFlow.value = balance
                    // For now, simulate finding some balance
                    _sourceAlgorandAccountAvoiBalanceFlow.value = BigInteger("123456789") // Example: 123.456789 aVOI
                }
            }
        }
    }

    fun onToAlgorandAddressChanged(address: String) {
        _toAccountAddressFlow.value = address
        viewModelScope.launch {
            if (address.isEmpty()) {
                _destinationAvoiOptInStatusFlow.value = OptInStatus.INVALID_ADDRESS
                return@launch
            }
            if (!address.isValidAddress()) {
                _bridgeUiEventFlow.emit(BridgeUiEvent.InvalidToAlgorandAddressError)
                _destinationAvoiOptInStatusFlow.value = OptInStatus.INVALID_ADDRESS
            } else {
                _destinationAvoiOptInStatusFlow.value = OptInStatus.CHECKING
                // TODO: val isOptedIn = checkAvoiOptInStatusUseCase(address, algorandNodeAlgodApi)
                // TODO: when (isOptedIn) {
                // TODO:    is Success -> _destinationAvoiOptInStatusFlow.value = if (it.data) OptInStatus.OPTED_IN else OptInStatus.NOT_OPTED_IN
                // TODO:    is Error -> _destinationAvoiOptInStatusFlow.value = OptInStatus.UNKNOWN_ERROR
                // TODO: }
                // For now, let's simulate a NOT_OPTED_IN case for testing help button
                _destinationAvoiOptInStatusFlow.value = OptInStatus.NOT_OPTED_IN
            }
        }
    }

    fun onToVoiAccountSelected(account: AccountDetail) {
        _selectedToVoiAccountFlow.value = account
        // No balance fetching needed for destination Voi account
    }

    fun onAmountChanged(amountString: String) {
        _amountInputFlow.value = amountString
        if (amountString.isBlank()) {
            _calculatedFeeAmountFlow.value = null
            _netAmountToReceiveFlow.value = null
            // TODO: Clear any amount validation errors
            return
        }

        val amountDouble = amountString.toDoubleOrNull()
        if (amountDouble == null || amountDouble <= 0) {
            _calculatedFeeAmountFlow.value = null
            _netAmountToReceiveFlow.value = null
            // TODO: Show invalid amount error (e.g. must be positive number)
            return
        }

        // Convert display amount to atomic units
        val userEnteredAtomicAmount = BigInteger.valueOf((amountDouble * (10.0.pow(VOI_DECIMALS))).toLong())

        // Calculate fee: floor(USER_ENTERED_ATOMIC_AMOUNT * FEE_PERCENTAGE)
        val feeCalculated = floor(userEnteredAtomicAmount.toDouble() * BRIDGE_FEE_PERCENTAGE).toLong()
        val feeAmountAtomic = BigInteger.valueOf(feeCalculated)

        _calculatedFeeAmountFlow.value = feeAmountAtomic

        // Calculate net amount
        val finalNetAmountAtomic = userEnteredAtomicAmount.subtract(feeAmountAtomic)
        _netAmountToReceiveFlow.value = finalNetAmountAtomic

        // TODO: Validate userEnteredAtomicAmount against selected Voi account balance OR source aVOI balance based on mode
        val currentBalance = if (_isVoiToAlgoFlow.value) {
            _selectedFromVoiAccountBalanceFlow.value
        } else {
            _sourceAlgorandAccountAvoiBalanceFlow.value
        }

        if (currentBalance != null && userEnteredAtomicAmount > currentBalance) {
            // TODO: Show insufficient balance error (make message specific to Voi or aVOI)
            val assetName = if (_isVoiToAlgoFlow.value) "Voi" else "aVOI"
            viewModelScope.launch {
                 _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Insufficient $assetName balance."))
            }
        } else {
            // TODO: Clear insufficient balance error if it was shown
        }
    }

    fun onSelectFromVoiAccountClicked() {
        viewModelScope.launch {
            // TODO: Navigate to Voi account selection screen/bottom sheet
            // _uiEventFlow.emit(NavigationEvent.ToVoiAccountSelection)
        }
    }

    fun onSelectToVoiAccountClicked() {
        viewModelScope.launch {
            // TODO: Navigate to Voi account selection screen/bottom sheet for destination
            // _uiEventFlow.emit(NavigationEvent.ToDestinationVoiAccountSelection)
        }
    }

    fun onAvoiOptInHelpClicked() {
        viewModelScope.launch {
            val destinationAddress = _toAccountAddressFlow.value
            if (destinationAddress != null && destinationAddress.isValidAddress()) {
                if (_destinationAvoiOptInStatusFlow.value == OptInStatus.NOT_OPTED_IN) {
                    val deepLink = "perawallet://$destinationAddress?amount=0&asset=$AVOI_ASSET_ID"
                    _bridgeUiEventFlow.emit(BridgeUiEvent.ShowAvoiOptInHelp(deepLink))
                } else {
                    // Handle cases where help is clicked but not needed, or show a message
                }
            } else {
                _bridgeUiEventFlow.emit(BridgeUiEvent.InvalidToAlgorandAddressError)
            }
        }
    }

    fun onBridgeRequested() {
        viewModelScope.launch {
            _transactionStatusFlow.value = TransactionStatus.LOADING

            val amountInputString = _amountInputFlow.value
            val amountAtomic = amountInputString?.let { str ->
                str.toDoubleOrNull()?.let {
                    BigInteger.valueOf((it * (10.0.pow(VOI_DECIMALS))).toLong())
                }
            }
            val feeAtomic = _calculatedFeeAmountFlow.value

            if (amountAtomic == null || amountAtomic <= BigInteger.ZERO || feeAtomic == null) {
                _transactionStatusFlow.value = TransactionStatus.ERROR
                _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Invalid amount or fee calculation."))
                return@launch
            }

            if (_isVoiToAlgoFlow.value) {
                // Voi to Algorand Flow
                val fromVoiAccount = _selectedFromVoiAccountFlow.value
                val toAlgorandAddress = _toAccountAddressFlow.value

                if (fromVoiAccount == null) {
                    _transactionStatusFlow.value = TransactionStatus.ERROR
                    _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Please select a source Voi account."))
                    return@launch
                }
                if (toAlgorandAddress.isNullOrBlank() || !toAlgorandAddress.isValidAddress()) {
                    _transactionStatusFlow.value = TransactionStatus.ERROR
                    _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Invalid destination Algorand address."))
                    return@launch
                }

                val voiBalance = _selectedFromVoiAccountBalanceFlow.value
                if (voiBalance == null || amountAtomic > voiBalance) {
                    _transactionStatusFlow.value = TransactionStatus.ERROR
                    _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Insufficient Voi balance."))
                    return@launch
                }

                if (_destinationAvoiOptInStatusFlow.value != OptInStatus.OPTED_IN) {
                    _transactionStatusFlow.value = TransactionStatus.ERROR
                    _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Destination account not opted into aVOI."))
                    return@launch
                }

                // TODO: Actual Voi->Algo transaction construction
                // Note format: aramid-transfer/v1:j{"destinationNetwork":416001,"destinationAddress":"TO_ALGORAND_ADDRESS","destinationToken":"2320775407","feeAmount":FEE_AMOUNT_CALCULATED,"destinationAmount":FINAL_NET_AMOUNT,"note":"aramid","sourceAmount":FINAL_NET_AMOUNT}
                // Placeholder for actual transaction construction and signing flow
                kotlinx.coroutines.delay(1000) // Simulate network
                _transactionStatusFlow.value = TransactionStatus.SUCCESS
                _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Voi-to-Algorand Bridge TXN initiated (simulated)."))

            } else {
                // Algorand to Voi Flow
                val fromAlgorandAddress = _fromAccountAddressFlow.value
                val toVoiAccount = _selectedToVoiAccountFlow.value

                if (fromAlgorandAddress.isNullOrBlank() || !fromAlgorandAddress.isValidAddress()) {
                    _transactionStatusFlow.value = TransactionStatus.ERROR
                    _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Invalid source Algorand address."))
                    return@launch
                }
                if (toVoiAccount == null) {
                    _transactionStatusFlow.value = TransactionStatus.ERROR
                    _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Please select a destination Voi account."))
                    return@launch
                }

                val avoiBalance = _sourceAlgorandAccountAvoiBalanceFlow.value
                if (avoiBalance == null || amountAtomic > avoiBalance) {
                    _transactionStatusFlow.value = TransactionStatus.ERROR
                    _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Insufficient aVOI balance."))
                    return@launch
                }

                val finalNetAmountAtomic = amountAtomic.subtract(feeAtomic)
                // Format: aramid-transfer/v1:j{"destinationNetwork":416101,"destinationAddress":"TO_VOI_ADDRESS","destinationToken":"0","feeAmount":FEE_AMOUNT_CALCULATED,"destinationAmount":FINAL_NET_AMOUNT,"note":"aramid","sourceAmount":FINAL_NET_AMOUNT}
                val noteJsonString = buildString {
                    append("{")
                    append("\"destinationNetwork\":$VOI_NETWORK_ID_FOR_NOTE,")
                    append("\"destinationAddress\":\"${toVoiAccount.address}\",")
                    append("\"destinationToken\":\"$NATIVE_VOI_ASSET_ID_STRING_FOR_NOTE\",")
                    append("\"feeAmount\":${feeAtomic.toString()},")
                    append("\"destinationAmount\":${finalNetAmountAtomic.toString()},")
                    append("\"note\":\"aramid\",")
                    append("\"sourceAmount\":${finalNetAmountAtomic.toString()}}")
                }
                val aramidNote = "aramid-transfer/v1:j$noteJsonString"
                val encodedNote = URLEncoder.encode(aramidNote, "UTF-8")

                // Deep Link Format: perawallet://<ARAMID_ADDRESS>?amount=<ATOMIC_aVOI_AMOUNT>&asset=<aVOI_ASSET_ID>&xnote=<URL_ENCODED_ARAMID_NOTE>
                val deepLink = "perawallet://$ARAMID_ADDRESS?amount=${amountAtomic.toString()}&asset=$AVOI_ASSET_ID&xnote=$encodedNote"

                _bridgeUiEventFlow.emit(BridgeUiEvent.ShowAlgoToVoiDeepLink(deepLink))
                _transactionStatusFlow.value = TransactionStatus.SUCCESS // Indicating deep link generation was successful
                _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Algorand-to-Voi Bridge: Deep link ready."))
            }
        }
    }
} 
} 
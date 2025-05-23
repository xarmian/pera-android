package com.algorand.android.ui.bridge

import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.algorand.android.core.BaseViewModel
import com.algorand.android.core.transaction.TransactionSignManager
import com.algorand.android.domain.bridge.usecase.GenerateAVoiToAramidDeepLinkUseCase
import com.algorand.android.domain.bridge.usecase.GetAVoiBalanceUseCase
import com.algorand.android.domain.bridge.usecase.GetVoiAccountBalanceUseCase
import com.algorand.android.domain.bridge.usecase.IsAccountOptedInToAVoiUseCase
import com.algorand.android.domain.bridge.usecase.PrepareVoiToAramidTransactionUseCase
import com.algorand.android.domain.bridge.usecase.SendSignedVoiTransactionUseCase
import com.algorand.android.models.BaseAccountSelectionListItem
import com.algorand.android.models.Result
import com.algorand.android.models.SignedTransactionDetail
import com.algorand.android.models.TransactionManagerResult
import com.algorand.android.models.TransactionSignData
import com.algorand.android.models.TargetUser
import com.algorand.android.models.User
import com.algorand.android.modules.accountcore.ui.accountselection.usecase.GetAccountSelectionAccountItemsUseCase
import com.algorand.android.modules.accountcore.ui.accountselection.usecase.GetAccountSelectionAccountsWhichCanSignTransactionUseCase
import com.algorand.android.modules.bridge.domain.model.BridgeConstants
import com.algorand.android.utils.Event
import com.algorand.android.utils.isValidAddress
import com.algorand.android.utils.toShortenedAddress
import com.algorand.wallet.account.core.domain.model.TransactionSigner
import com.algorand.wallet.account.core.domain.usecase.GetAccountsDetailsFlow
import com.algorand.wallet.account.core.domain.usecase.GetTransactionSigner
import com.algorand.wallet.account.core.domain.usecase.GetAccountMinBalance
import com.algorand.wallet.account.detail.domain.model.AccountDetail
import com.algorand.wallet.asset.domain.util.AssetConstants
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
import kotlin.math.pow
import com.algorand.android.usecase.AccountAddressUseCase
import com.algorand.android.models.BaseAccountAddress
import com.algorand.android.utils.VOI_DECIMALS
import com.algorand.wallet.asset.domain.model.AssetType

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
    private val getAccountsDetailsFlow: GetAccountsDetailsFlow,
    private val getVoiAccountBalanceUseCase: GetVoiAccountBalanceUseCase,
    private val isAccountOptedInToAVoiUseCase: IsAccountOptedInToAVoiUseCase,
    private val getAVoiBalanceUseCase: GetAVoiBalanceUseCase,
    private val generateAVoiToAramidDeepLinkUseCase: GenerateAVoiToAramidDeepLinkUseCase,
    private val prepareVoiToAramidTransactionUseCase: PrepareVoiToAramidTransactionUseCase,
    private val sendSignedVoiTransactionUseCase: SendSignedVoiTransactionUseCase,
    private val getTransactionSigner: GetTransactionSigner,
    private val getAccountMinBalanceUseCase: GetAccountMinBalance,
    private val getAccountSelectionAccountsWhichCanSignTransactionUseCase: GetAccountSelectionAccountsWhichCanSignTransactionUseCase,
    private val getAccountSelectionAccountItemsUseCase: GetAccountSelectionAccountItemsUseCase,
    private val accountAddressUseCase: AccountAddressUseCase,
    val transactionSignManager: TransactionSignManager
) : BaseViewModel() {

    sealed class BridgeUiEvent {
        data class ShowToast(val message: String) : BridgeUiEvent()
        data class ShowAvoiOptInHelp(val deepLinkUrl: String) : BridgeUiEvent()
        data class ShowAlgoToVoiDeepLink(val deepLinkUrl: String) : BridgeUiEvent()
        object InvalidToAlgorandAddressError : BridgeUiEvent()
        object ClearToAlgorandAddressError : BridgeUiEvent()
        object InvalidFromAlgorandAddressError : BridgeUiEvent()
        object ClearFromAlgorandAddressError : BridgeUiEvent()
        object InvalidToVoiAddressError : BridgeUiEvent()
        object ClearToVoiAddressError : BridgeUiEvent()
        data class RequestVoiToAlgoSigning(val transactionSignData: TransactionSignData.Send) : BridgeUiEvent()
        data class ShowAlgoToVoiConfirmation(
            val amountToSendAtomic: Long,
            val amountToReceiveAtomic: Long,
            val destinationAddress: String,
            val peraWalletDeeplink: String
        ) : BridgeUiEvent()
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

    // Account Selection Flows
    private val _signableVoiAccountsFlow = MutableStateFlow<List<BaseAccountSelectionListItem.BaseAccountItem>>(emptyList())
    val signableVoiAccountsFlow: StateFlow<List<BaseAccountSelectionListItem.BaseAccountItem>> = _signableVoiAccountsFlow

    private val _allLocalVoiAccountsFlow = MutableStateFlow<List<BaseAccountSelectionListItem.BaseAccountItem>>(emptyList())
    val allLocalVoiAccountsFlow: StateFlow<List<BaseAccountSelectionListItem.BaseAccountItem>> = _allLocalVoiAccountsFlow

    private val _allLocalAlgorandAccountsFlow = MutableStateFlow<List<BaseAccountSelectionListItem.BaseAccountItem>>(emptyList())
    val allLocalAlgorandAccountsFlow: StateFlow<List<BaseAccountSelectionListItem.BaseAccountItem>> = _allLocalAlgorandAccountsFlow

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

    // Expose LiveData for observing TransactionManagerResult from the Fragment
    val transactionManagerResultLiveData: LiveData<Event<TransactionManagerResult>?>
        get() = transactionSignManager.transactionManagerResultLiveData

    init {
        fetchUserAccounts()
        // Note: transactionSignManager.setup(lifecycle) should be called by the Fragment
        // with its viewLifecycleOwner.lifecycle
    }

    private fun fetchUserAccounts() {
        viewModelScope.launch {
            // For "From Voi Account" - local Voi accounts that can sign Voi native token transactions.
            // Voi is the native token, so AssetConstants.ALGO_ID (0L) is used.
            val signableAccounts = getAccountSelectionAccountsWhichCanSignTransactionUseCase(
                showHoldings = true, // Include balance information
                showFailedAccounts = false, // Exclude accounts that couldn't be loaded
                assetId = AssetConstants.ALGO_ID // Use 0L for the native Voi token
                // No excludedAccountTypes needed as we want standard accounts primarily.
            )
            _signableVoiAccountsFlow.value = signableAccounts

            // For "To Voi Account", "To Algorand Account", and "From Algorand Account" selectors.
            // Fetches all local accounts. Since all local accounts are Voi accounts as per clarification.
            // For Algorand fields, the user can select a local Voi address for convenience,
            // and it will be treated as an Algorand address by the bridge logic.
            val allLocalAccounts = getAccountSelectionAccountItemsUseCase(
                showHoldings = true, // Include balance information
                showFailedAccounts = true // Include even accounts that might have loading issues
            )

            // All local accounts are Voi accounts.
            _allLocalVoiAccountsFlow.value = allLocalAccounts
            // For Algorand address fields, users can pick from their local Voi list for convenience.
            _allLocalAlgorandAccountsFlow.value = allLocalAccounts

            // The original _userVoiAccountsFlow provides AccountDetail objects.
            // This might still be useful for getting full details after selection,
            // or if other parts of the ViewModel rely on it.
            // Consider refactoring the nested launchIn if it causes issues.
            getAccountsDetailsFlow.invoke()
                .onEach { accounts ->
                    // These are AccountDetail objects for all local (Voi) accounts.
                    _userVoiAccountsFlow.value = accounts
                }
                .launchIn(viewModelScope)
        }
    }

    fun onFromVoiAccountSelected(account: AccountDetail) {
        _selectedFromVoiAccountFlow.value = account
        fetchVoiAccountBalance(account.address)
    }

    private fun fetchVoiAccountBalance(accountAddress: String) {
        viewModelScope.launch {
            _selectedFromVoiAccountBalanceFlow.value = null // Clear previous balance
            getVoiAccountBalanceUseCase(accountAddress)
                .onEach { balance ->
                    _selectedFromVoiAccountBalanceFlow.value = balance
                }
                .launchIn(viewModelScope)
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
            val isAlgoToVoi = !_isVoiToAlgoFlow.value // Current mode is Algo to Voi

            if (address.isEmpty()) {
                _sourceAlgorandAccountAvoiBalanceFlow.value = null
                if (isAlgoToVoi) {
                    // Field is optional in Algo-to-Voi, clear any existing error if user empties it
                    _bridgeUiEventFlow.emit(BridgeUiEvent.ClearFromAlgorandAddressError)
                }
                // If Voi-to-Algo, an empty address is an error, but we'll let bridge/submit validation handle it
                // rather than showing an error immediately on typing an empty string.
                return@launch
            }

            // Address is not empty, proceed with validation
            if (!address.isValidAddress()) {
                _bridgeUiEventFlow.emit(BridgeUiEvent.InvalidFromAlgorandAddressError)
                _sourceAlgorandAccountAvoiBalanceFlow.value = null
            } else {
                _bridgeUiEventFlow.emit(BridgeUiEvent.ClearFromAlgorandAddressError)
                if (isAlgoToVoi) { // If Algo-to-Voi and address is valid
                    _sourceAlgorandAccountAvoiBalanceFlow.value = null // Clear previous before fetching
                    getAVoiBalanceUseCase(address)
                        .onEach { balance ->
                            _sourceAlgorandAccountAvoiBalanceFlow.value = balance
                        }
                        .launchIn(viewModelScope)
                }
            }
        }
    }

    fun onToAlgorandAddressChanged(address: String) {
        _toAccountAddressFlow.value = address
        viewModelScope.launch {
            if (address.isEmpty()) {
                _destinationAvoiOptInStatusFlow.value = OptInStatus.INVALID_ADDRESS
                _bridgeUiEventFlow.emit(BridgeUiEvent.ClearToAlgorandAddressError)
                return@launch
            }
            if (!address.isValidAddress()) {
                _bridgeUiEventFlow.emit(BridgeUiEvent.InvalidToAlgorandAddressError)
                _destinationAvoiOptInStatusFlow.value = OptInStatus.INVALID_ADDRESS
            } else {
                _bridgeUiEventFlow.emit(BridgeUiEvent.ClearToAlgorandAddressError)
                _destinationAvoiOptInStatusFlow.value = OptInStatus.CHECKING
                isAccountOptedInToAVoiUseCase(address)
                    .onEach { isOptedIn ->
                        _destinationAvoiOptInStatusFlow.value = if (isOptedIn) {
                            OptInStatus.OPTED_IN
                        } else {
                            OptInStatus.NOT_OPTED_IN
                        }
                    }
                    .launchIn(viewModelScope)
            }
        }
    }

    fun onToVoiAccountSelected(account: AccountDetail) {
        _selectedToVoiAccountFlow.value = account
        // No balance fetching needed for destination Voi account
    }

    fun onToVoiAddressManuallyChanged(address: String) {
        viewModelScope.launch {
            if (address.isBlank()) {
                _selectedToVoiAccountFlow.value = null
                _bridgeUiEventFlow.emit(BridgeUiEvent.ClearToVoiAddressError)
                return@launch
            }
            if (!address.isValidAddress()) {
                _selectedToVoiAccountFlow.value = null
                _bridgeUiEventFlow.emit(BridgeUiEvent.InvalidToVoiAddressError)
                return@launch
            }
            _bridgeUiEventFlow.emit(BridgeUiEvent.ClearToVoiAddressError)

            val verifiedAddress = accountAddressUseCase.getAccountAddress(address)
            if (verifiedAddress is BaseAccountAddress.AccountAddress) {
                _selectedToVoiAccountFlow.value = AccountDetail(
                    address = verifiedAddress.publicKey,
                    customAccountInfo = null,
                    accountRegistrationType = null,
                    accountType = null
                )
                _bridgeUiEventFlow.emit(BridgeUiEvent.ClearToVoiAddressError)
            } else {
                _selectedToVoiAccountFlow.value = null
                _bridgeUiEventFlow.emit(BridgeUiEvent.InvalidToVoiAddressError)
            }
        }
    }

    fun onAmountChanged(amount: String) {
        _amountInputFlow.value = amount
        if (amount.isEmpty()) {
            _calculatedFeeAmountFlow.value = null
            _netAmountToReceiveFlow.value = null
        } else {
            calculateFeeAndNetAmount()
        }
    }

    private fun calculateFeeAndNetAmount() {
        val amountToBridgeText = _amountInputFlow.value
        if (amountToBridgeText.isNullOrBlank()) {
            _calculatedFeeAmountFlow.value = null
            _netAmountToReceiveFlow.value = null
            return
        }
        val amountDouble = amountToBridgeText.toDoubleOrNull()
        if (amountDouble == null || amountDouble <= 0) {
            _calculatedFeeAmountFlow.value = null
            _netAmountToReceiveFlow.value = null
            return
        }
        val userEnteredAtomicAmount = BigInteger.valueOf((amountDouble * (10.0.pow(VOI_DECIMALS))).toLong())
        val feeCalculated = floor(userEnteredAtomicAmount.toDouble() * BridgeConstants.FEE_PERCENTAGE).toLong()
        val feeAmountAtomic = BigInteger.valueOf(feeCalculated)
        _calculatedFeeAmountFlow.value = feeAmountAtomic
        val finalNetAmountAtomic = userEnteredAtomicAmount.subtract(feeAmountAtomic)
        _netAmountToReceiveFlow.value = finalNetAmountAtomic

        val currentBalance = if (_isVoiToAlgoFlow.value) {
            _selectedFromVoiAccountBalanceFlow.value
        } else {
            _sourceAlgorandAccountAvoiBalanceFlow.value
        }
        if (currentBalance != null && userEnteredAtomicAmount > currentBalance) {
            val assetName = if (_isVoiToAlgoFlow.value) "Voi" else "aVOI"
            viewModelScope.launch {
                 _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Insufficient $assetName balance."))
            }
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
            if (destinationAddress.isNullOrBlank() || !destinationAddress.isValidAddress()) {
                _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Please enter a valid destination Algorand address first."))
                return@launch
            }
            val deepLinkUrl = "perawallet://$destinationAddress?amount=0&asset=${BridgeConstants.AVOI_ASSET_ID}"
            _bridgeUiEventFlow.emit(BridgeUiEvent.ShowAvoiOptInHelp(deepLinkUrl))
        }
    }

    fun onBridgeRequested() {
        viewModelScope.launch {
            _transactionStatusFlow.value = TransactionStatus.LOADING

            val amounts = parseAndValidateAmountInput() ?: return@launch
            val (grossAmountAtomic, feeAmountAtomic, netAmountAtomic) = amounts

            if (_isVoiToAlgoFlow.value) {
                handleVoiToAlgoBridging(grossAmountAtomic, feeAmountAtomic, netAmountAtomic)
            } else {
                handleAlgoToVoiBridging(grossAmountAtomic, feeAmountAtomic, netAmountAtomic)
            }
        }
    }

    private suspend fun parseAndValidateAmountInput(): Triple<BigInteger, BigInteger, BigInteger>? {
        val amountToBridgeText = _amountInputFlow.value
        val grossAmountAtomic: BigInteger
        val feeAmountAtomic: BigInteger
        val netAmountAtomic: BigInteger

        if (amountToBridgeText.isNullOrBlank()) {
            _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Amount cannot be empty."))
            _transactionStatusFlow.value = TransactionStatus.IDLE
            return null
        }
        try {
            val amountDouble = amountToBridgeText.toDouble()
            if (amountDouble <= 0) throw NumberFormatException("Amount must be positive")
            grossAmountAtomic = BigInteger.valueOf((amountDouble * (10.0.pow(VOI_DECIMALS))).toLong())
            val feeCalculated = floor(grossAmountAtomic.toDouble() * BridgeConstants.FEE_PERCENTAGE).toLong()
            feeAmountAtomic = BigInteger.valueOf(feeCalculated)
            netAmountAtomic = grossAmountAtomic.subtract(feeAmountAtomic)
            if (netAmountAtomic <= BigInteger.ZERO) {
                _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Net amount after fee must be positive."))
                _transactionStatusFlow.value = TransactionStatus.IDLE
                return null
            }
        } catch (e: NumberFormatException) {
            _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Invalid amount entered."))
            _transactionStatusFlow.value = TransactionStatus.IDLE
            return null
        }
        return Triple(grossAmountAtomic, feeAmountAtomic, netAmountAtomic)
    }

    private suspend fun handleVoiToAlgoBridging(
        grossAmountAtomic: BigInteger,
        feeAmountAtomic: BigInteger,
        netAmountAtomic: BigInteger
    ) {
        val fromVoiAccount = _selectedFromVoiAccountFlow.value
        val toAlgoAddress = _toAccountAddressFlow.value

        if (fromVoiAccount == null) {
            _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Please select a source Voi account."))
            _transactionStatusFlow.value = TransactionStatus.IDLE
            return
        }
        if (toAlgoAddress.isNullOrBlank() || !toAlgoAddress.isValidAddress()) {
            _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Invalid destination Algorand address."))
            _transactionStatusFlow.value = TransactionStatus.IDLE
            return
        }
        if (_destinationAvoiOptInStatusFlow.value != OptInStatus.OPTED_IN) {
            _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Destination account must be opted-in to aVOI."))
            _transactionStatusFlow.value = TransactionStatus.IDLE
            return
        }
        val currentVoiBalance = _selectedFromVoiAccountBalanceFlow.value
        if (currentVoiBalance == null || currentVoiBalance < grossAmountAtomic) {
            _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Insufficient Voi balance."))
            _transactionStatusFlow.value = TransactionStatus.IDLE
            return
        }

        val fromVoiAccountAddress = fromVoiAccount.address

        val unsignedTxnData = prepareVoiToAramidTransactionUseCase(
            fromVoiAccountAddress = fromVoiAccountAddress,
            destinationAlgorandAddress = toAlgoAddress,
            grossVoiAmount = grossAmountAtomic,
            feeAmount = feeAmountAtomic,
            netAmountToReceive = netAmountAtomic
        )

        if (unsignedTxnData == null) {
            _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Failed to prepare Voi->Algo transaction."))
            _transactionStatusFlow.value = TransactionStatus.ERROR
            return
        }

        val transactionSigner = getTransactionSigner.invoke(fromVoiAccountAddress)
        if (transactionSigner is TransactionSigner.SignerNotFound) {
            _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Cannot sign: Signer not found for $fromVoiAccountAddress."))
            _transactionStatusFlow.value = TransactionStatus.ERROR
            return
        }

        val senderVoiAtomicBalance = _selectedFromVoiAccountBalanceFlow.value ?: BigInteger.ZERO
        // getAccountMinBalanceUseCase.invoke() returns BigInteger, .toLong() is used as TransactionSignData.Send expects Long
        val senderMinimumBalance = getAccountMinBalanceUseCase.invoke(fromVoiAccountAddress).toLong()
        val senderAuthAddress = when (transactionSigner) {
            is TransactionSigner.SignerNotFound.AuthAccountSigningDetailsNotFound -> transactionSigner.authAddress
            else -> null // Standard signers or other SignerNotFound types don't have a separate authAddress here
        }

        val transactionSignData = TransactionSignData.Send(
            senderAccountAddress = fromVoiAccountAddress,
            senderAuthAddress = senderAuthAddress,
            signer = transactionSigner,
            amount = grossAmountAtomic,
            targetUser = TargetUser(
                publicKey = BridgeConstants.ARAMID_ADDRESS,
                contact = User(name = "Aramid Bridge", publicKey = BridgeConstants.ARAMID_ADDRESS, imageUriAsString = null),
                accountIconDrawablePreview = null
            ),
            transactionByteArray = unsignedTxnData.unsignedTxnByteArray,
            isArc59Transaction = false,
            isArc200Transaction = false,
            senderAlgoAmount = senderVoiAtomicBalance,
            minimumBalance = senderMinimumBalance,
            senderAccountName = fromVoiAccount.customAccountInfo?.customName ?: fromVoiAccount.address.toShortenedAddress(),
            assetId = AssetConstants.ALGO_ID, // For native Voi (treated as Algo on Voi network)
            assetType = AssetType.ASA,
            xnote = unsignedTxnData.noteString,
            projectedFee = feeAmountAtomic.toLong()
        )
        // ViewModel will now observe transactionManagerResultEventFlow for signing result
        // _transactionStatusFlow remains LOADING until signing result is processed by the Fragment/Activity
        // transactionSignManager.initSigningTransactions(false, transactionSignData)
        _transactionStatusFlow.value = TransactionStatus.IDLE // ViewModel preparation is done
        _bridgeUiEventFlow.emit(BridgeUiEvent.RequestVoiToAlgoSigning(transactionSignData))
    }

    private suspend fun handleAlgoToVoiBridging(
        grossAmountAtomic: BigInteger,
        feeAmountAtomic: BigInteger,
        netAmountAtomic: BigInteger
    ) {
        val fromAlgoAddress = _fromAccountAddressFlow.value // This is nullable String?
        val toVoiAccount = _selectedToVoiAccountFlow.value

        // Validate From Algorand Address ONLY if it's provided
        if (!fromAlgoAddress.isNullOrBlank() && !fromAlgoAddress.isValidAddress()) {
            _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Invalid source Algorand address provided."))
            _transactionStatusFlow.value = TransactionStatus.IDLE
            return
        }

        if (toVoiAccount == null) {
            _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Please select a destination Voi account."))
            _transactionStatusFlow.value = TransactionStatus.IDLE
            return
        }

        // Check aVOI balance ONLY if the source Algorand address is provided
        if (!fromAlgoAddress.isNullOrBlank()) {
            val currentAvoiBalance = _sourceAlgorandAccountAvoiBalanceFlow.value
            if (currentAvoiBalance == null || currentAvoiBalance < grossAmountAtomic) {
                _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Insufficient aVOI balance for the provided source account."))
                _transactionStatusFlow.value = TransactionStatus.IDLE
                return
            }
        }

        val deepLinkUrl: String
        try {
            deepLinkUrl = generateAVoiToAramidDeepLinkUseCase(
                grossAVoiAmount = grossAmountAtomic,
                feeAmount = feeAmountAtomic,
                netAmountToReceive = netAmountAtomic,
                destinationVoiAddress = toVoiAccount.address
            )
            if (deepLinkUrl.isBlank()) {
                _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Failed to generate bridge link. Please check inputs and try again."))
                _transactionStatusFlow.value = TransactionStatus.IDLE
                return
            }
        } catch (e: Exception) {
            // Consider logging exception 'e' here for more detailed debugging
            _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Error preparing bridge transaction: ${e.message ?: "Unknown error"}"))
            _transactionStatusFlow.value = TransactionStatus.IDLE
            return
        }

        _bridgeUiEventFlow.emit(
            BridgeUiEvent.ShowAlgoToVoiConfirmation(
                amountToSendAtomic = grossAmountAtomic.toLong(), // Assuming conversion is safe, otherwise check range
                amountToReceiveAtomic = netAmountAtomic.toLong(), // Assuming conversion is safe
                destinationAddress = toVoiAccount.address,
                peraWalletDeeplink = deepLinkUrl
            )
        )
        _transactionStatusFlow.value = TransactionStatus.IDLE // Reset status after preparing confirmation data
    }

    // This method will be called by the Fragment/Activity observing transactionSignManager.transactionManagerResultEventFlow
    fun processVoiToAramidSignedTransaction(signedTransactionDetail: SignedTransactionDetail) {
        viewModelScope.launch {
            // Extract the signed transaction byte array
            // This logic might need adjustment based on how single vs group transactions are wrapped
            val signedTxnByteArray = when (signedTransactionDetail) {
                is SignedTransactionDetail.Send -> signedTransactionDetail.signedTransactionData
                is SignedTransactionDetail.Group -> {
                    // If it's a group, and we expect only one, take the first. Otherwise, this needs more robust handling.
                    if (signedTransactionDetail.transactions?.size == 1) {
                        signedTransactionDetail.transactions.first().signedTransactionData
                    } else {
                        // This case should ideally not happen for this specific bridge transaction
                        _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Unexpected grouped transaction result."))
                        _transactionStatusFlow.value = TransactionStatus.ERROR
                        return@launch
                    }
                }
                else -> {
                    _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Unsupported signed transaction type."))
                    _transactionStatusFlow.value = TransactionStatus.ERROR
                    return@launch
                }
            }

            if (signedTxnByteArray == null) {
                _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Failed to get signed transaction data."))
                _transactionStatusFlow.value = TransactionStatus.ERROR
                return@launch
            }

            _transactionStatusFlow.value = TransactionStatus.LOADING // Show loading while sending
            sendSignedVoiTransactionUseCase(signedTxnByteArray).onEach { result ->
                when (result) {
                    is Result.Success -> {
                        _transactionStatusFlow.value = TransactionStatus.SUCCESS
                        _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast("Voi->Algo TXN Sent: ${result.data}"))
                        // Consider clearing inputs or navigating on success
                    }
                    is Result.Error -> {
                        _transactionStatusFlow.value = TransactionStatus.ERROR
                        val errorMessage = result.exception.message ?: "Unknown error sending transaction."
                        _bridgeUiEventFlow.emit(BridgeUiEvent.ShowToast(errorMessage))
                    }
                }
            }.launchIn(viewModelScope)
        }
    }
}

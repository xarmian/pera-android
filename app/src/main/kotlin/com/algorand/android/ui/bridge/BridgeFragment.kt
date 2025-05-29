package com.algorand.android.ui.bridge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.algorand.android.R
import com.algorand.android.core.DaggerBaseFragment
import com.algorand.android.databinding.FragmentBridgeBinding
import com.algorand.android.models.BaseAccountSelectionListItem
import com.algorand.android.models.FragmentConfiguration
import com.algorand.android.models.TransactionManagerResult
import com.algorand.android.utils.showSnackbar
import com.algorand.android.utils.viewbinding.viewBinding
import com.algorand.wallet.account.detail.domain.model.AccountDetail
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.algorand.android.utils.toShortenedAddress
import com.algorand.android.utils.ALGO_DECIMALS
import com.algorand.android.utils.browser.BRIDGE_SUPPORT_URL
import com.algorand.android.utils.browser.openUrl
import android.text.method.LinkMovementMethod
import androidx.core.content.ContextCompat

@AndroidEntryPoint
class BridgeFragment : DaggerBaseFragment(R.layout.fragment_bridge), BridgeAccountSelectionBottomSheet.Listener {

    private val bridgeViewModel: BridgeViewModel by viewModels()

    private val binding by viewBinding(FragmentBridgeBinding::bind)

    // To keep track of which account selection initiated the bottom sheet
    private enum class AccountSelectionTarget {
        FROM_VOI, TO_VOI, FROM_ALGO_LOCAL, TO_ALGO_LOCAL
    }
    private var currentAccountSelectionTarget: AccountSelectionTarget? = null

    override val fragmentConfiguration = FragmentConfiguration(
        toolbarConfiguration = null, // TODO: Configure toolbar if needed
        isBottomBarNeeded = true
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bridgeViewModel.transactionSignManager.setup(viewLifecycleOwner.lifecycle)
        setupTabs()
        initUiListeners()
        initObservers()
    }

    private fun setupTabs() {
        binding.bridgeModeTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    val isVoiToAlgo = it.position == 0
                    bridgeViewModel.onBridgeModeSelected(isVoiToAlgo)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) { /* Nothing to do */ }
            override fun onTabReselected(tab: TabLayout.Tab?) { /* Nothing to do */ }
        })
    }

    private fun initUiListeners() {
        binding.bridgeButton.setOnClickListener {
            bridgeViewModel.onBridgeRequested()
        }

        // Voi to Algo Mode Listeners
        binding.bridgeFromVoiSection.selectFromVoiAccountButton.setOnClickListener {
            currentAccountSelectionTarget = AccountSelectionTarget.FROM_VOI
            val accounts = bridgeViewModel.signableVoiAccountsFlow.value
            if (accounts.isNotEmpty()) {
                BridgeAccountSelectionBottomSheet.show(
                    childFragmentManager,
                    getString(R.string.select_from_voi_account),
                    accounts,
                    this
                )
            } else {
                Toast.makeText(context, R.string.no_signable_accounts_found, Toast.LENGTH_SHORT).show()
            }
        }
        binding.bridgeToAlgoSection.toAlgorandAddressEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { /* NA */ }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { /* NA */ }
            override fun afterTextChanged(s: Editable?) {
                bridgeViewModel.onToAlgorandAddressChanged(s.toString().trim())
            }
        })
        binding.bridgeToAlgoSection.selectToLocalForAlgoButton.setOnClickListener {
            currentAccountSelectionTarget = AccountSelectionTarget.TO_ALGO_LOCAL
            val accounts = bridgeViewModel.allLocalAlgorandAccountsFlow.value
             if (accounts.isNotEmpty()) {
                BridgeAccountSelectionBottomSheet.show(
                    childFragmentManager,
                    getString(R.string.select_to_algorand_account),
                    accounts,
                    this
                )
            } else {
                Toast.makeText(context, R.string.no_local_accounts_found, Toast.LENGTH_SHORT).show()
            }
        }
        binding.bridgeToAlgoSection.avoiOptInHelpButton.setOnClickListener {
            bridgeViewModel.onAvoiOptInHelpClicked()
        }

        // Algo to Voi Mode Listeners
        binding.bridgeFromAlgoSection.fromAlgorandAddressEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { /* NA */ }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { /* NA */ }
            override fun afterTextChanged(s: Editable?) {
                bridgeViewModel.onFromAlgorandAddressChanged(s.toString().trim())
            }
        })
        binding.bridgeFromAlgoSection.selectFromLocalForAlgoButton.setOnClickListener {
            currentAccountSelectionTarget = AccountSelectionTarget.FROM_ALGO_LOCAL
            val accounts = bridgeViewModel.allLocalAlgorandAccountsFlow.value
            if (accounts.isNotEmpty()) {
                BridgeAccountSelectionBottomSheet.show(
                    childFragmentManager,
                    getString(R.string.select_from_algorand_account),
                    accounts,
                    this
                )
            } else {
                Toast.makeText(context, R.string.no_local_accounts_found, Toast.LENGTH_SHORT).show()
            }
        }
        binding.bridgeToVoiSection.toVoiAddressEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { /* NA */ }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { /* NA */ }
            override fun afterTextChanged(s: Editable?) {
                bridgeViewModel.onToVoiAddressManuallyChanged(s.toString().trim())
                // If user types, assume this is the primary selection method for this field for now
                // binding.bridgeToVoiSection.toVoiAccountAddressTextView.visibility = View.GONE
            }
        })
        binding.bridgeToVoiSection.selectToVoiAccountButton.setOnClickListener {
            currentAccountSelectionTarget = AccountSelectionTarget.TO_VOI
            val accounts = bridgeViewModel.allLocalVoiAccountsFlow.value
            if (accounts.isNotEmpty()) {
                BridgeAccountSelectionBottomSheet.show(
                    childFragmentManager,
                    getString(R.string.select_to_voi_account),
                    accounts,
                    this
                )
            } else {
                Toast.makeText(context, R.string.no_local_accounts_found, Toast.LENGTH_SHORT).show()
            }
        }

        // Set LinkMovementMethod for the support link TextView
        // binding.bridgeSupportLinkTextView.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        setupSupportLinkText()

        // Aramid logo click listener
        binding.aramidLogoImageView.setOnClickListener {
            context?.openUrl("https://aramid.finance")
        }

        // Amount Listener
        binding.bridgeAmountSection.bridgeAmountEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { /* NA */ }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { /* NA */ }
            override fun afterTextChanged(s: Editable?) {
                bridgeViewModel.onAmountChanged(s.toString().trim())
            }
        })
    }

    private fun setupSupportLinkText() {
        binding.bridgeSupportLinkTextView.apply {
            val linkTextColor = ContextCompat.getColor(context, R.color.link_primary)
            val text = getString(R.string.bridge_need_help)
            val spannableString = android.text.SpannableString(text)
            val learnMoreStart = text.indexOf("Learn more")
            val learnMoreEnd = learnMoreStart + "Learn more".length
            val clickableSpan = object : android.text.style.ClickableSpan() {
                override fun onClick(widget: android.view.View) {
                    context?.openUrl(BRIDGE_SUPPORT_URL)
                }
                override fun updateDrawState(ds: android.text.TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = linkTextColor
                    ds.isUnderlineText = true
                }
            }
            spannableString.setSpan(clickableSpan, learnMoreStart, learnMoreEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            this.text = spannableString
            highlightColor = ContextCompat.getColor(context, R.color.transparent)
            isClickable = true
            isFocusable = true
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun updateUiForBridgeMode(isVoiToAlgo: Boolean) {
        binding.bridgeFromVoiSection.root.isVisible = isVoiToAlgo
        binding.bridgeToAlgoSection.root.isVisible = isVoiToAlgo

        binding.bridgeFromAlgoSection.root.isVisible = !isVoiToAlgo
        binding.bridgeToVoiSection.root.isVisible = !isVoiToAlgo

        val amountSuffix = if (isVoiToAlgo) getString(R.string.voi_ticker) else getString(R.string.avoi_ticker)
        binding.bridgeAmountSection.bridgeAmountTextInputLayout.suffixText = amountSuffix

        binding.bridgeToAlgoSection.toAlgorandAddressEditText.setText("")
        binding.bridgeFromAlgoSection.fromAlgorandAddressEditText.setText("")
        binding.bridgeAmountSection.bridgeAmountEditText.setText("")
        // TODO: Clear selected Voi account display if any
    }

    private fun initObservers() {
        observeScreenModeAndInputs()
        observeAccountStates()
        observeCalculatedAmounts()
        observeTransactionPipeline()
    }

    private fun observeScreenModeAndInputs() {
        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.isVoiToAlgoFlow.collectLatest {
                updateUiForBridgeMode(it)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.fromAccountAddressFlow.collectLatest { address ->
                if (binding.bridgeFromAlgoSection.fromAlgorandAddressEditText.text.toString() != address) {
                    binding.bridgeFromAlgoSection.fromAlgorandAddressEditText.setText(address)
                    binding.bridgeFromAlgoSection.fromAlgorandAddressTextInputLayout.error = null
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.toAccountAddressFlow.collectLatest { address ->
                if (binding.bridgeToAlgoSection.toAlgorandAddressEditText.text.toString() != address) {
                    binding.bridgeToAlgoSection.toAlgorandAddressEditText.setText(address)
                    binding.bridgeToAlgoSection.toAlgorandAddressTextInputLayout.error = null
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.amountInputFlow.collectLatest { amount ->
                if (binding.bridgeAmountSection.bridgeAmountEditText.text.toString() != amount) {
                    binding.bridgeAmountSection.bridgeAmountEditText.setText(amount)
                }
            }
        }
    }

    private fun observeAccountStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.selectedFromVoiAccountFlow.collectLatest { account ->
                val accountIdentifier = account?.customAccountInfo?.customName ?: account?.address?.toShortenedAddress()
                binding.bridgeFromVoiSection.selectFromVoiAccountButton.text = accountIdentifier ?: getString(R.string.select_account)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.selectedFromVoiAccountBalanceFlow.collectLatest { balance ->
                val balanceText = balance?.let {
                    val voi = it.toBigDecimal().movePointLeft(ALGO_DECIMALS)
                    "Balance: ${voi.stripTrailingZeros().toPlainString()} VOI"
                } ?: "Balance: -"
                binding.bridgeFromVoiSection.fromVoiAccountBalanceTextView.text = balanceText
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.destinationAvoiOptInStatusFlow.collectLatest { status ->
                when (status) {
                    OptInStatus.CHECKING -> {
                        binding.bridgeToAlgoSection.avoiOptInStatusTextView.text = "Checking opt-in..."
                        binding.bridgeToAlgoSection.avoiOptInHelpButton.isVisible = false
                    }
                    OptInStatus.OPTED_IN -> {
                        binding.bridgeToAlgoSection.avoiOptInStatusTextView.text = "aVOI Opted-In"
                        binding.bridgeToAlgoSection.avoiOptInHelpButton.isVisible = false
                    }
                    OptInStatus.NOT_OPTED_IN -> {
                        binding.bridgeToAlgoSection.avoiOptInStatusTextView.text = "aVOI Not Opted-In"
                        binding.bridgeToAlgoSection.avoiOptInHelpButton.isVisible = true
                    }
                    OptInStatus.INVALID_ADDRESS -> {
                        binding.bridgeToAlgoSection.avoiOptInStatusTextView.text = "Enter valid Algo address"
                        binding.bridgeToAlgoSection.avoiOptInHelpButton.isVisible = false
                    }
                    OptInStatus.UNKNOWN_ERROR -> {
                        binding.bridgeToAlgoSection.avoiOptInStatusTextView.text = "Error checking opt-in"
                        binding.bridgeToAlgoSection.avoiOptInHelpButton.isVisible = false
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.sourceAlgorandAccountAvoiBalanceFlow.collectLatest { balance ->
                val balanceText = balance?.let {
                    val voi = it.toBigDecimal().movePointLeft(ALGO_DECIMALS)
                    "aVOI Balance: ${voi.stripTrailingZeros().toPlainString()} aVOI"
                } ?: "aVOI Balance: -"
                binding.bridgeFromAlgoSection.fromAlgoAccountBalanceTextView.text = balanceText
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.selectedToVoiAccountFlow.collectLatest { account ->
                val accountIdentifier = account?.customAccountInfo?.customName ?: account?.address?.toShortenedAddress()
                if (account != null) {
                    // If an account is selected (either via button or valid manual entry)
                    binding.bridgeToVoiSection.toVoiAccountAddressTextView.text = getString(R.string.selected_account_colon, accountIdentifier)
                    binding.bridgeToVoiSection.toVoiAccountAddressTextView.visibility = View.VISIBLE

                    // If selection came from button, ensure EditText is cleared or matches
                    // This logic might need refinement based on UX preference (which input takes precedence)
                    if (binding.bridgeToVoiSection.toVoiAddressEditText.text.toString() != account.address) {
                         binding.bridgeToVoiSection.toVoiAddressEditText.setText(account.address)
                    }
                    binding.bridgeToVoiSection.selectToVoiAccountButton.text = getString(R.string.change_account) // Or show selected acc name
                } else {
                    // No account selected or manual input was cleared/invalid
                    binding.bridgeToVoiSection.toVoiAccountAddressTextView.visibility = View.GONE
                    binding.bridgeToVoiSection.selectToVoiAccountButton.text = getString(R.string.select_from_local_accounts)
                    // binding.bridgeToVoiSection.toVoiAddressEditText.setText("") // Already handled by onToVoiAddressManuallyChanged for blank
                }
            }
        }

        // Observers for the account lists themselves (for populating dialogs later)
        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.signableVoiAccountsFlow.collectLatest {
                // List available for "From Voi Account" selection dialog
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.allLocalVoiAccountsFlow.collectLatest {
                // List available for "To Voi Account" (local) selection dialog
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.allLocalAlgorandAccountsFlow.collectLatest {
                // List available for "From/To Algorand Account" (local) selection dialogs
            }
        }
    }

    private fun observeCalculatedAmounts() {
        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.calculatedFeeAmountFlow.collectLatest { fee ->
                val bridgeFeeText = getString(R.string.bridge_fee)
                val feeText = fee?.let {
                    val voi = it.toBigDecimal().movePointLeft(ALGO_DECIMALS)
                    "$bridgeFeeText: ${voi.stripTrailingZeros().toPlainString()} ${if (bridgeViewModel.isVoiToAlgoFlow.value) "VOI" else "aVOI"}"
                } ?: "$bridgeFeeText: -"
                binding.bridgeAmountSection.bridgeFeeTextView.text = feeText
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.netAmountToReceiveFlow.collectLatest { netAmount ->
                val netAmountText = netAmount?.let {
                    val voi = it.toBigDecimal().movePointLeft(ALGO_DECIMALS)
                    "Net: ${voi.stripTrailingZeros().toPlainString()} ${if (bridgeViewModel.isVoiToAlgoFlow.value) "aVOI" else "VOI"}"
                } ?: "Net: -"
                binding.bridgeAmountSection.bridgeFinalAmountTextView.text = netAmountText
            }
        }
    }

    private fun observeTransactionPipeline() {
        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.bridgeUiEventFlow.collectLatest { event ->
                when (event) {
                    is BridgeViewModel.BridgeUiEvent.ShowToast -> {
                        Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    }
                    is BridgeViewModel.BridgeUiEvent.ShowAvoiOptInHelp -> {
                        handleAvoiOptInHelp(event.deepLinkUrl)
                    }
                    is BridgeViewModel.BridgeUiEvent.ShowAlgoToVoiDeepLink -> {
                        handleAlgoToVoiDeepLink(event.deepLinkUrl)
                    }
                    is BridgeViewModel.BridgeUiEvent.ShowAlgoToVoiConfirmation -> {
                        val action = BridgeFragmentDirections.actionBridgeFragmentToBridgeConfirmationFragment(
                            amountToSendAtomic = event.amountToSendAtomic,
                            amountToReceiveAtomic = event.amountToReceiveAtomic,
                            destinationAddress = event.destinationAddress,
                            peraWalletDeeplink = event.peraWalletDeeplink
                        )
                        findNavController().navigate(action)
                    }
                    is BridgeViewModel.BridgeUiEvent.InvalidToAlgorandAddressError -> {
                        binding.bridgeToAlgoSection.toAlgorandAddressTextInputLayout.error = getString(R.string.invalid_algorand_address)
                    }
                    is BridgeViewModel.BridgeUiEvent.ClearToAlgorandAddressError -> {
                        binding.bridgeToAlgoSection.toAlgorandAddressTextInputLayout.error = null
                    }
                    is BridgeViewModel.BridgeUiEvent.InvalidFromAlgorandAddressError -> {
                        binding.bridgeFromAlgoSection.fromAlgorandAddressTextInputLayout.error = getString(R.string.invalid_algorand_address)
                    }
                    is BridgeViewModel.BridgeUiEvent.InvalidToVoiAddressError -> {
                        binding.bridgeToVoiSection.toVoiAddressTextInputLayout.error = getString(R.string.invalid_voi_address)
                    }
                    is BridgeViewModel.BridgeUiEvent.ClearToVoiAddressError -> {
                        binding.bridgeToVoiSection.toVoiAddressTextInputLayout.error = null
                    }
                    is BridgeViewModel.BridgeUiEvent.RequestVoiToAlgoSigning -> {
                        val args = bundleOf(
                            "assetTransaction" to null,
                            "transactionData" to event.transactionSignData
                        )
                        findNavController().navigate(R.id.action_global_send_algo_navigation, args)
                    }

                    BridgeViewModel.BridgeUiEvent.ClearFromAlgorandAddressError -> {
                        binding.bridgeFromAlgoSection.fromAlgorandAddressTextInputLayout.error = null
                    }
                }
            }
        }

        // Observer for TransactionSignManager results - changed to observe LiveData
        bridgeViewModel.transactionManagerResultLiveData.observe(viewLifecycleOwner) { event ->
            event?.consume()?.let { result ->
                when (result) {
                    is TransactionManagerResult.Success -> {
                        binding.bridgeProgressBar.isVisible = false
                        bridgeViewModel.processVoiToAramidSignedTransaction(result.signedTransactionDetail)
                    }
                    is TransactionManagerResult.Error.GlobalWarningError -> {
                        binding.bridgeProgressBar.isVisible = false
                        val (_, message) = result.getMessage(requireContext())
                        showSnackbar(message.toString(), binding.root)
                    }
                    is TransactionManagerResult.Loading -> {
                        binding.bridgeProgressBar.isVisible = true
                    }
                    is TransactionManagerResult.LedgerWaitingForApproval -> {
                        binding.bridgeProgressBar.isVisible = false
                        Toast.makeText(context, "Ledger: Waiting for approval on ${result.bluetoothName}", Toast.LENGTH_LONG).show()
                    }
                    is TransactionManagerResult.LedgerScanFailed -> {
                        binding.bridgeProgressBar.isVisible = false
                        Toast.makeText(context, "Ledger: Scan failed. Please try again.", Toast.LENGTH_LONG).show()
                    }
                    is TransactionManagerResult.LedgerOperationCanceled -> {
                        binding.bridgeProgressBar.isVisible = false
                        Toast.makeText(context, "Ledger: Operation cancelled.", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        binding.bridgeProgressBar.isVisible = false
                        Toast.makeText(context, "Transaction signing: An unknown event occurred", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.transactionStatusFlow.collectLatest { status ->
                binding.bridgeProgressBar.isVisible = status == TransactionStatus.LOADING && !binding.bridgeProgressBar.isVisible
                binding.bridgeButton.isEnabled = status != TransactionStatus.LOADING
            }
        }
    }

    // BridgeAccountSelectionBottomSheet.Listener implementation
    override fun onAccountSelected(accountItem: BaseAccountSelectionListItem.BaseAccountItem) {
        when (currentAccountSelectionTarget) {
            AccountSelectionTarget.FROM_VOI -> {
                findAccountDetailByAddress(accountItem.address)?.let {
                    bridgeViewModel.onFromVoiAccountSelected(it)
                }
            }
            AccountSelectionTarget.TO_VOI -> {
                findAccountDetailByAddress(accountItem.address)?.let {
                    bridgeViewModel.onToVoiAccountSelected(it)
                }
            }
            AccountSelectionTarget.FROM_ALGO_LOCAL -> {
                binding.bridgeFromAlgoSection.fromAlgorandAddressEditText.setText(accountItem.address)
                // TextWatcher on fromAlgorandAddressEditText will call bridgeViewModel.onFromAlgorandAddressChanged
            }
            AccountSelectionTarget.TO_ALGO_LOCAL -> {
                binding.bridgeToAlgoSection.toAlgorandAddressEditText.setText(accountItem.address)
                // TextWatcher on toAlgorandAddressEditText will call bridgeViewModel.onToAlgorandAddressChanged
            }
            null -> {
                // Should not happen, but good to log or handle
                Toast.makeText(context, "Account selection target not set.", Toast.LENGTH_SHORT).show()
            }
        }
        currentAccountSelectionTarget = null // Reset target
    }

    private fun findAccountDetailByAddress(address: String): AccountDetail? {
        return bridgeViewModel.userVoiAccountsFlow.value.firstOrNull { it.address == address }
    }

    private fun handleAvoiOptInHelp(deepLinkUrl: String) {
        // TODO: Show a confirmation dialog before opening deeplink?
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLinkUrl))
        startActivity(intent)
    }

    private fun handleAlgoToVoiDeepLink(deepLinkUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLinkUrl))
        startActivity(intent)
        Toast.makeText(context, "Complete transaction in Pera Wallet", Toast.LENGTH_LONG).show()
    }
}

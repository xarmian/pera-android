package com.algorand.android.ui.bridge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.algorand.android.R
import com.algorand.android.core.DaggerBaseFragment
import com.algorand.android.databinding.FragmentBridgeConfirmationBinding
import com.algorand.android.models.FragmentConfiguration
import com.algorand.android.models.ToolbarConfiguration
import com.algorand.android.utils.ALGO_DECIMALS
import com.algorand.android.utils.VOI_DECIMALS // Assuming this constant exists or will be created
import com.algorand.android.utils.viewbinding.viewBinding
import com.algorand.android.utils.toShortenedAddress
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BridgeConfirmationFragment : DaggerBaseFragment(R.layout.fragment_bridge_confirmation) {

    private val binding by viewBinding(FragmentBridgeConfirmationBinding::bind)
    private val args: BridgeConfirmationFragmentArgs by navArgs()

    override val fragmentConfiguration = FragmentConfiguration(
        toolbarConfiguration = ToolbarConfiguration(
            titleResId = R.string.confirm_transaction,
            startIconResId = R.drawable.ic_left_arrow,
            startIconClick = ::onToolbarNavigateUp
        )
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populateUi()
        initListeners()
    }

    private fun populateUi() {
        val avoiToSend = args.amountToSendAtomic.toBigDecimal().movePointLeft(ALGO_DECIMALS) // aVOI has ALGO_DECIMALS
        val voiToReceive = args.amountToReceiveAtomic.toBigDecimal().movePointLeft(VOI_DECIMALS) // VOI has VOI_DECIMALS

        binding.sendingAmountTextView.text = getString(R.string.amount_avoi_template, avoiToSend.stripTrailingZeros().toPlainString())
        binding.receivingAmountTextView.text = getString(R.string.amount_voi_template, voiToReceive.stripTrailingZeros().toPlainString())
        binding.toAccountAddressTextView.text = args.destinationAddress.toShortenedAddress()
        // The static message is already in the layout (bridge_time_estimate_message)
    }

    private fun initListeners() {
        binding.sendToPeraWalletButton.setOnClickListener {
            openDeepLink(args.peraWalletDeeplink)
        }

        binding.sendToDeflyWalletButton.setOnClickListener {
            val deflyDeeplink = args.peraWalletDeeplink.replaceFirst("perawallet://", "defly://")
            openDeepLink(deflyDeeplink)
        }

        binding.viewQrCodeButton.setOnClickListener {
            val amountToSendText = binding.sendingAmountTextView.text.toString()
            val amountToReceiveText = binding.receivingAmountTextView.text.toString()
            // Ensure we pass the full destination address, not the shortened one.
            val receivingAccountText = args.destinationAddress

            val action = BridgeConfirmationFragmentDirections.actionBridgeConfirmationFragmentToShowQrFragment(
                title = getString(R.string.scan_with_your_wallet),
                qrText = args.peraWalletDeeplink,
                tokenToSend = "aVOI",
                amountToSend = amountToSendText,
                amountToReceive = amountToReceiveText,
                receivingAccount = receivingAccountText
            )
            findNavController().navigate(action)
        }
    }

    private fun openDeepLink(uriString: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
            startActivity(intent)
            Toast.makeText(context, R.string.complete_transaction_in_your_wallet, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, R.string.error_opening_deeplink, Toast.LENGTH_LONG).show()
            // Log error
        }
    }

    private fun onToolbarNavigateUp() {
        findNavController().popBackStack()
    }
}

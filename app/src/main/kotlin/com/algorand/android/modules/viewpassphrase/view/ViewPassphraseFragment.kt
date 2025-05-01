/*
 * Copyright 2022-2025 Pera Wallet, LDA
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.algorand.android.modules.viewpassphrase.view

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.algorand.android.R
import com.algorand.android.core.DaggerBaseFragment
import com.algorand.android.databinding.FragmentViewPassphraseBinding
import com.algorand.android.models.FragmentConfiguration
import com.algorand.android.models.ToolbarConfiguration
import com.algorand.android.utils.disableScreenCapture
import com.algorand.android.utils.enableScreenCapture
import com.algorand.android.utils.extensions.collectLatestOnLifecycle
import com.algorand.android.utils.viewbinding.viewBinding
import com.algorand.wallet.ui.accountdetail.viewpassphrase.ViewPassphraseViewModel
import com.algorand.wallet.ui.accountdetail.viewpassphrase.ViewPassphraseViewModel.ViewEvent.NavigateBack
import com.algorand.wallet.ui.accountdetail.viewpassphrase.ViewPassphraseViewModel.ViewEvent.ShowGenericError
import com.algorand.wallet.ui.accountdetail.viewpassphrase.ViewPassphraseViewModel.ViewState
import com.algorand.wallet.ui.accountdetail.viewpassphrase.ViewPassphraseViewModel.ViewState.Content
import com.algorand.wallet.ui.accountdetail.viewpassphrase.ViewPassphraseViewModel.ViewState.Idle
import com.algorand.wallet.ui.accountdetail.viewpassphrase.ViewPassphraseViewModel.ViewState.Loading
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ViewPassphraseFragment : DaggerBaseFragment(R.layout.fragment_view_passphrase) {

    private val toolbarConfiguration = ToolbarConfiguration(
        titleResId = R.string.passphrase,
        startIconResId = R.drawable.ic_left_arrow,
        startIconClick = ::navBack
    )

    override val fragmentConfiguration = FragmentConfiguration()

    private val binding by viewBinding(FragmentViewPassphraseBinding::bind)

    private val viewPassphraseViewModel: ViewPassphraseViewModel by viewModels()

    private var isScreenCaptureEnablingAllowed = true

    private val onWindowFocusChangeListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        isScreenCaptureEnablingAllowed = hasFocus
    }

    private val viewStateCollector: suspend (ViewState) -> Unit = {
        updateViewState(it)
    }

    private val viewEventCollector: suspend (ViewPassphraseViewModel.ViewEvent) -> Unit = {
        when (it) {
            ShowGenericError -> showGlobalError(getString(R.string.an_error_occured), tag = baseActivityTag)
            NavigateBack -> navBack()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewPassphraseToolbar.configure(toolbarConfiguration)
        initObserver()
        initListeners()
    }

    private fun initObserver() {
        collectLatestOnLifecycle(viewPassphraseViewModel.viewEvent, viewEventCollector, Lifecycle.State.CREATED)
        collectLatestOnLifecycle(viewPassphraseViewModel.state, viewStateCollector)
    }

    private fun initListeners() {
        binding.displayModeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.mnemonicRadioButton -> showMnemonicView()
                R.id.qrCodeRadioButton -> showQrCodeView()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        view?.viewTreeObserver?.addOnWindowFocusChangeListener(onWindowFocusChangeListener)
        activity?.disableScreenCapture()
        val address = requireArguments().getString(ACCOUNT_ADDRESS).orEmpty()
        viewPassphraseViewModel.initViewState(address)
    }

    override fun onStop() {
        if (isScreenCaptureEnablingAllowed) {
            activity?.enableScreenCapture()
        }
        view?.viewTreeObserver?.removeOnWindowFocusChangeListener(onWindowFocusChangeListener)
        super.onStop()
    }

    private fun updateViewState(state: ViewState) {
        binding.progressBar.isVisible = state is Loading
        when (state) {
            is Idle -> { /* Reset UI elements? */ }
            is Loading -> {
                // Handled above, hide content views
                binding.passphraseBoxView.visibility = View.INVISIBLE
                binding.qrCodeImageView.visibility = View.INVISIBLE
                binding.displayModeRadioGroup.visibility = View.INVISIBLE
            }
            is Content -> {
                binding.displayModeRadioGroup.visibility = View.VISIBLE
                binding.passphraseBoxView.setPassphrases(state.mnemonicWords)
                binding.qrCodeRadioButton.isEnabled = state.isQrExportAvailable

                // Ensure initial view is correct based on availability and current check state
                if (!state.isQrExportAvailable) {
                     binding.mnemonicRadioButton.isChecked = true // Force mnemonic if QR unavailable
                     showMnemonicView() // Show mnemonic view explicitly
                } else {
                     // If content loaded, show view based on which radio button is checked
                     if (binding.mnemonicRadioButton.isChecked) {
                         showMnemonicView()
                     } else if (binding.qrCodeRadioButton.isChecked) {
                         showQrCodeView() // Will trigger URI generation if QR is selected
                     }
                }
            }
        }
    }

    private fun showMnemonicView() {
        binding.passphraseBoxView.visibility = View.VISIBLE
        binding.qrCodeImageView.visibility = View.GONE
        binding.qrCodeImageView.setImageBitmap(null) // Clear QR if it was generated
    }

    private fun showQrCodeView() {
        binding.passphraseBoxView.visibility = View.GONE
        binding.qrCodeImageView.visibility = View.VISIBLE // Show the placeholder/loading view
        binding.qrCodeImageView.setImageBitmap(null) // Clear previous QR or error icon

        // Request URI generation from ViewModel
        val address = requireArguments().getString(ACCOUNT_ADDRESS).orEmpty()
        if (address.isNotEmpty()) {
             viewPassphraseViewModel.requestAccountExportUri(address) { result ->
                 // Ensure UI updates on main thread (lifecycleScope handles this)
                 viewLifecycleOwner.lifecycleScope.launch {
                     result.use(
                         onSuccess = { uriString -> generateAndDisplayQrCode(uriString) },
                         onFailed = { error, _ ->
                             // Handle error - Show error icon or message
                             binding.qrCodeImageView.setImageResource(R.drawable.ic_error) // Placeholder
                             // Consider logging the actual error
                             showGlobalError(getString(R.string.qr_code_generation_failed))
                         }
                     )
                 }
             }
        } else {
            // Handle case where address is missing (should not happen)
            showGlobalError(getString(R.string.an_error_occured))
            binding.qrCodeImageView.setImageResource(R.drawable.ic_error)
        }
    }

    private fun generateAndDisplayQrCode(uriString: String) {
        val qrSize = resources.getDimensionPixelSize(R.dimen.qr_code_size) // Use defined dimension
        val qrBitmap = generateQrCodeBitmap(uriString, qrSize) // Using the utility function
        if (qrBitmap != null) {
            binding.qrCodeImageView.setImageBitmap(qrBitmap)
        } else {
            // Error handled during generation or show here
            binding.qrCodeImageView.setImageResource(R.drawable.ic_error) // Placeholder error icon
            showGlobalError(getString(R.string.qr_code_generation_failed))
        }
    }

    /**
     * Generates a QR code bitmap from the given data string.
     */
    private fun generateQrCodeBitmap(data: String, size: Int): Bitmap? {
        return try {
            val barcodeEncoder = BarcodeEncoder()
            barcodeEncoder.encodeBitmap(data, BarcodeFormat.QR_CODE, size, size)
        } catch (e: Exception) {
            null // Return null on error, the caller handles showing a user message
        }
    }

    private companion object {
        const val ACCOUNT_ADDRESS = "accountAddress"
    }
}

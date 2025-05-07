package com.algorand.android.ui.bridge

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.algorand.android.R
import com.algorand.android.core.DaggerBaseFragment
import com.algorand.android.databinding.FragmentBridgeBinding
import com.algorand.android.models.FragmentConfiguration
import com.algorand.android.utils.viewbinding.viewBinding
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BridgeFragment : DaggerBaseFragment(R.layout.fragment_bridge) {

    private val bridgeViewModel: BridgeViewModel by viewModels()

    private val binding by viewBinding(FragmentBridgeBinding::bind)

    override val fragmentConfiguration = FragmentConfiguration(
        toolbarConfiguration = null, // TODO: Configure toolbar if needed
        isBottomBarNeeded = true
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
                    // updateUiForBridgeMode is now called via observer on isVoiToAlgoFlow
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) { /* Nothing to do */ }
            override fun onTabReselected(tab: TabLayout.Tab?) { /* Nothing to do */ }
        })
        // Initial mode is set by ViewModel's default, observer will pick it up.
        // bridgeViewModel.onBridgeModeSelected(true) // ViewModel defaults to true, so this specific call might be redundant if observer handles initial state.
    }

    private fun initUiListeners() {
        binding.bridgeButton.setOnClickListener {
            bridgeViewModel.onBridgeRequested()
        }

        binding.bridgeFromVoiSection.selectFromVoiAccountButton.setOnClickListener {
            bridgeViewModel.onSelectFromVoiAccountClicked()
        }
        binding.bridgeToAlgoSection.toAlgorandAddressEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { /* NA */ }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { /* NA */ }
            override fun afterTextChanged(s: Editable?) {
                bridgeViewModel.onToAlgorandAddressChanged(s.toString().trim())
            }
        })
        binding.bridgeToAlgoSection.avoiOptInHelpButton.setOnClickListener {
            bridgeViewModel.onAvoiOptInHelpClicked()
        }

        binding.bridgeFromAlgoSection.fromAlgorandAddressEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { /* NA */ }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { /* NA */ }
            override fun afterTextChanged(s: Editable?) {
                bridgeViewModel.onFromAlgorandAddressChanged(s.toString().trim())
            }
        })
        binding.bridgeToVoiSection.selectToVoiAccountButton.setOnClickListener {
            bridgeViewModel.onSelectToVoiAccountClicked()
        }

        binding.bridgeAmountSection.bridgeAmountEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { /* NA */ }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { /* NA */ }
            override fun afterTextChanged(s: Editable?) {
                bridgeViewModel.onAmountChanged(s.toString().trim())
            }
        })
    }

    private fun updateUiForBridgeMode(isVoiToAlgo: Boolean) {
        binding.bridgeFromVoiSection.root.isVisible = isVoiToAlgo
        binding.bridgeToAlgoSection.root.isVisible = isVoiToAlgo

        binding.bridgeFromAlgoSection.root.isVisible = !isVoiToAlgo
        binding.bridgeToVoiSection.root.isVisible = !isVoiToAlgo

        val amountSuffix = if (isVoiToAlgo) getString(R.string.voi_ticker) else getString(R.string.avoi_ticker)
        binding.bridgeAmountSection.bridgeAmountTextInputLayout.suffixText = amountSuffix
    }

    private fun initObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.isVoiToAlgoFlow.collectLatest {
                updateUiForBridgeMode(it)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.fromAccountAddressFlow.collectLatest { address ->
                if (binding.bridgeFromAlgoSection.fromAlgorandAddressEditText.text.toString() != address) {
                    binding.bridgeFromAlgoSection.fromAlgorandAddressEditText.setText(address)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            bridgeViewModel.toAccountAddressFlow.collectLatest { address ->
                if (binding.bridgeToAlgoSection.toAlgorandAddressEditText.text.toString() != address) {
                    binding.bridgeToAlgoSection.toAlgorandAddressEditText.setText(address)
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

        // TODO: Observe other StateFlows from BridgeViewModel (balances, opt-in status, errors, loading, events)
    }
} 
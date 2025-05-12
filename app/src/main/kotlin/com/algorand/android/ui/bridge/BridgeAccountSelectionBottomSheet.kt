package com.algorand.android.ui.bridge

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentManager
import com.algorand.android.R
import com.algorand.android.core.DaggerBaseBottomSheet
import com.algorand.android.databinding.LayoutBottomSheetBridgeAccountSelectionBinding
import com.algorand.android.models.BaseAccountSelectionListItem
import com.algorand.android.utils.viewbinding.viewBinding

class BridgeAccountSelectionBottomSheet : DaggerBaseBottomSheet(
    layoutResId = R.layout.layout_bottom_sheet_bridge_account_selection,
    fullPageNeeded = false,
    firebaseEventScreenId = null // Configure if needed
) {

    private val binding by viewBinding(LayoutBottomSheetBridgeAccountSelectionBinding::bind)

    private var listener: Listener? = null

    // These will be initialized in onViewCreated or onCreate
    private var titleText: String? = null
    private var accountsList: ArrayList<BaseAccountSelectionListItem.BaseAccountItem>? = null

    private val accountAdapter = BridgeAccountSelectionAdapter { selectedAccount ->
        listener?.onAccountSelected(selectedAccount)
        dismissAllowingStateLoss()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        titleText = arguments?.getString(TITLE_TEXT_KEY)
        accountsList = arguments?.getParcelableArrayList(ACCOUNTS_LIST_KEY)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // titleText and accountsList are already retrieved in onCreate
        binding.bottomSheetTitleTextView.text = titleText ?: "Select Account" // Provide a default if null
        binding.accountsRecyclerView.adapter = accountAdapter
        accountsList?.let { accountAdapter.submitList(it) }
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun interface Listener {
        fun onAccountSelected(accountItem: BaseAccountSelectionListItem.BaseAccountItem)
    }

    companion object {
        private const val TITLE_TEXT_KEY = "title_text"
        private const val ACCOUNTS_LIST_KEY = "accounts_list"
        const val TAG = "BridgeAccountSelectionBottomSheet"

        fun show(
            fragmentManager: FragmentManager,
            title: String,
            accounts: List<BaseAccountSelectionListItem.BaseAccountItem>,
            listener: Listener
        ): BridgeAccountSelectionBottomSheet {
            return BridgeAccountSelectionBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(TITLE_TEXT_KEY, title)
                    // ArrayList is Parcelable, List is not directly
                    putParcelableArrayList(ACCOUNTS_LIST_KEY, ArrayList(accounts))
                }
                setListener(listener)
                show(fragmentManager, TAG)
            }
        }
    }
}

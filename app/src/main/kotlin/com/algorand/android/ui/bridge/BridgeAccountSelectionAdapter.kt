package com.algorand.android.ui.bridge

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.algorand.android.databinding.ItemBridgeAccountSelectionBinding
import com.algorand.android.models.BaseAccountSelectionListItem

class BridgeAccountSelectionAdapter(
    private val listener: Listener
) : ListAdapter<BaseAccountSelectionListItem.BaseAccountItem, BridgeAccountSelectionAdapter.AccountViewHolder>(AccountItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val binding = ItemBridgeAccountSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AccountViewHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AccountViewHolder(
        private val binding: ItemBridgeAccountSelectionBinding,
        private val listener: Listener
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BaseAccountSelectionListItem.BaseAccountItem) {
            if (item is BaseAccountSelectionListItem.BaseAccountItem.AccountItem) {
                val config = item.accountListItem.itemConfiguration
                binding.accountItemView.setTitleText(config.accountDisplayName?.primaryDisplayName)
                binding.accountItemView.setDescriptionText(config.accountAddress)
                config.accountIconDrawablePreview?.iconResId?.let {
                    binding.accountItemView.setStartIconResource(it)
                } ?: binding.accountItemView.setStartIconDrawable(null)
            } else {
                binding.accountItemView.setTitleText(item.displayName)
                binding.accountItemView.setDescriptionText(item.address)
                binding.accountItemView.setStartIconDrawable(null)
                binding.accountItemView.setPrimaryValueText(null)
                binding.accountItemView.setSecondaryValueText(null)
            }
            itemView.setOnClickListener {
                listener.onAccountSelected(item)
            }
        }
    }

    fun interface Listener {
        fun onAccountSelected(accountItem: BaseAccountSelectionListItem.BaseAccountItem)
    }

    private class AccountItemDiffCallback : DiffUtil.ItemCallback<BaseAccountSelectionListItem.BaseAccountItem>() {
        override fun areItemsTheSame(
            oldItem: BaseAccountSelectionListItem.BaseAccountItem,
            newItem: BaseAccountSelectionListItem.BaseAccountItem
        ): Boolean {
            return oldItem.address == newItem.address
        }

        override fun areContentsTheSame(
            oldItem: BaseAccountSelectionListItem.BaseAccountItem,
            newItem: BaseAccountSelectionListItem.BaseAccountItem
        ): Boolean {
            return oldItem == newItem
        }
    }
}

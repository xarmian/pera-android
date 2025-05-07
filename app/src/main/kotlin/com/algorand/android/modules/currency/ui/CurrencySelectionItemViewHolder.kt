/*
 * Copyright 2025 Vera Wallet, LDA
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.algorand.android.modules.currency.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.algorand.android.databinding.ItemCurrencySelectionBinding
import com.algorand.android.ui.settings.selection.CurrencyListItem

class CurrencySelectionItemViewHolder(
    private val binding: ItemCurrencySelectionBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(currencyListItem: CurrencyListItem) {
        with(binding) {
            currencyActualSymbolTextView.text = currencyListItem.currencySymbol
            currencyActualSymbolTextView.visibility = if (currencyListItem.currencySymbol.isNullOrEmpty()) View.GONE else View.VISIBLE

            currencyIdTextView.text = currencyListItem.currencyId
            currencyNameTextView.text = currencyListItem.currencyName
            radioButton.isSelected = currencyListItem.isSelected
            // itemView's background can also be updated based on isSelected if selector_selectable_item_background doesn't handle states
            itemView.isSelected = currencyListItem.isSelected // For stateful background
        }
    }

    companion object {
        fun create(parent: ViewGroup): CurrencySelectionItemViewHolder {
            val binding = ItemCurrencySelectionBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return CurrencySelectionItemViewHolder(binding)
        }
    }
}

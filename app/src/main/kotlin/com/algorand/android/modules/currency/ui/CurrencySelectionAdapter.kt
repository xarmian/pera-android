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

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.algorand.android.ui.settings.selection.CurrencyListItem

class CurrencySelectionAdapter(
    private val onDifferentCurrencyListItemClick: (CurrencyListItem) -> Unit
) : RecyclerView.Adapter<CurrencySelectionItemViewHolder>() {

    private val list = mutableListOf<CurrencyListItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CurrencySelectionItemViewHolder {
        return CurrencySelectionItemViewHolder.create(parent).apply {
            itemView.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    val selectedListItem = list[bindingAdapterPosition]
                    if (selectedListItem.isSelected.not()) {
                        onDifferentCurrencyListItemClick.invoke(selectedListItem)
                        // Logic to update selection state and notify changes
                        val previouslySelectedItemIndex = list.indexOfFirst { it.isSelected }
                        if (previouslySelectedItemIndex != -1) {
                            list[previouslySelectedItemIndex].isSelected = false
                            notifyItemChanged(previouslySelectedItemIndex, SELECTION_CHANGED_PAYLOAD)
                        }
                        selectedListItem.isSelected = true
                        notifyItemChanged(bindingAdapterPosition, SELECTION_CHANGED_PAYLOAD)
                    }
                }
            }
        }
    }

    override fun onBindViewHolder(holder: CurrencySelectionItemViewHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun onBindViewHolder(
        holder: CurrencySelectionItemViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(SELECTION_CHANGED_PAYLOAD)) {
            holder.bind(list[position]) // Rebind for selection change
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    fun setItems(newList: List<CurrencyListItem>) {
        list.apply {
            clear()
            addAll(newList)
        }
        notifyDataSetChanged()
    }

    override fun getItemCount() = list.size

    companion object {
        private const val SELECTION_CHANGED_PAYLOAD = "selection_changed_payload"
    }
}

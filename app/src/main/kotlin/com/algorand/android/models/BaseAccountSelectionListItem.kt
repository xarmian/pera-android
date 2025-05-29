/*
 * Copyright 2025 Pera Wallet, LDA
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License
 */

package com.algorand.android.models

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.StringRes
import com.algorand.android.modules.accountsorting.ui.domain.model.BaseAccountAndAssetListItem.AccountListItem
import kotlinx.parcelize.Parcelize

sealed class BaseAccountSelectionListItem : RecyclerListItem {

    abstract override fun areItemsTheSame(other: RecyclerListItem): Boolean
    abstract override fun areContentsTheSame(other: RecyclerListItem): Boolean

    @Parcelize
    data class HeaderItem(@StringRes val titleRes: Int) : BaseAccountSelectionListItem(), Parcelable {

        override fun areItemsTheSame(other: RecyclerListItem): Boolean {
            return other is HeaderItem && titleRes == other.titleRes
        }

        override fun areContentsTheSame(other: RecyclerListItem): Boolean {
            return other is HeaderItem && other == this
        }
    }

    @Parcelize
    data class PasteItem(val address: String) : BaseAccountSelectionListItem(), Parcelable {
        override fun areItemsTheSame(other: RecyclerListItem): Boolean {
            return other is PasteItem && address == other.address
        }

        override fun areContentsTheSame(other: RecyclerListItem): Boolean {
            return other is PasteItem && this == other
        }
    }

    sealed class BaseAccountItem : BaseAccountSelectionListItem(), Parcelable {
        abstract val displayName: String
        abstract val address: String

        data class ContactItem(
            override val displayName: String,
            override val address: String,
            val imageUri: Uri?
        ) : BaseAccountItem() {
            override fun areItemsTheSame(other: RecyclerListItem): Boolean {
                return other is ContactItem && address == other.address
            }

            override fun areContentsTheSame(other: RecyclerListItem): Boolean {
                return other is ContactItem && displayName == other.displayName && imageUri == other.imageUri
            }
        }

        data class AccountItem(val accountListItem: AccountListItem) : BaseAccountItem() {

            override val displayName: String = accountListItem.itemConfiguration
                .accountDisplayName
                ?.primaryDisplayName
                .orEmpty()

            override val address: String = accountListItem.itemConfiguration.accountAddress

            override fun areItemsTheSame(other: RecyclerListItem): Boolean {
                return other is AccountItem &&
                    other.accountListItem.itemConfiguration.accountAddress ==
                    accountListItem.itemConfiguration.accountAddress
            }

            override fun areContentsTheSame(other: RecyclerListItem): Boolean {
                return other is AccountItem && this == other
            }
        }

        data class AccountErrorItem(val accountListItem: AccountListItem) : BaseAccountItem() {

            override val displayName: String = accountListItem.itemConfiguration
                .accountDisplayName
                ?.primaryDisplayName
                .orEmpty()

            override val address: String = accountListItem.itemConfiguration.accountAddress

            override fun areItemsTheSame(other: RecyclerListItem): Boolean {
                return other is AccountErrorItem &&
                    other.accountListItem.itemConfiguration.accountAddress ==
                    accountListItem.itemConfiguration.accountAddress
            }

            override fun areContentsTheSame(other: RecyclerListItem): Boolean {
                return other is AccountErrorItem && this == other
            }
        }

        data class NftDomainAccountItem(
            override val displayName: String,
            override val address: String,
            val serviceLogoUrl: String?
        ) : BaseAccountItem() {

            override fun areItemsTheSame(other: RecyclerListItem): Boolean {
                return other is NftDomainAccountItem && other.address == address
            }

            override fun areContentsTheSame(other: RecyclerListItem): Boolean {
                return other is NftDomainAccountItem && this == other
            }
        }

        override fun areItemsTheSame(other: RecyclerListItem): Boolean {
            TODO("Not yet implemented")
        }

        override fun areContentsTheSame(other: RecyclerListItem): Boolean {
            TODO("Not yet implemented")
        }

        override fun describeContents(): Int {
            TODO("Not yet implemented")
        }

        override fun writeToParcel(p0: Parcel, p1: Int) {
            TODO("Not yet implemented")
        }
    }
}

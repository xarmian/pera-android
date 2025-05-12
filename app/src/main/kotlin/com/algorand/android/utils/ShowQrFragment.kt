/*
 * Copyright 2025 Pera Wallet, LDA
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.algorand.android.utils

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.navArgs
import com.algorand.android.R
import com.algorand.android.core.DaggerBaseFragment
import com.algorand.android.databinding.FragmentShowQrBinding
import com.algorand.android.models.FragmentConfiguration
import com.algorand.android.models.ToolbarConfiguration
import com.algorand.android.utils.viewbinding.viewBinding
import dagger.hilt.android.AndroidEntryPoint

const val SHORT_ADDRESS_LENGTH = 8

@AndroidEntryPoint
class ShowQrFragment : DaggerBaseFragment(R.layout.fragment_show_qr) {

    private val toolbarConfiguration = ToolbarConfiguration(
        startIconResId = R.drawable.ic_left_arrow,
        startIconClick = ::navBack
    )

    override val fragmentConfiguration = FragmentConfiguration(
        firebaseEventScreenId = FIREBASE_EVENT_SCREEN_ID,
        toolbarConfiguration = toolbarConfiguration
    )

    private val qrCodeBitmap by lazy {
        getQrCodeBitmap(resources.getDimensionPixelSize(R.dimen.show_qr_size), args.qrText)
    }

    private val binding by viewBinding(FragmentShowQrBinding::bind)

    private val args: ShowQrFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        getAppToolbar()?.changeTitle(args.title)
        with(binding) {
            qrImageView.setImageBitmap(qrCodeBitmap)

            // Instruction text is set from XML via @string resource

            // Token to Send
            if (args.tokenToSend != null) {
                tokenToSendTextView.text = args.tokenToSend
                tokenToSendLabelTextView.visibility = View.VISIBLE
                tokenToSendTextView.visibility = View.VISIBLE
            } else {
                tokenToSendLabelTextView.visibility = View.GONE
                tokenToSendTextView.visibility = View.GONE
            }

            // Amount to Send
            if (args.amountToSend != null) {
                amountToSendTextView.text = args.amountToSend
                amountToSendLabelTextView.visibility = View.VISIBLE
                amountToSendTextView.visibility = View.VISIBLE
            } else {
                amountToSendLabelTextView.visibility = View.GONE
                amountToSendTextView.visibility = View.GONE
            }

            // Amount to Receive
            if (args.amountToReceive != null) {
                amountToReceiveTextView.text = args.amountToReceive
                amountToReceiveLabelTextView.visibility = View.VISIBLE
                amountToReceiveTextView.visibility = View.VISIBLE
            } else {
                amountToReceiveLabelTextView.visibility = View.GONE
                amountToReceiveTextView.visibility = View.GONE
            }

            // Receiving Account
            if (args.receivingAccount != null) {
                receivingAccountTextView.text = args.receivingAccount?.toShortenedAddress(SHORT_ADDRESS_LENGTH) // Or show full, or make it configurable
                receivingAccountLabelTextView.visibility = View.VISIBLE
                receivingAccountTextView.visibility = View.VISIBLE
            } else {
                receivingAccountLabelTextView.visibility = View.GONE
                receivingAccountTextView.visibility = View.GONE
            }
        }
    }

    companion object {
        private const val FIREBASE_EVENT_SCREEN_ID = "screen_show_qr_bridge"
    }
}

/*
 *  Copyright 2025 Pera Wallet, LDA
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License
 */

package com.algorand.android.discover.common.ui.model

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.Message
import android.graphics.Bitmap
import android.webkit.WebResourceRequest

class PeraWebChromeClient(
    val listener: PeraWebViewClient.PeraWebViewClientListener?
) : WebChromeClient() {
    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message
    ): Boolean {
        // Create a dummy WebView to capture the URL
        val dummyWebView = WebView(view.context)
        dummyWebView.webViewClient = object : WebViewClient() {
            // Use shouldOverrideUrlLoading for newer APIs
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val targetUrl = request?.url?.toString()
                if (targetUrl != null) {
                    listener?.onTargetBlankLinkClicked(targetUrl)
                }
                // Always return true, we don't want the dummy webview to actually load the url.
                return true
            }

            // Fallback for older APIs or cases where shouldOverrideUrlLoading isn't called
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                 if (url != null) {
                    listener?.onTargetBlankLinkClicked(url)
                }
            }
        }

        // Pass the dummy WebView back to the system via the transport message
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        transport.webView = dummyWebView
        resultMsg.sendToTarget()

        // Return true to indicate we're handling the window creation
        return true
    }
}

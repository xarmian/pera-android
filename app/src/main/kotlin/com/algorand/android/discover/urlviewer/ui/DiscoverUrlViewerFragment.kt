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

package com.algorand.android.discover.urlviewer.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.MenuItem
import android.view.LayoutInflater
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.algorand.android.R
import com.algorand.android.databinding.FragmentDiscoverUrlViewerBinding
import com.algorand.android.discover.common.ui.BaseDiscoverFragment
import com.algorand.android.discover.common.ui.model.DappFavoriteElement
import com.algorand.android.discover.common.ui.model.PeraWebChromeClient
import com.algorand.android.discover.common.ui.model.PeraWebViewClient
import com.algorand.android.discover.common.ui.model.WebViewError
import com.algorand.android.discover.dapp.ui.DiscoverDappFragment
import com.algorand.android.discover.home.domain.PeraMobileWebInterface
import com.algorand.android.MainActivity
import com.algorand.android.discover.urlviewer.ui.model.DiscoverUrlViewerPreview
import com.algorand.android.discover.utils.getDiscoverCustomUrl
import com.algorand.android.models.FragmentConfiguration
import com.algorand.android.models.ToolbarConfiguration
import com.algorand.android.utils.extensions.collectLatestOnLifecycle
import com.algorand.android.utils.extensions.hide
import com.algorand.android.utils.extensions.show
import com.algorand.android.utils.listenToNavigationResult
import com.algorand.android.utils.browser.openExternalBrowserApp
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class DiscoverUrlViewerFragment :
    BaseDiscoverFragment(R.layout.fragment_discover_url_viewer),
    PeraMobileWebInterface.WebInterfaceListener,
    Toolbar.OnMenuItemClickListener {

    private val toolbarConfiguration = ToolbarConfiguration(
        startIconResId = R.drawable.ic_close,
        startIconClick = ::navBack
    )

    override lateinit var binding: FragmentDiscoverUrlViewerBinding

    override val discoverViewModel: DiscoverUrlViewerViewModel by viewModels()

    override val fragmentConfiguration = FragmentConfiguration(
        toolbarConfiguration = toolbarConfiguration,
        isBottomBarNeeded = false
    )

    private val discoverUrlViewerPreviewCollector: suspend (DiscoverUrlViewerPreview) -> Unit = { preview ->
        with(preview) {
            updateUi(this)
            loadingErrorEvent?.consume()?.run {
                handleLoadingError(this)
            }
            reloadPageEvent?.consume()?.run {
                loadUrl(preview)
            }
            dappViewerScreenRequestEvent?.consume()?.run {
                nav(this)
            }
        }
    }

    override val peraWebViewClientListener = object : PeraWebViewClient.PeraWebViewClientListener {
        override fun onPageStarted() {
            discoverViewModel.onPageStarted()
        }

        override fun onPageFinished(title: String?, url: String?) {
            discoverViewModel.onPageFinished(title, url)
            // Optionally update toolbar title?
            // getToolbarConfiguration()?.title = title
            // binding.toolbar.updateToolbarConfiguration(getToolbarConfiguration())
        }

        override fun onPageRequestedShouldOverrideUrlLoading(url: String): Boolean {
            // Handle WalletConnect URLs first
            if (url.startsWith("wc:")) {
                (activity as? MainActivity)?.handleWalletConnectUrl(url)
                return true // WC URL handled
            }
            // Handle standard Pera deep links
            if (url.startsWith("perawallet-wc://")) {
                (activity as? MainActivity)?.handleDeepLink(url)
                return true // Deep link handled
            }
            // Let the default client handle other URLs (like http/https)
            return false
        }

        override fun onWalletConnectUrlDetected(url: String) {
            // This might be redundant now with the override, but keep for safety
            (activity as? MainActivity)?.handleWalletConnectUrl(url)
        }

        override fun onError() {
            discoverViewModel.onError()
        }

        override fun onHttpError() {
            discoverViewModel.onHttpError()
        }

        override fun onPageUrlChanged() {
            // Implement if needed
        }

        override fun onRenderProcessGone() {
            discoverViewModel.onError()
        }

        override fun onTargetBlankLinkClicked(url: String) {
            // Decide how to handle target=_blank links, e.g., open in the same view or external browser
            // For now, let the WebView handle it by returning false from onPageRequestedShouldOverrideUrlLoading
            // Or explicitly load in the current view: binding.webView.loadUrl(url)
        }

        override fun onEmailRequested(url: String) {
            // Implement email handling if needed
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Ensure the fragment menu is considered
        setHasOptionsMenu(true)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarMenu()
        initObservers()
        initWebview()
        initSavedStateListener()
    }

    private fun setupToolbarMenu() {
        // Access the toolbar using its ID from the binding
        (binding.root.findViewById<Toolbar>(R.id.toolbar))?.apply {
            inflateMenu(R.menu.discover_url_viewer_menu)
            setOnMenuItemClickListener(this@DiscoverUrlViewerFragment)
        }
    }

    // Handle menu item clicks
    override fun onMenuItemClick(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.open_in_browser_menu_item -> {
                onOpenInBrowserClick()
                true
            }
            else -> false
        }
    }

    override fun onReportActionFailed() {
        nav(
            DiscoverUrlViewerFragmentDirections.actionDiscoverUrlViewerFragmentToSingleButtonBottomSheetNavigation(
                titleAnnotatedString = getTitleForFailedReport(),
                descriptionAnnotatedString = getDescriptionForFailedReport(),
                buttonStringResId = R.string.got_it,
                drawableResId = R.drawable.ic_flag,
                drawableTintResId = R.color.negative,
                shouldDescriptionHasLinkMovementMethod = true
            )
        )
    }

    override fun bindWebView(view: View?) {
        view?.let { binding = FragmentDiscoverUrlViewerBinding.bind(it) }
    }

    override fun pushDappViewerScreen(jsonEncodedPayload: String) {
        discoverViewModel.pushDappViewerScreen(jsonEncodedPayload)
    }

    override fun pushNewScreen(jsonEncodedPayload: String) {
        discoverViewModel.pushNewScreen(jsonEncodedPayload)
    }

    private fun initSavedStateListener() {
        listenToNavigationResult<DappFavoriteElement?>(DiscoverDappFragment.ADD_FAVORITE_RESULT_KEY) { favorite ->
            favorite?.let { discoverViewModel.onFavoritesUpdate(it) }
        }
    }

    private fun handleLoadingError(error: WebViewError) {
        with(binding) {
            webView.hide()
            discoverViewModel.saveLastError(error)
            errorScreenState.show()
            when (error) {
                WebViewError.HTTP_ERROR -> {
                    errorTitleTextView.text = getString(R.string.well_this_is_unexpected)
                    errorDescriptionTextView.text = getString(R.string.we_are_not_able_to_find)
                }

                WebViewError.NO_CONNECTION -> {
                    errorTitleTextView.text = getString(R.string.no_internet_connection)
                    errorDescriptionTextView.text = getString(R.string.you_dont_seem_to_be_connected)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebview() {
        with(binding) {
            val peraWebInterface = PeraMobileWebInterface.create(this@DiscoverUrlViewerFragment)
            webView.addJavascriptInterface(peraWebInterface, PeraMobileWebInterface.WEB_INTERFACE_NAME)
            webView.webViewClient = PeraWebViewClient(peraWebViewClientListener)
            webView.webChromeClient = PeraWebChromeClient(peraWebViewClientListener)
        }
    }

    private fun updateUi(preview: DiscoverUrlViewerPreview) {
        binding.loadingProgressBar.isVisible = preview.isLoading
        discoverViewModel.getLastError()?.let {
            handleLoadingError(it)
        } ?: run {
            binding.webView.show()
            binding.errorScreenState.hide()
        }
    }

    private fun loadUrl(preview: DiscoverUrlViewerPreview) {
        binding.webView.loadUrl(
            // TODO Get locale from PeraLocaleProvider after merging TinymanSwapSprint2 branch
            getDiscoverCustomUrl(
                url = preview.url,
                themePreference = getWebViewThemeFromThemePreference(preview.themePreference),
                currency = discoverViewModel.getPrimaryCurrencyId(),
                locale = Locale.getDefault().language
            )
        )
    }

    private fun initObservers() {
        viewLifecycleOwner.collectLatestOnLifecycle(
            discoverViewModel.discoverUrlViewerPreviewFlow,
            discoverUrlViewerPreviewCollector
        )
    }

    private fun onOpenInBrowserClick() {
        val currentUrl = discoverViewModel.discoverUrlViewerPreviewFlow.value.url
        if (currentUrl.isNotBlank()) {
            context?.openExternalBrowserApp(currentUrl)
        }
    }
}

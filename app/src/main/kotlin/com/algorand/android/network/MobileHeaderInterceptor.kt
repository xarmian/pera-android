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

package com.algorand.android.network

import android.os.Build
import com.algorand.android.BuildConfig
import com.algorand.android.models.Node
import java.util.Locale
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class MobileHeaderInterceptor(
    private val packageName: String = BuildConfig.APPLICATION_ID,
    private val appVersion: String = BuildConfig.VERSION_NAME,
    private val mobileApiKey: String = BuildConfig.MOBILE_API_KEY,
    private vararg val otherHeaders: Pair<String, String>,
    private val appName: String? = null,
    private val clientType: String = DEFAULT_CLIENT_TYPE,
    private val osVersion: String = Build.VERSION.SDK_INT.toString(),
    private val deviceModel: String = Build.MODEL
) : PeraInterceptor() {

    private val localLanguageTag: String
        get() = Locale.getDefault().language ?: Locale.ENGLISH.language

    private val defaultAppName = packageName.split('.')
        .run { elementAtOrNull(THIRD_ITEM_INDEX) ?: elementAtOrNull(SECOND_ITEM_INDEX).orEmpty() }
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    var currentActiveNode: Node? = null

    override fun safeIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBuilder = request.newBuilder()

        // Get the host of the original request
        val originalHost = request.url.host

        // Extract the host from the Envoi Base URL (assuming it's stored correctly in BuildConfig)
        val envoiHost = try {
            BuildConfig.ENVOI_MAINNET_BASE_URL.toHttpUrlOrNull()?.host
        } catch (e: Exception) {
            // Handle potential parsing errors if the BuildConfig value is malformed
            null
        }

        // Only rewrite URL if currentActiveNode is set AND the original host is NOT the Envoi host
        if (currentActiveNode != null && envoiHost != null && originalHost != envoiHost) {
            currentActiveNode?.let { activeNode ->
                val baseUrl = activeNode.mobileAlgorandAddress.toHttpUrlOrNull()
                if (baseUrl != null) {
                    val newUrl = request.url.newBuilder()
                        .scheme(baseUrl.scheme)
                        .host(baseUrl.toUrl().toURI().host) // Use host from active node
                        .port(baseUrl.port) // Use port from active node
                        .build()

                    requestBuilder.url(newUrl)
                }
            }
        }

        requestBuilder.addHeader(KEY_APP_NAME, appName ?: defaultAppName)
            .addHeader(KEY_CLIENT_TYPE, clientType)
            .addHeader(KEY_DEVICE_OS_VERSION, osVersion)
            .addHeader(KEY_APP_PACKAGE_NAME, packageName)
            .addHeader(KEY_APP_VERSION, appVersion)
            .addHeader(KEY_DEVICE_MODEL, deviceModel)
            .addHeader(MOBILE_API_KEY_HEADER, mobileApiKey)
            .addHeader(KEY_ACCEPT_LANGUAGE, localLanguageTag)
            .apply {
                otherHeaders.forEach { header ->
                    addHeader(header.first, header.second)
                }
            }
        return chain.proceed(requestBuilder.build())
    }

    companion object {
        private const val MOBILE_API_KEY_HEADER = "X-API-Key"
        private const val KEY_APP_NAME = "App-Name"
        private const val KEY_CLIENT_TYPE = "Client-Type"
        private const val KEY_DEVICE_OS_VERSION = "Device-OS-Version"
        private const val KEY_APP_PACKAGE_NAME = "App-Package-Name"
        private const val KEY_APP_VERSION = "App-Version"
        private const val KEY_DEVICE_MODEL = "Device-Model"
        private const val DEFAULT_CLIENT_TYPE = "android"
        private const val SECOND_ITEM_INDEX = 1
        private const val THIRD_ITEM_INDEX = 2
        private const val KEY_ACCEPT_LANGUAGE = "Accept-Language"
    }
}

package com.algorand.wallet.deeplink.parser.query

import com.algorand.wallet.deeplink.model.PeraUri
import com.algorand.wallet.deeplink.model.DeepLink
import javax.inject.Inject

internal class AccountImportFromPrivateKeyQueryParser @Inject constructor() :
    DeepLinkQueryParser<DeepLink.AccountImportFromPrivateKey?> {

    override fun parseQuery(peraUri: PeraUri): DeepLink.AccountImportFromPrivateKey? {
        if (peraUri.scheme != "avm" || peraUri.host != "account") {
            return null
        }

        val name = peraUri.getQueryParam("name")
        val key = peraUri.getQueryParam("privatekey")

        return if (!key.isNullOrBlank()) {
            DeepLink.AccountImportFromPrivateKey(name, key)
        } else {
            null
        }
    }
}

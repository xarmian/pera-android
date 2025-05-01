package com.algorand.wallet.deeplink.builder

import com.algorand.wallet.deeplink.model.DeepLink
import com.algorand.wallet.deeplink.model.DeepLinkPayload
import javax.inject.Inject

internal class AccountImportFromPrivateKeyDeepLinkBuilder @Inject constructor() : DeepLinkBuilder {

    override fun doesDeeplinkMeetTheRequirements(payload: DeepLinkPayload): Boolean {
        // Ensure this payload is specifically for AccountImportFromPrivateKey
        // and not conflicting with other potential deeplink types.
        return with(payload) {
            accountImportFromPrivateKey != null &&
                accountAddress == null &&
                walletConnectUrl == null &&
                assetId == null &&
                amount == null &&
                note == null &&
                xnote == null &&
                mnemonic == null &&
                label == null &&
                transactionStatus == null &&
                transactionId == null &&
                url == null &&
                webImportQrCode == null &&
                notificationGroupType == null &&
                fee == null &&
                votekey == null &&
                selkey == null &&
                sprfkey == null &&
                votefst == null &&
                votelst == null &&
                votekd == null &&
                type == null &&
                // host and path might be present from the URI parsing, but shouldn't define the link type here
                // rawDeepLinkUri will always be present
                true
        }
    }

    override fun createDeepLink(payload: DeepLinkPayload): DeepLink {
        // The doesDeeplinkMeetTheRequirements check ensures this is not null.
        return payload.accountImportFromPrivateKey!!
    }
}

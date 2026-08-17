/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.association;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.common.AssociationContract;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig;
import com.solana.mobilewalletadapter.walletlib.scenario.NostrRelayScenario;
import com.solana.mobilewalletadapter.walletlib.scenario.Scenario;

public class NostrAssociationUri extends AssociationUri {
    @NonNull
    public final String relayDomain;

    @NonNull
    public final String dappNostrPubkey;

    public NostrAssociationUri(@NonNull Uri uri) {
        super(uri);
        validate(uri);
        relayDomain = parseRelayDomain(uri);
        dappNostrPubkey = parsePubkey(uri);
    }

    private static void validate(@NonNull Uri uri) {
        final String path = uri.getPath();
        if (path == null || !path.endsWith(AssociationContract.NOSTR_PATH_SUFFIX)) {
            throw new IllegalArgumentException("uri must end with " +
                    AssociationContract.NOSTR_PATH_SUFFIX);
        }
    }

    @NonNull
    @Override
    public Scenario createScenario(@NonNull Context context,
                                   @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                                   @NonNull AuthIssuerConfig authIssuerConfig,
                                   @NonNull Scenario.Callbacks callbacks) {
        return new NostrRelayScenario(context, mobileWalletAdapterConfig, authIssuerConfig,
                callbacks, associationPublicKey, associationProtocolVersions,
                relayDomain, dappNostrPubkey);
    }

    @NonNull
    private static String parseRelayDomain(@NonNull Uri uri) {
        final String relay = uri.getQueryParameter(AssociationContract.NOSTR_PARAMETER_RELAY);
        if (relay == null || relay.isEmpty()) {
            throw new IllegalArgumentException("Relay domain must be specified");
        }
        return relay;
    }

    @NonNull
    private static String parsePubkey(@NonNull Uri uri) {
        final String pubkey = uri.getQueryParameter(AssociationContract.NOSTR_PARAMETER_PUBKEY);
        if (pubkey == null || pubkey.isEmpty()) {
            throw new IllegalArgumentException("Nostr pubkey must be specified");
        }
        if (pubkey.length() != 64 || !pubkey.matches("[0-9a-f]+")) {
            throw new IllegalArgumentException(
                    "Nostr pubkey must be a 64-character lowercase hex string");
        }
        return pubkey;
    }
}

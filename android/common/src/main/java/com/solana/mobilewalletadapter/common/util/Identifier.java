package com.solana.mobilewalletadapter.common.util;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.common.ProtocolContract;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Identifier {

    // matches "{namespace}:{reference}", no whitespace
    private static final Pattern namespacedIdentifierPattern = Pattern.compile("^\\S+:\\S+$");

    public static boolean isValidIdentifier(@NonNull String input) {
        Matcher matcher = namespacedIdentifierPattern.matcher(input);
        return matcher.find();
    }

    public static @NonNull String clusterToChainIdentifier(@NonNull String cluster)
            throws IllegalArgumentException {
        switch (cluster) {
            case ProtocolContract.CLUSTER_MAINNET_BETA:
                return ProtocolContract.CHAIN_SOLANA_MAINNET;
            case ProtocolContract.CLUSTER_TESTNET:
                return ProtocolContract.CHAIN_SOLANA_TESTNET;
            case ProtocolContract.CLUSTER_DEVNET:
                return ProtocolContract.CHAIN_SOLANA_DEVNET;
            default:
                throw new IllegalArgumentException("input is not a valid solana cluster");
        }
    }

    private Identifier() {}
}

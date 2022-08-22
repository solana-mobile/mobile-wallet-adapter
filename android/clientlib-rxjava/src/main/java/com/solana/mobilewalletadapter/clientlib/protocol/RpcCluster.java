package com.solana.mobilewalletadapter.clientlib.protocol;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.common.ProtocolContract;

public enum RpcCluster {
    MAINNET_BETA(ProtocolContract.CLUSTER_MAINNET_BETA),
    TESTNET(ProtocolContract.CLUSTER_TESTNET),
    DEVNET(ProtocolContract.CLUSTER_DEVNET);

    @NonNull
    private final String clusterName;

    RpcCluster(@NonNull String clusterMainnetBeta) {
        this.clusterName = clusterMainnetBeta;
    }

    @NonNull
    public String getClusterName() {
        return clusterName;
    }
}

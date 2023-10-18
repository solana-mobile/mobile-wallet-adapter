/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.SessionProperties;

import java.util.List;

public interface Scenario {

    byte[] getAssociationPublicKey();
    List<SessionProperties.ProtocolVersion> getAssociationProtocolVersions();

    MessageReceiver createMessageReceiver();

    void start();
    void close();

    interface Callbacks {
        // Scenario state callbacks
        void onScenarioReady();
        void onScenarioServingClients();
        void onScenarioServingComplete();
        void onScenarioComplete();
        void onScenarioError();
        void onScenarioTeardownComplete();

        // Request callbacks
        void onAuthorizeRequest(@NonNull AuthorizeRequest request);
        void onReauthorizeRequest(@NonNull ReauthorizeRequest request);
        void onSignTransactionsRequest(@NonNull SignTransactionsRequest request);
        void onSignMessagesRequest(@NonNull SignMessagesRequest request);
        void onSignAndSendTransactionsRequest(@NonNull SignAndSendTransactionsRequest request);

        // Event callbacks
        void onDeauthorizedEvent(@NonNull DeauthorizedEvent event);
    }
}
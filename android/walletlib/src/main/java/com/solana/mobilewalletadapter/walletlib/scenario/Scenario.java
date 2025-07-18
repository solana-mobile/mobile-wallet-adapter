/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.SessionProperties;
import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture;

import java.util.List;

public interface Scenario {

    byte[] getAssociationPublicKey();
    List<SessionProperties.ProtocolVersion> getAssociationProtocolVersions();

    MessageReceiver createMessageReceiver();

    /**
     * Start the scenario, asynchronously
     * <p>
     * This method starts the connection process but returns immediately with a
     * {@link NotifyingCompletableFuture}. The returned future will be completed
     * once the scenario has successfully established a session.
     * <p>
     * If session establishment fails for any reason, the Future will throw an
     * {@link java.util.concurrent.ExecutionException} with the cause of the failure.
     *
     * @return a Future that completes with a session UUID when the session is established.
     */
    NotifyingCompletableFuture<String> startAsync();
    void close();

    /**
     * @deprecated
     * Starting a scenario without a deferred future is deprecated.
     * <p> Use {@link Scenario#startAsync()} instead, which returns a
     * {@link NotifyingCompletableFuture} to notify of Session establishment status
     */
    @Deprecated
    void start();

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
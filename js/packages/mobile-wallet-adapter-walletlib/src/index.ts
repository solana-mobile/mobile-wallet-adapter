import { TransactionVersion } from '@solana/web3.js';
import { useEffect, useState } from 'react';
import { Linking, NativeEventEmitter, NativeModules, Platform } from 'react-native';

import {
    AuthorizeDappRequest,
    ReauthorizeDappRequest,
    SignAndSendTransactionsRequest,
    SignMessagesRequest,
    SignTransactionsRequest,
} from './requests.js';
import { MobileWalletAdapterServiceRequest, MobileWalletAdapterServiceRequestEventType } from './requests.js';
import {
    LowPowerNoConnectionEvent,
    MobileWalletAdapterSessionEvent,
    MobileWalletAdapterSessionEventType,
    NoneEvent,
    SessionCompleteEvent,
    SessionErrorEvent,
    SessionReadyEvent,
    SessionServingClientsEvent,
    SessionServingCompleteEvent,
    SessionTeardownCompleteEvent,
    SessionTerminatedEvent,
} from './sessionEvents.js';

const LINKING_ERROR =
    `The package 'solana-mobile-wallet-adapter-walletlib' doesn't seem to be linked. Make sure: \n\n` +
    '- You rebuilt the app after installing the package\n' +
    '- If you are using Lerna workspaces\n' +
    '  - You have added `@solana-mobile/mobile-wallet-adapter-walletlib` as an explicit dependency, and\n' +
    '  - You have added `@solana-mobile/mobile-wallet-adapter-walletlib` to the `nohoist` section of your package.json\n' +
    '- You are not using Expo managed workflow\n';

const SolanaMobileWalletAdapterWalletLib =
    Platform.OS === 'android' && NativeModules.SolanaMobileWalletAdapterWalletLib
        ? NativeModules.SolanaMobileWalletAdapterWalletLib
        : new Proxy(
              {},
              {
                  get() {
                      throw new Error(
                          Platform.OS !== 'android'
                              ? 'The package `solana-mobile-wallet-adapter-walletlib` is only compatible with React Native Android'
                              : LINKING_ERROR,
                      );
                  },
              },
          );

const MOBILE_WALLET_ADAPTER_SERVICE_REQUEST_BRIDGE_NAME = 'MobileWalletAdapterServiceRequestBridge';
const MOBILE_WALLET_ADAPTER_SESSION_EVENT_BRIDGE_NAME = 'MobileWalletAdapterSessionEventBridge';

export interface MobileWalletAdapterConfig {
    supportsSignAndSendTransactions: boolean;
    maxTransactionsPerSigningRequest: number;
    maxMessagesPerSigningRequest: number;
    supportedTransactionVersions: Array<TransactionVersion>;
    noConnectionWarningTimeoutMs: number;
}

export function subscribeToNativeEvents(
    handleServiceRequest: (event: MobileWalletAdapterServiceRequest | null) => void,
    handleSessionEvent: (event: MobileWalletAdapterSessionEvent | null) => void,
) {
    const eventEmitter = new NativeEventEmitter();

    // Listens for service requests
    eventEmitter.addListener(MOBILE_WALLET_ADAPTER_SERVICE_REQUEST_BRIDGE_NAME, (newRequest) => {
        const typedRequest: MobileWalletAdapterServiceRequest | null = bridgeEventToMWARequest(newRequest);
        handleServiceRequest(typedRequest);
    });

    // Listens for session events
    eventEmitter.addListener(MOBILE_WALLET_ADAPTER_SESSION_EVENT_BRIDGE_NAME, (newEvent) => {
        const typedSessionEvent: MobileWalletAdapterSessionEvent | null = bridgeEventToMWASessionEvent(newEvent);
        handleSessionEvent(typedSessionEvent);
    });
    return eventEmitter;
}

export async function initializeScenario(walletName: string, walletConfig: MobileWalletAdapterConfig) {
    // Get initial URL
    const initialUrl = await Linking.getInitialURL();

    console.log('Initializing scenario for ' + initialUrl);
    // Create Scenario and establish session with dapp
    SolanaMobileWalletAdapterWalletLib.createScenario(walletName, initialUrl, walletConfig);
}

export function useMobileWalletAdapterSession(walletName: string, walletConfig: MobileWalletAdapterConfig) {
    const [request, setRequest] = useState<MobileWalletAdapterServiceRequest | null>();
    const [sessionEvent, setSessionEvent] = useState<MobileWalletAdapterSessionEvent | null>();

    useEffect(() => {
        console.log('Listening to Mobile Wallet Adapter native events');
        // Subscribe to events
        const nativeEventEmitter = subscribeToNativeEvents(
            (newRequest: MobileWalletAdapterServiceRequest | null) => {
                console.log('Request received: ' + newRequest?.type);
                setRequest(newRequest);
            },
            (newSessionEvent: MobileWalletAdapterSessionEvent | null) => {
                console.log('Session event received: ' + newSessionEvent?.type);
                setSessionEvent(newSessionEvent);
            },
        );

        // Create scenario (now native starts receiving events)
        initializeScenario(walletName, walletConfig);

        // Cleanup function
        return () => {
            console.log('Stopping listening to Mobile Wallet Adapter native events');
            // Unsubscribe from events and any other necessary cleanup
            nativeEventEmitter.removeAllListeners(MOBILE_WALLET_ADAPTER_SERVICE_REQUEST_BRIDGE_NAME);
            nativeEventEmitter.removeAllListeners(MOBILE_WALLET_ADAPTER_SESSION_EVENT_BRIDGE_NAME);
        };
    }, []);

    return { request, sessionEvent };
}

function bridgeEventToMWARequest(event: any): MobileWalletAdapterServiceRequest | null {
    const appIdentity = {
        identityUri: event.identityUri,
        iconRelativeUri: event.iconRelativeUri,
        identityName: event.identityName,
    };
    switch (event.type) {
        case MobileWalletAdapterServiceRequestEventType.AuthorizeDapp:
            return new AuthorizeDappRequest(event.cluster, appIdentity);
        case MobileWalletAdapterServiceRequestEventType.ReauthorizeDapp:
            return new ReauthorizeDappRequest(event.cluster, event.authorizationScope, appIdentity);
        case MobileWalletAdapterServiceRequestEventType.SignMessages:
            return new SignMessagesRequest(
                event.payloads.map((payload: number[]) => new Uint8Array(payload)),
                event.cluster,
                event.authorizationScope,
                appIdentity,
            );
        case MobileWalletAdapterServiceRequestEventType.SignTransactions:
            return new SignTransactionsRequest(
                event.payloads.map((payload: number[]) => new Uint8Array(payload)),
                event.cluster,
                event.authorizationScope,
                appIdentity,
            );
        case MobileWalletAdapterServiceRequestEventType.SignAndSendTransactions:
            return new SignAndSendTransactionsRequest(
                event.payloads.map((payload: number[]) => new Uint8Array(payload)),
                event.cluster,
                event.authorizationScope,
                appIdentity,
                event.minContextSlot,
            );
        default:
            console.warn('Unknown event type received:', event.type);
            return null;
    }
}

function bridgeEventToMWASessionEvent(event: any): MobileWalletAdapterSessionEvent | null {
    switch (event.type) {
        case MobileWalletAdapterSessionEventType.None:
            return new NoneEvent();
        case MobileWalletAdapterSessionEventType.SessionTerminated:
            return new SessionTerminatedEvent();
        case MobileWalletAdapterSessionEventType.SessionReady:
            return new SessionReadyEvent();
        case MobileWalletAdapterSessionEventType.SessionServingClients:
            return new SessionServingClientsEvent();
        case MobileWalletAdapterSessionEventType.SessionServingComplete:
            return new SessionServingCompleteEvent();
        case MobileWalletAdapterSessionEventType.SessionComplete:
            return new SessionCompleteEvent();
        case MobileWalletAdapterSessionEventType.SessionError:
            return new SessionErrorEvent();
        case MobileWalletAdapterSessionEventType.SessionTeardownComplete:
            return new SessionTeardownCompleteEvent();
        case MobileWalletAdapterSessionEventType.LowPowerNoConnection:
            return new LowPowerNoConnectionEvent();
        default:
            console.warn('Unknown event type received:', event.type);
            return null;
    }
}

export function createScenario(walletName: string, uriStr: string, config: MobileWalletAdapterConfig) {
    SolanaMobileWalletAdapterWalletLib.createScenario(walletName, uriStr, config);
}

export function completeRequestWithDecline() {
    SolanaMobileWalletAdapterWalletLib.completeRequestWithDecline();
}

export function authorizeDapp(publicKey: Uint8Array, authorized: boolean) {
    SolanaMobileWalletAdapterWalletLib.authorizedDapp(Array.from(publicKey), authorized);
}

export * from './requests.js';
export * from './sessionEvents.js';

import { TransactionVersion } from '@solana/web3.js';
import { useEffect,useState } from 'react'
import { Linking, NativeEventEmitter, NativeModules, Platform } from 'react-native';

import { AuthorizeDappRequest, LowPowerNoConnectionRequest, NoneRequest, ReauthorizeDappRequest, SessionTerminatedRequest, SignAndSendTransactionsRequest, SignMessagesRequest, SignTransactionsRequest } from './requests.js';
import { MobileWalletAdapterServiceEventType, MobileWalletAdapterServiceRequest } from './requests.js';

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

const MOBILE_WALLET_ADAPTER_EVENT_NAME = 'MobileWalletAdapterServiceEvent';

export interface MobileWalletAdapterConfig {
    supportsSignAndSendTransactions: boolean;
    maxTransactionsPerSigningRequest:  number;
    maxMessagesPerSigningRequest: number;
    supportedTransactionVersions: Array<TransactionVersion>;
    noConnectionWarningTimeoutMs: number;
}

// Approach 1: Expose two methods, subscribeToEvents and initializeScenario. Let user handle granular function calls
export function subscribeToEvents(handleEvent: (event: MobileWalletAdapterServiceRequest) => void) {
    const eventEmitter = new NativeEventEmitter();
    eventEmitter.addListener(MOBILE_WALLET_ADAPTER_EVENT_NAME, newEvent => {
        if (newEvent.type === null || Object.values(MobileWalletAdapterServiceEventType).indexOf(newEvent.type) === -1) {
            console.warn("Unsupported MWA event type: " + newEvent)
            return;
        }
        handleEvent(newEvent)
    });
    return eventEmitter;
}

export async function initializeScenario(walletName: string, walletConfig: MobileWalletAdapterConfig) {
    // Get initial URL
    const initialUrl = await Linking.getInitialURL();

    console.log("Initializing scenario for " + initialUrl)
    // Create Scenario and establish session with dapp
    SolanaMobileWalletAdapterWalletLib.createScenario(walletName, initialUrl, walletConfig);
}

// Approach 2: Expose a singular event that encapsulates subbing to events and initalizing scenario.
export function useMobileWalletAdapterEvent(walletName: string, walletConfig: MobileWalletAdapterConfig) {
    const [event, setEvent] = useState<MobileWalletAdapterServiceRequest | null>();
  
    useEffect(() => {
      console.log("Listening to Mobile Wallet Adapter events")
      // Subscribe to events
      const eventEmitter = subscribeToEvents((event: MobileWalletAdapterServiceRequest) => {
        setEvent(event);
      });
  
      // Create scenario (now native starts receiving events)
      initializeScenario(walletName, walletConfig);
  
      // Cleanup function
      return () => {
        console.log("Stopping listening to Mobile Wallet Adapter events")
        // Unsubscribe from events and any other necessary cleanup
        eventEmitter.removeAllListeners(MOBILE_WALLET_ADAPTER_EVENT_NAME);
      };
    }, []);
  
    return { event, setEvent };
}

// Approach 3: Create and return request "classes" that come with their appropriate "complete" methods. e.g: request.completeWithDecline()
export function useMobileWalletAdapterRequest(walletName: string, walletConfig: MobileWalletAdapterConfig) {
    const [request, setRequest] = useState<MobileWalletAdapterServiceRequest | null>();
  
    useEffect(() => {
      console.log("Listening to Mobile Wallet Adapter events")
      // Subscribe to events
      const eventEmitter = subscribeToRequests((newRequest: MobileWalletAdapterServiceRequest | null) => {
        console.log("Request received: " + newRequest)
        setRequest(newRequest);
      });
  
      // Create scenario (now native starts receiving events)
      initializeScenario(walletName, walletConfig);
  
      // Cleanup function
      return () => {
        console.log("Stopping listening to Mobile Wallet Adapter events")
        // Unsubscribe from events and any other necessary cleanup
        eventEmitter.removeAllListeners(MOBILE_WALLET_ADAPTER_EVENT_NAME);
      };
    }, []);
  
    return { request };
}

export function subscribeToRequests(handleRequest: (newRequest: MobileWalletAdapterServiceRequest | null) => void) {
    const eventEmitter = new NativeEventEmitter();
    eventEmitter.addListener(MOBILE_WALLET_ADAPTER_EVENT_NAME, newEvent => {
        const newRequest = bridgeEventToMWARequest(newEvent);
        handleRequest(newRequest)
    });
    return eventEmitter;
}

function bridgeEventToMWARequest(event: any): MobileWalletAdapterServiceRequest | null {
    switch (event.type) {
        case MobileWalletAdapterServiceEventType.None:
            return new NoneRequest();
        case MobileWalletAdapterServiceEventType.SessionTerminated:
            return new SessionTerminatedRequest();
        case MobileWalletAdapterServiceEventType.LowPowerNoConnection:
            return new LowPowerNoConnectionRequest();
        case MobileWalletAdapterServiceEventType.AuthorizeDapp:
            return new AuthorizeDappRequest(event.identityName, event.identityUri, event.iconRelativeUri, event.cluster);
        case MobileWalletAdapterServiceEventType.ReauthorizeDapp:
            return new ReauthorizeDappRequest();
        case MobileWalletAdapterServiceEventType.SignMessages:
            return new SignMessagesRequest(event.payloads.map((payload: number[]) => new Uint8Array(payload)));
        case MobileWalletAdapterServiceEventType.SignTransactions:
            return new SignTransactionsRequest(event.payloads.map((payload: number[]) => new Uint8Array(payload)));
        case MobileWalletAdapterServiceEventType.SignAndSendTransactions:
            return new SignAndSendTransactionsRequest(
                event.payloads.map((payload: number[]) => new Uint8Array(payload)),
                event.minContextSlot
            );
        default:
            console.error("Unknown event type received:", event.type);
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



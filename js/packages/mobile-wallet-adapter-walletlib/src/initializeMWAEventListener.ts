import { EmitterSubscription, NativeEventEmitter } from 'react-native';

import { MWASessionEvent, MWASessionEventType } from './mwaSessionEvents.js';
import { MWARequest, MWARequestType } from './resolve.js';

const MOBILE_WALLET_ADAPTER_EVENT_BRIDGE_NAME = 'MobileWalletAdapterServiceRequestBridge';

export function initializeMWAEventListener(
    handleRequest: (request: MWARequest) => void,
    handleSessionEvent: (sessionEvent: MWASessionEvent) => void,
): EmitterSubscription {
    const mwaEventEmitter = new NativeEventEmitter();
    const listener = mwaEventEmitter.addListener(MOBILE_WALLET_ADAPTER_EVENT_BRIDGE_NAME, (nativeEvent) => {
        if (isMWARequest(nativeEvent)) {
            handleRequest(nativeEvent as MWARequest);
        } else if (isMWASessionEvent(nativeEvent)) {
            handleSessionEvent(nativeEvent as MWASessionEvent);
        } else {
            console.warn('Unexpected native event type');
        }
    });

    return listener;
}

function isMWARequest(nativeEvent: any): boolean {
    return Object.values(MWARequestType).includes(nativeEvent.__type);
}

function isMWASessionEvent(nativeEvent: any) {
    return Object.values(MWASessionEventType).includes(nativeEvent.__type);
}

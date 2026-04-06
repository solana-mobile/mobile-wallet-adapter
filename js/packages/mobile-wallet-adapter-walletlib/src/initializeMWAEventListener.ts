import { EmitterSubscription, NativeEventEmitter } from 'react-native';

import { MWASessionEvent, MWASessionEventType } from './mwaSessionEvents.js';
import { MWARequest, MWARequestType } from './resolve.js';

const MOBILE_WALLET_ADAPTER_EVENT_BRIDGE_NAME = 'MobileWalletAdapterServiceRequestBridge';
type NativeMWAEvent = Readonly<{ __type?: unknown }>;

export function initializeMWAEventListener(
    handleRequest: (request: MWARequest) => void,
    handleSessionEvent: (sessionEvent: MWASessionEvent) => void,
): EmitterSubscription {
    const mwaEventEmitter = new NativeEventEmitter();
    const listener = mwaEventEmitter.addListener(MOBILE_WALLET_ADAPTER_EVENT_BRIDGE_NAME, (nativeEvent) => {
        const event = nativeEvent as NativeMWAEvent;
        if (isMWARequest(event)) {
            handleRequest(event);
        } else if (isMWASessionEvent(event)) {
            handleSessionEvent(event);
        } else {
            console.warn('Unexpected native event type');
        }
    });

    return listener;
}

function isMWARequest(nativeEvent: NativeMWAEvent): nativeEvent is MWARequest {
    return Object.values(MWARequestType).includes(nativeEvent.__type as MWARequest['__type']);
}

function isMWASessionEvent(nativeEvent: NativeMWAEvent): nativeEvent is MWASessionEvent {
    return Object.values(MWASessionEventType).includes(nativeEvent.__type as MWASessionEvent['__type']);
}

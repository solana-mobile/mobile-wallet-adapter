import { useEffect } from 'react';

import {
    initializeMobileWalletAdapterSession,
    MobileWalletAdapterConfig,
} from './initializeMobileWalletAdapterSession.js';
import { initializeMWAEventListener } from './initializeMWAEventListener.js';
import { MWASessionEvent } from './mwaSessionEvents.js';
import { MWARequest } from './resolve.js';

export function useMobileWalletAdapterSession(
    walletName: string,
    config: MobileWalletAdapterConfig,
    handleRequest: (request: MWARequest) => void,
    handleSessionEvent: (sessionEvent: MWASessionEvent) => void,
) {
    useEffect(() => {
        async function startSession() {
            try {
                await initializeMobileWalletAdapterSession(walletName, config);
            } catch (e) {
                console.error(e);
            }
        }
        const listener = initializeMWAEventListener(handleRequest, handleSessionEvent);
        startSession();
        return () => {
            listener.remove();
        };
    }, []);
}

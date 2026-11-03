import '@fontsource-variable/inter';
import './style.css';

import {
    createDefaultAuthorizationCache,
    createDefaultChainSelector,
    createDefaultWalletNotFoundHandler,
    registerMwa,
} from '@solana-mobile/wallet-standard-mobile';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { App } from './App.js';
import { CHAIN, config } from './config.js';

const baseConfig = {
    appIdentity: {
        name: 'Example MWA Web DApp',
        uri: window.location.origin,
    },
    authorizationCache: createDefaultAuthorizationCache(),
    chains: [CHAIN] as const,
    chainSelector: createDefaultChainSelector(),
    onWalletNotFound: createDefaultWalletNotFoundHandler(),
};

registerMwa(
    config.nostrRelay
        ? { ...baseConfig, nostrRelay: config.nostrRelay }
        : { ...baseConfig, remoteHostAuthority: config.reflectorHostAuthority },
);

createRoot(document.getElementById('root')!).render(
    <StrictMode>
        <App />
    </StrictMode>,
);

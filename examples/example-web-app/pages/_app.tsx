import '@solana/wallet-adapter-react-ui/styles.css';
import '../styles/globals.css';

import { ThemeProvider } from '@emotion/react';
import { createTheme } from '@mui/material';
import { WalletAdapterNetwork, WalletError } from '@solana/wallet-adapter-base';
import { ConnectionProvider, WalletProvider } from '@solana/wallet-adapter-react';
import { WalletModalProvider } from '@solana/wallet-adapter-react-ui';
import { clusterApiUrl, ConnectionConfig } from '@solana/web3.js';
import {
    createDefaultAuthorizationCache,
    createDefaultChainSelector,
    createDefaultWalletNotFoundHandler,
    registerMwa,
} from '@solana-mobile/wallet-standard-mobile';
import type { AppProps } from 'next/app';
import { SnackbarProvider, useSnackbar } from 'notistack';
import { ReactNode, useCallback, useMemo } from 'react';

const NOSTR_RELAY = process.env.NEXT_PUBLIC_NOSTR_RELAY ?? undefined;
const REFLECTOR_HOST_AUTHORITY = process.env.NEXT_PUBLIC_REFLECTOR_HOST_AUTHORITY ?? undefined;

function getUriForAppIdentity() {
    const location = globalThis.location;
    if (!location) return;
    return `${location.protocol}//${location.host}`;
}

function getBaseMwaConfig() {
    return {
        appIdentity: {
            uri: getUriForAppIdentity(),
            name: 'Example MWA Web DApp',
        },
        authorizationCache: createDefaultAuthorizationCache(),
        chains: ['solana:devnet'] as readonly `${string}:${string}`[],
        chainSelector: createDefaultChainSelector(),
        onWalletNotFound: createDefaultWalletNotFoundHandler(),
    };
}

registerMwa(
    NOSTR_RELAY
        ? {
              ...getBaseMwaConfig(),
              nostrRelay: NOSTR_RELAY,
          }
        : {
              ...getBaseMwaConfig(),
              remoteHostAuthority: REFLECTOR_HOST_AUTHORITY,
          },
);

const CLUSTER = WalletAdapterNetwork.Devnet;
const CONNECTION_CONFIG: ConnectionConfig = { commitment: 'processed' };
const ENDPOINT = /*#__PURE__*/ clusterApiUrl(CLUSTER);

const theme = /*#__PURE__*/ createTheme();

function isAndroidMobile() {
    return (
        typeof window !== 'undefined' &&
        window.isSecureContext &&
        typeof document !== 'undefined' &&
        /android/i.test(navigator.userAgent)
    );
}

function App({ children }: { children: ReactNode }) {
    const { enqueueSnackbar } = useSnackbar();
    const handleWalletError = useCallback(
        (e: WalletError) => {
            enqueueSnackbar(`${e.name}: ${e.message}`, { variant: 'error' });
        },
        [enqueueSnackbar],
    );
    const adapters = useMemo(
        () =>
            typeof window === 'undefined'
                ? [] // No wallet adapters when server-side rendering.
                : [
                      /**
                       * Note that you don't have to include the SolanaMobileWalletAdapter here;
                       * It will be added automatically when this app is running in a compatible mobile context.
                       */
                  ],
        [],
    );
    return (
        <ThemeProvider theme={theme}>
            <ConnectionProvider config={CONNECTION_CONFIG} endpoint={ENDPOINT}>
                <WalletProvider autoConnect={isAndroidMobile()} onError={handleWalletError} wallets={adapters}>
                    <WalletModalProvider>{children}</WalletModalProvider>
                </WalletProvider>
            </ConnectionProvider>
        </ThemeProvider>
    );
}

function ExampleMobileDApp({ Component, pageProps }: AppProps) {
    return (
        <SnackbarProvider autoHideDuration={10000}>
            <App>
                <Component {...pageProps} />
            </App>
        </SnackbarProvider>
    );
}

export default ExampleMobileDApp;

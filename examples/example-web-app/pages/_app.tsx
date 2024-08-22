import '@solana/wallet-adapter-react-ui/styles.css';
import '../styles/globals.css';

import { ConnectionConfig, clusterApiUrl } from '@solana/web3.js';
import { ConnectionProvider, WalletProvider } from '@solana/wallet-adapter-react';

import type { AppProps } from 'next/app';
import { SnackbarProvider, useSnackbar } from 'notistack';
import { ThemeProvider } from '@emotion/react';
import { WalletAdapterNetwork, WalletError } from '@solana/wallet-adapter-base';
import { WalletModalProvider } from '@solana/wallet-adapter-react-ui';
import { createTheme } from '@mui/material';
import { ReactNode, useCallback, useMemo } from 'react';
import { createDefaultAddressSelector, createDefaultAuthorizationResultCache, SolanaMobileWalletAdapterRemote } from '@solana-mobile/wallet-adapter-mobile';

const CLUSTER = WalletAdapterNetwork.Devnet;
const CONNECTION_CONFIG: ConnectionConfig = { commitment: 'processed' };
const ENDPOINT = /*#__PURE__*/ clusterApiUrl(CLUSTER);

const theme = /*#__PURE__*/ createTheme();

function getUriForAppIdentity() {
    const location = globalThis.location;
    if (!location)
        return;
    return `${location.protocol}//${location.host}`;
}

async function defaultWalletNotFoundHandler(mobileWalletAdapter: SolanaMobileWalletAdapterRemote) {
    if (typeof window !== 'undefined') {
        window.location.assign(mobileWalletAdapter.url);
    }
}

function createDefaultWalletNotFoundHandler(): (
    mobileWalletAdapter: SolanaMobileWalletAdapterRemote,
) => Promise<void> {
    return defaultWalletNotFoundHandler;
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
                    new SolanaMobileWalletAdapterRemote({
                        addressSelector: createDefaultAddressSelector(),
                        appIdentity: {
                            uri: getUriForAppIdentity(),
                        },
                        authorizationResultCache: createDefaultAuthorizationResultCache(),
                        chain: 'solana:devnet',
                        remoteHostAuthority: '4.tcp.us-cal-1.ngrok.io:14027',
                        onWalletNotFound: createDefaultWalletNotFoundHandler(),
                    }),
                  ],
        [],
    );
    return (
        <ThemeProvider theme={theme}>
            <ConnectionProvider config={CONNECTION_CONFIG} endpoint={ENDPOINT}>
                <WalletProvider autoConnect={true} onError={handleWalletError} wallets={adapters}>
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

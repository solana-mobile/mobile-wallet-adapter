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

import { SolanaMobileWalletAdapter } from '@solana-mobile/wallet-adapter-mobile';
import { createDefaultAddressSelector, createDefaultAuthorizationResultCache } from '@solana-mobile/wallet-adapter-mobile';

function getUriForAppIdentity() {
    const location = globalThis.location;
    if (!location) return;
    return `${location.protocol}//${location.host}`;
}

const CLUSTER = WalletAdapterNetwork.Devnet;
const CONNECTION_CONFIG: ConnectionConfig = { commitment: 'processed' };
const ENDPOINT = /*#__PURE__*/ clusterApiUrl(CLUSTER);

const theme = /*#__PURE__*/ createTheme();

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
                       * 
                       * However, it is recommended to manually include the SolanaSolanaMobileWalletAdapter 
                       * here so that you can provide Dapp identity parameters and a walletNotFoundHandler for 
                       * the best UX. The adapter will overwrite any existing SolanaMobileWalletAdapter. 
                       */
                      new SolanaMobileWalletAdapter({
                        addressSelector: createDefaultAddressSelector(),
                        appIdentity: {
                            uri: getUriForAppIdentity(),
                        },
                        authorizationResultCache: createDefaultAuthorizationResultCache(),
                        chain: 'solana:testnet',
                        onWalletNotFound: async (mobileWalletAdapter: SolanaMobileWalletAdapter) => {
                            if (typeof window !== 'undefined') {
                                const userAgent = window.navigator.userAgent.toLowerCase();
                                if (userAgent.includes('wv')) { // Android WebView
                                    // Example: MWA is not supported in this browser so we inform the user
                                    enqueueSnackbar('Mobile Wallet Adapter is not supported on this browser!', { variant: 'error' });
                                } else { // Browser, user does not have a wallet installed. 
                                    window.location.assign(mobileWalletAdapter.url);
                                }
                            }
                        },
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

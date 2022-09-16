import '../styles/globals.css';

import { ThemeProvider } from '@emotion/react';
import { createTheme } from '@mui/material';
import { WalletAdapterNetwork } from '@solana/wallet-adapter-base';
import { ConnectionProvider, WalletProvider } from '@solana/wallet-adapter-react';
import { clusterApiUrl } from '@solana/web3.js';
import {
    createDefaultAddressSelector,
    createDefaultAuthorizationResultCache,
    SolanaMobileWalletAdapter,
} from '@solana-mobile/wallet-adapter-mobile';
import type { AppProps } from 'next/app';
import { SnackbarProvider } from 'notistack';
import { useMemo } from 'react';

const CLUSTER = WalletAdapterNetwork.Devnet;
const ENDPOINT = /*#__PURE__*/ clusterApiUrl(CLUSTER);

const theme = /*#__PURE__*/ createTheme();

function ExampleMobileDApp({ Component, pageProps }: AppProps) {
    const wallets = useMemo(
        () =>
            typeof window === 'undefined'
                ? [] // No wallet adapters when server-side rendering.
                : [
                      new SolanaMobileWalletAdapter({
                          addressSelector: createDefaultAddressSelector(),
                          appIdentity: {
                              icon: 'images/app_icon.png',
                              name: 'Mobile Web dApp',
                              uri: window.location.href,
                          },
                          authorizationResultCache: createDefaultAuthorizationResultCache(),
                          cluster: CLUSTER,
                      }),
                  ],
        [],
    );
    return (
        <ThemeProvider theme={theme}>
            <SnackbarProvider autoHideDuration={10000}>
                <ConnectionProvider config={{ commitment: 'processed' }} endpoint={ENDPOINT}>
                    <WalletProvider wallets={wallets}>
                        <Component {...pageProps} />
                    </WalletProvider>
                </ConnectionProvider>
            </SnackbarProvider>
        </ThemeProvider>
    );
}

export default ExampleMobileDApp;

import '../styles/globals.css';

import { ThemeProvider } from '@emotion/react';
import { createTheme } from '@mui/material';
import { AuthorizationResult } from '@solana/mobile-wallet-protocol';
import { WalletAdapterNetwork } from '@solana/wallet-adapter-base';
import { NativeWalletAdapter } from '@solana/wallet-adapter-mobile';
import { ConnectionProvider, WalletProvider } from '@solana/wallet-adapter-react';
import { clusterApiUrl } from '@solana/web3.js';
import type { AppProps } from 'next/app';
import { SnackbarProvider } from 'notistack';
import { useMemo } from 'react';

const DEVNET_ENDPOINT = clusterApiUrl(WalletAdapterNetwork.Devnet);

const theme = createTheme();

function MyApp({ Component, pageProps }: AppProps) {
    const wallets = useMemo(
        () => [
            new NativeWalletAdapter({
                appIdentity: { name: 'Fake Web dApp' },
                authorizationResultCache: {
                    async get() {
                        try {
                            return JSON.parse(
                                localStorage.getItem('myapp:cachedAuthorizationResult') as string,
                            ) as AuthorizationResult;
                        } catch {}
                    },
                    async set(authorizationResult: AuthorizationResult) {
                        localStorage.setItem('myapp:cachedAuthorizationResult', JSON.stringify(authorizationResult));
                    },
                    async clear() {
                        localStorage.removeItem('myapp:cachedAuthorizationResult');
                    },
                },
            }),
        ],
        [],
    );
    return (
        <ThemeProvider theme={theme}>
            <SnackbarProvider autoHideDuration={10000}>
                <ConnectionProvider endpoint={DEVNET_ENDPOINT}>
                    <WalletProvider wallets={wallets}>
                        <Component {...pageProps} />
                    </WalletProvider>
                </ConnectionProvider>
            </SnackbarProvider>
        </ThemeProvider>
    );
}

export default MyApp;

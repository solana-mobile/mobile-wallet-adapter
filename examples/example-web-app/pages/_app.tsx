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
import { 
    createDefaultAuthorizationCache, 
    createDefaultChainSelector, 
    createDefaultWalletNotFoundHandler,
    registerMwa, 
} from '@solana-mobile/wallet-standard-mobile';

const REFLECTOR_HOST_AUTHORITY = process.env.NEXT_PUBLIC_REFLECTOR_HOST_AUTHORITY ?? undefined;

function getUriForAppIdentity() {
    const location = globalThis.location;
    if (!location) return;
    return `${location.protocol}//${location.host}`;
}

registerMwa({
    appIdentity: {
        uri: getUriForAppIdentity(),
        name: 'Example MWA Web DApp',
    },
    authorizationCache: createDefaultAuthorizationCache(),
    chains: ['solana:devnet'],
    chainSelector: createDefaultChainSelector(),
    remoteHostAuthority: REFLECTOR_HOST_AUTHORITY,
    onWalletNotFound: createDefaultWalletNotFoundHandler(),
})

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
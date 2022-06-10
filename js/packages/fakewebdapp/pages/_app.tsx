import '../styles/globals.css';

import { WalletAdapterNetwork } from '@solana/wallet-adapter-base';
import { NativeWalletAdapter } from '@solana/wallet-adapter-mobile';
import { ConnectionProvider, WalletProvider } from '@solana/wallet-adapter-react';
import { clusterApiUrl } from '@solana/web3.js';
import type { AppProps } from 'next/app';
import { useMemo } from 'react';
import { AuthorizationResult } from '@solana/mobile-wallet-protocol';

const DEVNET_ENDPOINT = clusterApiUrl(WalletAdapterNetwork.Devnet);

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
        <ConnectionProvider endpoint={DEVNET_ENDPOINT}>
            <WalletProvider wallets={wallets}>
                <Component {...pageProps} />
            </WalletProvider>
        </ConnectionProvider>
    );
}

export default MyApp;

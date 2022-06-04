import '../styles/globals.css';

import { WalletAdapterNetwork } from '@solana/wallet-adapter-base';
import { NativeWalletAdapter } from '@solana/wallet-adapter-mobile';
import { ConnectionProvider, WalletProvider } from '@solana/wallet-adapter-react';
import { clusterApiUrl } from '@solana/web3.js';
import type { AppProps } from 'next/app';
import { useMemo } from 'react';

const DEVNET_ENDPOINT = clusterApiUrl(WalletAdapterNetwork.Devnet);

function MyApp({ Component, pageProps }: AppProps) {
    const wallets = useMemo(
        () => [
            new NativeWalletAdapter({
                appIdentity: { name: 'Fake Web dApp' },
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

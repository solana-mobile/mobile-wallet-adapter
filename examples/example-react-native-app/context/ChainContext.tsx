import type { ClusterUrl } from '@solana/kit';
import { devnet } from '@solana/kit';
import { createContext } from 'react';

export type ChainContext = Readonly<{
    chain: `solana:${string}`;
    displayName: string;
    setChain?(chain: `solana:${string}`): void;
    solanaExplorerClusterName: 'devnet' | 'mainnet-beta' | 'testnet';
    solanaRpcSubscriptionsUrl: ClusterUrl;
    solanaRpcUrl: ClusterUrl;
}>;

export const DEFAULT_CHAIN_CONFIG = Object.freeze({
    chain: 'solana:devnet',
    displayName: 'Devnet',
    solanaExplorerClusterName: 'devnet',
    solanaRpcSubscriptionsUrl: devnet('wss://api.devnet.solana.com'),
    solanaRpcUrl: devnet('https://api.devnet.solana.com'),
    // solanaRpcSubscriptionsUrl: devnet('wss://devnet.helius-rpc.com/?api-key=cae9abbb-fbed-4e5e-918a-7e1241d63f1d'),
    // solanaRpcUrl: devnet('https://devnet.helius-rpc.com/?api-key=cae9abbb-fbed-4e5e-918a-7e1241d63f1d'),
});

export const ChainContext = createContext<ChainContext>(DEFAULT_CHAIN_CONFIG);